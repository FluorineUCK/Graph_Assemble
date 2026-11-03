package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.Progress;
import dev.graphassembly.recipe.Workpiece;
import dev.graphassembly.integration.projection.IngredientDecoration;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.neoforged.neoforge.network.connection.ConnectionType;
import io.netty.buffer.Unpooled;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class OrdinaryRecipeQueryGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void multiItemShapelessCachedSimpleCannotEscapeQueryGuard(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var recipe = (ShapelessRecipe) Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),
            JsonParser.parseString("""
                {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:gold_ingot"}],
                 "result":{"id":"minecraft:diamond"}}
                """)).getOrThrow();
        var holder = new RecipeHolder<CraftingRecipe>(GraphAssembly.id("query_shapeless"), recipe);
        var plain = CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)));
        var illegal = new ItemStack(Items.GOLD_INGOT);
        illegal.set(GraphAssembly.PROGRESS.get(), "{invalid-progress");
        var input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_INGOT), illegal));
        try {
            manager.replaceRecipes(List.of(holder));
            check(manager.getRecipeFor(RecipeType.CRAFTING, plain, level).orElseThrow() == holder, "Plain fixture must match before marking");
            IngredientDecoration.markOrdinary(level, recipe);
            check(!recipe.getIngredients().get(1).isSimple(), "Runtime ingredient guard must be installed");
            check(recipe.matches(input, level), "Fixture must expose cached isSimple StackedContents collision in direct matches");
            check(manager.getRecipeFor(RecipeType.CRAFTING, input, level).isEmpty(), "Ordinary query must reject cached-simple shapeless collision");
            check(manager.getRecipesFor(RecipeType.CRAFTING, input, level).isEmpty(), "All-matching query must reject the same collision");
            check(manager.getRecipeFor(RecipeType.CRAFTING, input, level, holder).isEmpty(), "Cached holder must not bypass query guard");
            check(manager.getRecipeFor(RecipeType.CRAFTING, input, level, holder.id()).isEmpty(), "Cached id must not bypass query guard");
            var cached = RecipeManager.<CraftingInput, CraftingRecipe>createCheck(RecipeType.CRAFTING);
            check(cached.getRecipeFor(plain, level).orElseThrow() == holder, "CachedCheck must warm on ordinary input");
            check(cached.getRecipeFor(input, level).isEmpty(), "Warmed CachedCheck must not bypass query guard");
            check(cached.getRecipeFor(plain, level).orElseThrow() == holder, "Blocked CachedCheck lookup must not poison later plain matching");
            check(manager.getRecipeFor(RecipeType.CRAFTING, plain, level).orElseThrow() == holder, "Blocked query must not poison later plain matching");
        } finally { manager.replaceRecipes(originals); }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void unmarkedShapedAndSmeltingQueriesRejectProgressWithoutMutatingDefinitions(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var shaped = (ShapedRecipe) Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),
            JsonParser.parseString("""
                {"type":"minecraft:crafting_shaped","pattern":["AB"],
                 "key":{"A":{"item":"minecraft:iron_ingot"},"B":{"item":"minecraft:gold_ingot"}},
                 "result":{"id":"minecraft:diamond"}}
                """)).getOrThrow();
        var smelting = new SmeltingRecipe("", CookingBookCategory.MISC, net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT),
            new ItemStack(Items.IRON_NUGGET), 0, 4);
        var shapedHolder = new RecipeHolder<CraftingRecipe>(GraphAssembly.id("query_shaped"), shaped);
        var smeltingHolder = new RecipeHolder<>(GraphAssembly.id("query_smelting"), smelting);
        var fixtures = List.<RecipeHolder<?>>of(shapedHolder, smeltingHolder);
        var packet = new ClientboundUpdateRecipesPacket(fixtures);
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var shapedJson = Recipe.CODEC.encodeStart(ops, shaped).getOrThrow();
        var smeltingJson = Recipe.CODEC.encodeStart(ops, smelting).getOrThrow();
        var originalIngredients = new ArrayList<>(shaped.getIngredients());
        byte[] before = packetBytes(helper, packet);
        try {
            manager.replaceRecipes(fixtures);
            for (String progress : progressValues()) {
                var markedGold = marked(Items.GOLD_INGOT, progress);
                var input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_INGOT), markedGold));
                check(shaped.matches(input, level), "Unmarked shaped direct matches documents query-only boundary");
                check(manager.getRecipeFor(RecipeType.CRAFTING, input, level).isEmpty(), "Auxiliary-only progress must block shaped query");
                check(manager.getRecipesFor(RecipeType.CRAFTING, input, level).isEmpty(), "Auxiliary-only progress must block shaped list query");
                var single = new SingleRecipeInput(marked(Items.IRON_INGOT, progress));
                check(smelting.matches(single, level), "Unmarked smelting direct matches must remain untouched");
                check(manager.getRecipeFor(RecipeType.SMELTING, single, level).isEmpty(), "Single-input query must reject progress");
                check(manager.getRecipesFor(RecipeType.SMELTING, single, level).isEmpty(), "Single-input list query must reject progress");
            }
            check(manager.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(Items.IRON_INGOT)), level).orElseThrow() == smeltingHolder,
                "Ordinary smelting query must retain original holder");
            check(manager.byKey(shapedHolder.id()).orElseThrow() == shapedHolder, "byKey must retain original holder");
            check(manager.getAllRecipesFor(RecipeType.CRAFTING).getFirst() == shapedHolder, "Type inventory must remain unchanged");
            check(manager.getAllRecipesFor(RecipeType.SMELTING).getFirst() == smeltingHolder, "Smelting inventory must remain unchanged");
            check(manager.getRecipes().size() == 2 && manager.getRecipes().containsAll(fixtures), "Global recipe inventory changed");
            for (int i = 0; i < originalIngredients.size(); i++)
                check(shaped.getIngredients().get(i) == originalIngredients.get(i), "Query must not replace original ingredients");
            check(shapedJson.equals(Recipe.CODEC.encodeStart(ops, shaped).getOrThrow()), "Shaped JSON changed after queries");
            check(smeltingJson.equals(Recipe.CODEC.encodeStart(ops, smelting).getOrThrow()), "Smelting JSON changed after queries");
            check(Arrays.equals(before, packetBytes(helper, packet)), "Query guard must not change update_recipes encoding");
        } finally { manager.replaceRecipes(originals); }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void ingredientIgnoringCustomRecipeCannotBypassAnyQueryEntry(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var recipe = new IngredientIgnoringRecipe();
        var holder = new RecipeHolder<>(GraphAssembly.id("query_ingredient_ignoring"), recipe);
        var plain = new SlotInput(List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)));
        try {
            manager.replaceRecipes(List.of(holder));
            var cached = RecipeManager.<SlotInput, IngredientIgnoringRecipe>createCheck(IngredientIgnoringRecipe.TYPE);
            check(cached.getRecipeFor(plain, level).orElseThrow() == holder, "Custom CachedCheck must warm");
            for (String progress : progressValues()) {
                var input = new SlotInput(List.of(new ItemStack(Items.IRON_INGOT), marked(Items.GOLD_INGOT, progress)));
                check(recipe.matches(input, level), "Custom fixture must deliberately ignore component/Ingredient predicates");
                int before = recipe.calls;
                check(manager.getRecipeFor(IngredientIgnoringRecipe.TYPE, input, level).isEmpty(), "Custom plain lookup bypassed guard");
                check(manager.getRecipeFor(IngredientIgnoringRecipe.TYPE, input, level, holder).isEmpty(), "Custom holder lookup bypassed guard");
                check(manager.getRecipeFor(IngredientIgnoringRecipe.TYPE, input, level, holder.id()).isEmpty(), "Custom id lookup bypassed guard");
                check(cached.getRecipeFor(input, level).isEmpty(), "Custom warmed CachedCheck bypassed guard");
                check(manager.getRecipesFor(IngredientIgnoringRecipe.TYPE, input, level).isEmpty(), "Custom list lookup bypassed guard");
                check(recipe.calls == before, "Guard must reject input before invoking custom matches");
                check(cached.getRecipeFor(plain, level).orElseThrow() == holder, "Custom plain lookup after rejection must still work");
            }
            check(manager.getRecipesFor(IngredientIgnoringRecipe.TYPE, plain, level).getFirst() == holder, "Custom ordinary list matching broken");
            // Empty stacks carrying a component are not input material and must not block ordinary queries.
            var empty = marked(Items.GOLD_INGOT, "broken");
            empty.setCount(0);
            var withEmpty = new SlotInput(List.of(new ItemStack(Items.IRON_INGOT), empty));
            check(manager.getRecipeFor(IngredientIgnoringRecipe.TYPE, withEmpty, level).orElseThrow() == holder,
                "Tagged empty slots must not be treated as material");
        } finally { manager.replaceRecipes(originals); }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void furnaceServerTickRejectsProgressButSmeltsPlainInput(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var recipe = new SmeltingRecipe("", CookingBookCategory.MISC, net.minecraft.world.item.crafting.Ingredient.of(Items.RAW_IRON),
            new ItemStack(Items.IRON_INGOT), 0, 4);
        var pos = new BlockPos(1, 1, 1);
        try {
            manager.replaceRecipes(List.of(new RecipeHolder<>(GraphAssembly.id("query_furnace"), recipe)));
            for (String progress : List.of("", progressValues().getFirst(), "{invalid-progress")) {
                helper.setBlock(pos, Blocks.AIR);
                helper.setBlock(pos, Blocks.FURNACE);
                var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
                var input = new ItemStack(Items.RAW_IRON);
                if (!progress.isEmpty()) input.set(GraphAssembly.PROGRESS.get(), progress);
                furnace.setItem(0, input);
                furnace.setItem(1, new ItemStack(Items.COAL));
                for (int tick = 0; tick < 240; tick++)
                    AbstractFurnaceBlockEntity.serverTick(level, helper.absolutePos(pos), level.getBlockState(helper.absolutePos(pos)), furnace);
                if (progress.isEmpty()) {
                    check(furnace.getItem(0).isEmpty() && furnace.getItem(2).is(Items.IRON_INGOT), "Plain furnace control must complete actual serverTick smelting");
                } else {
                    check(furnace.getItem(0).getCount() == 1 && progress.equals(furnace.getItem(0).get(GraphAssembly.PROGRESS.get())),
                        "Ordinary furnace must not consume progress input");
                    check(furnace.getItem(2).isEmpty(), "Ordinary furnace must not create output from progress input");
                }
            }
        } finally {
            helper.setBlock(pos, Blocks.AIR);
            manager.replaceRecipes(originals);
        }
        helper.succeed();
    }

    private static List<String> progressValues() {
        var progress = new Progress("graph_assembly:query_fixture", "start", "start", Map.of(), List.of(), Map.of(), null, List.of("start"));
        var stale = new Progress("graph_assembly:removed_graph", "old", "missing", Map.of(), List.of(), Map.of(), null, List.of("old"));
        return List.of(progress.toJsonString(), stale.toJsonString(), "{invalid-progress", "");
    }

    private static ItemStack marked(net.minecraft.world.item.Item item, String progress) {
        var stack = new ItemStack(item);
        stack.set(GraphAssembly.PROGRESS.get(), progress);
        check(Workpiece.has(stack), "Progress fixture must carry the component even when its value is empty");
        return stack;
    }

    private static byte[] packetBytes(GameTestHelper helper, ClientboundUpdateRecipesPacket packet) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer, packet);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally { buffer.release(); }
    }

    private record SlotInput(List<ItemStack> items) implements RecipeInput {
        @Override public int size() { return items.size(); }
        @Override public ItemStack getItem(int slot) { return items.get(slot); }
    }

    /** Intentionally has no ingredient metadata, no serializable type, and ignores all component predicates. */
    private static final class IngredientIgnoringRecipe implements Recipe<SlotInput> {
        static final RecipeType<IngredientIgnoringRecipe> TYPE = new RecipeType<>() {};
        int calls;
        @Override public boolean matches(SlotInput input, Level level) { calls++; return !input.isEmpty(); }
        @Override public ItemStack assemble(SlotInput input, HolderLookup.Provider registries) { return new ItemStack(Items.DIAMOND); }
        @Override public boolean canCraftInDimensions(int width, int height) { return true; }
        @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return new ItemStack(Items.DIAMOND); }
        @Override public RecipeSerializer<?> getSerializer() { throw new UnsupportedOperationException("Query-only test recipe"); }
        @Override public RecipeType<?> getType() { return TYPE; }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
