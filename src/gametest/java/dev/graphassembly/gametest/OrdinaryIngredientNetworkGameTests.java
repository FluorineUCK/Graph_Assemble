package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.bridge.IngredientDecoration;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import java.util.Arrays;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class OrdinaryIngredientNetworkGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void markedNativeIngredientStillEncodesWithoutLosingProtection(GameTestHelper helper) {
        var recipe = Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
            JsonParser.parseString("""
                {"type":"minecraft:smelting","ingredient":{"item":"minecraft:iron_ingot"},
                 "result":{"id":"minecraft:iron_nugget"},"experience":0,"cookingtime":100}
                """)).getOrThrow();
        var ingredient = recipe.getIngredients().getFirst();
        var plain = new ItemStack(Items.IRON_INGOT);
        var marked = plain.copy();
        marked.set(GraphAssembly.PROGRESS.get(), "{}");
        encode(helper, ingredient, () -> {});
        IngredientDecoration.markOrdinary(helper.getLevel(), recipe);
        check(recipe.getIngredients().getFirst() == ingredient, "Marking must retain the native ingredient identity");
        check(!ingredient.isSimple() && !ingredient.test(marked) && ingredient.test(plain), "Runtime guard missing before encode");
        int[] observedWrites = {0};
        encode(helper, ingredient, () -> {
            observedWrites[0]++;
            check(!ingredient.test(marked) && ingredient.test(plain), "Encoding must not suspend the shared object's test guard");
        });
        check(observedWrites[0] > 0, "Must observe protection inside the real network encoder");
        check(!ingredient.isSimple() && !ingredient.test(marked) && ingredient.test(plain), "Runtime guard missing after encode");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void nativeDefinitionBytesJsonAndPacketIdentityStayUnchanged(GameTestHelper helper) {
        var ingredient = Ingredient.of(Items.IRON_INGOT);
        var recipe = recipe(ingredient);
        var holder = new RecipeHolder<>(GraphAssembly.id("wire_native"), recipe);
        var packet = new ClientboundUpdateRecipesPacket(List.of(holder));
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow();
        byte[] before = encode(helper, ingredient, () -> {});
        byte[] packetBefore = packetBytes(helper, packet);
        IngredientDecoration.markOrdinary(helper.getLevel(), recipe);
        check(Arrays.equals(before, encode(helper, ingredient, () -> {})), "Native wire bytes must not change after marking");
        check(Arrays.equals(packetBefore, packetBytes(helper, packet)), "update_recipes bytes must not change after marking");
        check(packet.getRecipes().size() == 1 && packet.getRecipes().getFirst() == holder && holder.value() == recipe,
            "Encoding must not replace the original packet recipe collection or objects");
        check(recipe.getIngredients().getFirst() == ingredient, "Original ingredient object must remain installed");
        check(json.equals(Recipe.CODEC.encodeStart(ops, recipe).getOrThrow()), "Transient guard must not alter recipe JSON");
        var buffer = buffer(helper);
        try {
            ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer, packet);
            var received = ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(buffer);
            check(received.getRecipes().size() == 1 && received.getRecipes().getFirst().id().equals(holder.id()), "Packet lost recipe id");
            check(received.getRecipes().getFirst().value().getIngredients().getFirst().test(new ItemStack(Items.IRON_INGOT)),
                "Packet lost native matching semantics");
            check(buffer.readableBytes() == 0, "Packet round trip left unread bytes");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void customAndNestedIngredientDefinitionsKeepCodecAndSemantics(GameTestHelper helper) {
        var custom = DataComponentIngredient.of(false, DataComponents.CUSTOM_NAME, Component.literal("wire"), Items.IRON_INGOT);
        var nativeChild = Ingredient.of(Items.GOLD_INGOT);
        var mixed = CompoundIngredient.of(custom, nativeChild);
        var simple = CompoundIngredient.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.GOLD_INGOT));
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("wire"));
        for (var ingredient : List.of(custom, mixed, simple)) {
            var source = recipe(ingredient);
            var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var json = Recipe.CODEC.encodeStart(ops, source).getOrThrow();
            byte[] before = encode(helper, ingredient, () -> {});
            IngredientDecoration.markOrdinary(helper.getLevel(), source);
            check(Arrays.equals(before, encode(helper, ingredient, () -> {})), "Custom/nested wire representation changed");
            check(json.equals(Recipe.CODEC.encodeStart(ops, source).getOrThrow()), "Custom/nested JSON changed");
            var buffer = buffer(helper);
            try {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
                var received = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                for (var sample : List.of(named, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND)))
                    check(received.test(sample) == ingredient.test(sample), "Decoded custom/nested matching differs for " + sample);
                if (ingredient != simple) {
                    check(received.isCustom() && !received.isSimple(), "Non-simple custom definition must keep its codec/type");
                    check(received.getCustomIngredient().getType() == ingredient.getCustomIngredient().getType(), "Custom type changed");
                }
                check(buffer.readableBytes() == 0, "Custom round trip left unread bytes");
            } finally { buffer.release(); }
            check(!ingredient.isSimple(), "Marked custom/nested runtime guard lost");
        }
        check(!custom.isSimple() && !nativeChild.isSimple(), "Nested original children must remain protected");
        var illegal = named.copy();
        illegal.set(GraphAssembly.PROGRESS.get(), "{}");
        check(!custom.test(illegal) && !mixed.test(illegal), "Nested custom runtime guard must reject progress");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void nestedAndExceptionalEncodingRestoreScopes(GameTestHelper helper) {
        var outer = Ingredient.of(Items.IRON_INGOT);
        var inner = Ingredient.of(Items.GOLD_INGOT);
        IngredientDecoration.markOrdinary(helper.getLevel(), recipe(outer));
        IngredientDecoration.markOrdinary(helper.getLevel(), recipe(inner));
        boolean[] entered = {false};
        var failure = new IllegalStateException("intentional wire failure");
        encode(helper, outer, () -> {
            if (entered[0]) return;
            entered[0] = true;
            check(outer.isSimple(), "Native definition view must be active inside encode");
            encode(helper, inner, () -> check(inner.isSimple(), "Nested encode lost native view"));
            check(outer.isSimple(), "Successful nested encode must restore its parent scope");
            try {
                encode(helper, inner, () -> { throw failure; });
                throw new AssertionError("Nested failing encoder must propagate exception");
            } catch (IllegalStateException expected) { check(expected == failure, "Exception identity changed"); }
            check(outer.isSimple(), "Failed nested encode must restore its parent scope");
            boolean otherThreadSimple = java.util.concurrent.CompletableFuture.supplyAsync(outer::isSimple).join();
            check(!otherThreadSimple, "Definition encoding scope must not leak to other threads");
        });
        check(entered[0] && !outer.isSimple() && !inner.isSimple(), "Successful outer encode leaked its scope");
        try {
            encode(helper, outer, () -> { throw failure; });
            throw new AssertionError("Outer failing encoder must propagate exception");
        } catch (IllegalStateException expected) { check(expected == failure, "Outer exception identity changed"); }
        check(!outer.isSimple() && !inner.isSimple(), "Exceptional outer encode leaked its scope");
        encode(helper, outer, () -> {});
        check(!outer.isSimple(), "Next successful encode must not inherit a leaked scope");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void nativeShapedMatchingRejectsIllegalGraphWorkpieceAfterSync(GameTestHelper helper) {
        var recipe = (ShapedRecipe) Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
            JsonParser.parseString("""
                {"type":"minecraft:crafting_shaped","pattern":["AB"],
                 "key":{"A":{"item":"minecraft:iron_ingot"},"B":{"item":"minecraft:gold_ingot"}},
                 "result":{"id":"minecraft:diamond"}}
                """)).getOrThrow();
        var plain = new ItemStack(Items.IRON_INGOT);
        var gold = new ItemStack(Items.GOLD_INGOT);
        var illegal = plain.copy();
        illegal.set(GraphAssembly.PROGRESS.get(), "{invalid-progress");
        IngredientDecoration.markOrdinary(helper.getLevel(), recipe);
        check(recipe.matches(CraftingInput.of(2, 1, List.of(plain, gold)), helper.getLevel()), "Native shaped fixture must match ordinary input");
        check(!recipe.matches(CraftingInput.of(2, 1, List.of(illegal, gold)), helper.getLevel()), "Native shaped matcher consumed an illegal graph workpiece");
        packetBytes(helper, new ClientboundUpdateRecipesPacket(List.of(new RecipeHolder<>(GraphAssembly.id("wire_shaped"), recipe))));
        check(!recipe.matches(CraftingInput.of(2, 1, List.of(illegal, gold)), helper.getLevel()), "Native matching guard lost after packet encoding");
        check(recipe.matches(CraftingInput.of(2, 1, List.of(plain, gold)), helper.getLevel()), "Packet encoding broke ordinary native matching");
        helper.succeed();
    }

    private static SmeltingRecipe recipe(Ingredient ingredient) {
        return new SmeltingRecipe("", CookingBookCategory.MISC, ingredient, new ItemStack(Items.IRON_NUGGET), 0, 100);
    }

    private static RegistryFriendlyByteBuf buffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
    }

    private static byte[] packetBytes(GameTestHelper helper, ClientboundUpdateRecipesPacket packet) {
        var buffer = buffer(helper);
        try {
            ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer, packet);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private static byte[] encode(GameTestHelper helper, Ingredient ingredient, Runnable duringWrite) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE) {
            @Override public FriendlyByteBuf writeVarInt(int value) {
                duringWrite.run();
                return super.writeVarInt(value);
            }
        };
        try {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
