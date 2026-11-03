package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.*;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.recipe.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

/** Real registry and Ingredient checks for the P4 lookup index. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class P4RuntimeGameTests {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static RecipeHolder<GraphRecipe> recipe(String id, String ingredient, String type, boolean loop) {
        var json = JsonParser.parseString("""
            {"ingredient":%s,"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"%s"}}],"edges":%s}
            """.formatted(ingredient, type, loop ? "[{\"from\":\"s\",\"to\":\"s\"}]" : "[]")).getAsJsonObject();
        return new RecipeHolder<>(ResourceLocation.parse(id), new GraphRecipe(json));
    }
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void rawTypeBucketsPreserveDynamicPredicatesAndEncounterOrder(GameTestHelper helper) {
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of(
                recipe("test:p4_cut", "{\"item\":\"minecraft:iron_ingot\"}", "create:cutting", false),
                recipe("test:p4_dynamic", "{\"type\":\"neoforge:components\",\"items\":[\"minecraft:iron_ingot\"],\"components\":{}}", "create:pressing", false),
                recipe("test:p4_gold", "{\"item\":\"minecraft:gold_ingot\"}", "create:pressing", false),
                recipe("test:p4_simple", "{\"item\":\"minecraft:iron_ingot\"}", "create:pressing", false)));
            GraphIndex.invalidate();
            var index = GraphIndex.get(level);
            check(index.graphs().size() == 4, "Dynamic/simple overlap must remain enabled: " + index.disabledReasons());
            var input = new ItemStack(Items.IRON_INGOT);
            input.set(DataComponents.CUSTOM_NAME, Component.literal("extra component"));
            var dynamic = index.entrance("test:p4_dynamic", "s");
            check(!dynamic.isSimple() && dynamic.test(input), "Dynamic predicate must test real extra-component input");
            check(Arrays.stream(dynamic.getItems()).noneMatch(s -> ItemStack.isSameItemSameComponents(s, input)),
                "Matched input must be absent from dynamic display stacks");
            var preview = GraphRuntime.lookup(level, input, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class, h -> true, true);
            check(preview.recipes().stream().map(h -> h.id().toString()).toList().equals(List.of("test:p4_dynamic", "test:p4_simple")),
                "Preview candidates must preserve interleaved graph order: " + preview.recipes());
            var runtime = GraphRuntime.lookup(level, input, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class, h -> true);
            check(runtime.handled() && runtime.recipes().isEmpty(), "Ambiguous dynamic/simple operation must be handled, not native fallthrough");
            var gold = GraphRuntime.lookup(level, new ItemStack(Items.GOLD_INGOT), AllRecipeTypes.PRESSING.getType(), PressingRecipe.class, h -> true);
            check(gold.recipes().size() == 1 && gold.recipes().getFirst().id().toString().equals("test:p4_gold"), "Simple input must find gold entry");
            helper.succeed();
        } finally { manager.replaceRecipes(originals); GraphIndex.invalidate(); RuntimeSnapshots.release(manager); }
    }

    @GameTest(template="test_empty", timeoutTicks=100)
    public static void twoNativeRecipesCaptureIndependentStateAndCountOneInput(GameTestHelper helper) {
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of(recipe("test:p4_loop", "{\"item\":\"minecraft:iron_ingot\"}", "create:pressing", true)));
            GraphIndex.invalidate();
            var raw = new ItemStack(Items.IRON_INGOT, 7);
            var graph = GraphIndex.get(level).graphs().get("test:p4_loop");
            var previous = GraphEngine.complete(graph, null, "s", "minecraft:iron_ingot", () -> 0).state();
            var piece = Workpiece.write(new ItemStack(Items.IRON_NUGGET, 3), previous);
            int pieceCount = piece.getCount();
            var first = GraphRuntime.lookup(level, raw, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class, h -> true).first().orElseThrow().value();
            var second = GraphRuntime.lookup(level, piece, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class, h -> true).first().orElseThrow().value();
            check(first != second, "Native recipes cannot be shared across operations");
            var firstOutput = first.rollResults(level.random).getFirst();
            var secondOutput = second.rollResults(level.random).getFirst();
            check(firstOutput.getCount() == 1 && secondOutput.getCount() == 1 && raw.getCount() == 7 && piece.getCount() == pieceCount,
                "Each closure must use a count-one snapshot without consuming caller input: outputs=" + firstOutput.getCount() + "," + secondOutput.getCount()
                    + " inputs=" + raw.getCount() + "," + piece.getCount() + " originalPiece=" + pieceCount);
            check(Workpiece.read(firstOutput).checkpoints().equals(List.of("s")), "First closure must retain raw start state");
            check(Workpiece.read(secondOutput).checkpoints().equals(List.of("s", "s")), "Second closure must retain workpiece state");
            helper.succeed();
        } finally { manager.replaceRecipes(originals); GraphIndex.invalidate(); RuntimeSnapshots.release(manager); }
    }
}
