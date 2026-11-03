package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.filter.FilterSelection;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class OverflowGameTests {
    @GameTest(template = "test_empty", batch = "graph_assembly_overflow", timeoutTicks = 100)
    public static void nativeOverflowStopsBeforeConsumptionAndExplainsWhy(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            var json = JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"rewards":{"x":2147483647}},
                          {"id":"fill","rewards":{"x":1},"recipe":{"type":"create:filling",
                           "ingredients":[{"type":"neoforge:single","fluid":"minecraft:water","amount":250}]}}],
                 "edges":[{"from":"s","to":"s"},{"from":"s","to":"fill"}]}
                """).getAsJsonObject();
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:overflow"), new GraphRecipe(json))));
            GraphIndex.invalidate();
            helper.setBlock(new BlockPos(1, 1, 1), AllBlocks.MECHANICAL_PRESS.getDefaultState());
            var press = (MechanicalPressBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
            var piece = press.getRecipe(new ItemStack(Items.IRON_INGOT)).orElseThrow().value().rollResults(level.random).getFirst();
            String saved = piece.get(GraphAssembly.PROGRESS.get());
            check(Workpiece.read(piece).marks().get("x") == Integer.MAX_VALUE, "First legal reward must not be clamped");
            check(press.getRecipe(piece).isEmpty(), "Press must reject the overflowing loop before native consumption");
            check(!FillingBySpout.canItemBeFilled(level, piece), "Spout preview must not offer overflowing work");
            var water = new FluidStack(Fluids.WATER, 1000);
            check(FillingBySpout.fillItem(level, 250, piece, water).isEmpty(), "Forced spout attempt must stop, not throw");
            check(piece.getCount() == 1 && water.getAmount() == 1000, "Stopped work must preserve item and fluid");
            check(saved.equals(piece.get(GraphAssembly.PROGRESS.get())), "Stopped work cannot change marks, history or locks");
            check(!new FilterSelection("test:overflow", List.of("s", "fill")).matches(level, piece), "Overflowing next steps cannot pass graph filters");
            String reason = GraphRuntime.problem(level, piece);
            check(GraphRuntime.diagnostic(level, piece).key().equals("graph_assembly.stop.capacity"), "Player reason must be a translation key, not parsed English");
            check(reason != null && reason.contains("fill") && reason.contains("x") && reason.contains("2147483647"),
                "Tooltip diagnostic must name the blocked node, mark and limit: " + reason);
            helper.succeed();
        } finally {
            manager.replaceRecipes(originals);
            GraphIndex.invalidate();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
