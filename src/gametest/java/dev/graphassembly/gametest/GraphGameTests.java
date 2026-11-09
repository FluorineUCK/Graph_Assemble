package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.press.*;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.filter.FilterSelection;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Dev-run tests; this package and its empty template are excluded from the published jar. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class GraphGameTests {
    private static GraphRecipe recipe(String nodes, String edges, String outputs) {
        return new GraphRecipe(JsonParser.parseString("{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"transitional_item\":\"minecraft:iron_nugget\",\"nodes\":"
            + nodes + ",\"edges\":" + edges + ",\"outputs\":" + outputs + "}").getAsJsonObject());
    }
    private static RecipeHolder<GraphRecipe> holder(String id, GraphRecipe recipe) { return new RecipeHolder<>(ResourceLocation.parse(id), recipe); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void nativeExecutionAndReload(GameTestHelper helper) {
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        try {
            if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
                check(manager.byKey(ResourceLocation.parse("test:kube_fixture")).isPresent(), "KubeJS binding must generate fixture graph");
            }
            var graphRecipe = recipe("""
                [{"id":"start","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                 {"id":"cut","finish":true,"recipe":{"type":"create:cutting","processing_time":20}}]
                """, "[{\"from\":\"start\",\"to\":\"cut\"}]", "[{\"results\":[{\"item\":\"minecraft:diamond\"}]}]");
            manager.replaceRecipes(List.of(holder("test:flow", graphRecipe)));
            GraphIndex.invalidate();
            helper.setBlock(new BlockPos(1, 1, 1), AllBlocks.MECHANICAL_PRESS.getDefaultState());
            var press = (MechanicalPressBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
            var raw = new ItemStack(Items.IRON_INGOT, 2);
            var found = press.getRecipe(raw);
            check(found.isPresent(), "Real mechanical press hook must find graph entry");
            check(raw.getCount() == 2 && !Workpiece.has(raw), "Recipe lookup must not consume or attach progress");
            var results = found.orElseThrow().value().rollResults(level.random);
            var piece = results.getFirst();
            check(piece.is(Items.IRON_NUGGET) && piece.getMaxStackSize() == 1, "Workpiece must be intermediate and unstackable");
            check(Workpiece.read(piece).routing().lockedNext().equals("cut"), "Random branch must persist chosen target");
            var restored = ItemStack.parse(level.registryAccess(), piece.save(level.registryAccess())).orElseThrow();
            check(Workpiece.read(piece).equals(Workpiece.read(restored)), "Progress component must survive NBT round trip");
            check(press.getRecipe(restored).isEmpty(), "Wrong native operation must stop");
            check(!FillingBySpout.canItemBeFilled(level, restored), "Wrong spout operation must not fall back");
            var filter = GraphAssembly.FILTER.get().getDefaultInstance();
            filter.set(GraphAssembly.FILTER_SELECTION.get(), new FilterSelection("test:flow", List.of("start", "cut")).toJson());
            check(FilterItemStack.of(filter).test(level, raw), "Filter must match raw start ingredient");
            check(FilterItemStack.of(filter).test(level, piece), "Filter must match locked target");
            check(Workpiece.read(piece).equals(Workpiece.read(restored)), "Filter must not mutate lock");
            var cut = GraphRuntime.lookup(level, piece, AllRecipeTypes.CUTTING.getType(), com.simibubi.create.content.kinetics.saw.CuttingRecipe.class, r -> true);
            var finalItem = cut.first().orElseThrow().value().rollResults(level.random).getFirst();
            check(finalItem.is(Items.DIAMOND) && !Workpiece.has(finalItem), "Finish must settle and remove graph ownership");
            manager.replaceRecipes(List.of()); GraphIndex.invalidate();
            check(press.getRecipe(piece).isEmpty() && GraphRuntime.problem(level, piece).contains("Missing graph"), "Missing graph must halt without resetting");
            check(Workpiece.read(piece).routing().lockedNext().equals("cut"), "Reload cannot reroll or erase lock");

            var strict = recipe("""
                [{"id":"single","start":true,"finish":true,"finish_mode":"strict","recipe":{"type":"create:pressing",
                  "results":[{"id":"minecraft:flint"}]}}]
                """, "[]", "[{\"requirements\":{\"marks\":{\"missing\":1}},\"results\":[{\"item\":\"minecraft:diamond\"}]}]");
            manager.replaceRecipes(List.of(holder("test:strict", strict))); GraphIndex.invalidate();
            var byproducts = press.getRecipe(raw).orElseThrow().value().rollResults(level.random);
            check(byproducts.size() == 1 && byproducts.getFirst().is(Items.FLINT), "Strict failure must preserve independent byproduct");

            var overlap = recipe("[{\"id\":\"s\",\"start\":true,\"recipe\":{\"type\":\"create:pressing\"}}]", "[]", "[]");
            var unrelated = recipe("[{\"id\":\"s\",\"start\":true,\"recipe\":{\"type\":\"create:cutting\"}}]", "[]", "[]");
            manager.replaceRecipes(List.of(holder("test:a", overlap), holder("test:b", overlap), holder("test:other", unrelated))); GraphIndex.invalidate();
            var index = GraphIndex.get(level);
            check(index.graphs().keySet().equals(Set.of("test:other")), "All conflicting graphs, and only those, must be disabled");
            check(index.disabledReason("test:a").contains("test:b/s"), "Conflict diagnostics must identify both nodes");
            verifyOtherAdapters(helper, raw);
            helper.succeed();
        } finally { manager.replaceRecipes(originals); GraphIndex.invalidate(); }
    }

    private static void verifyOtherAdapters(GameTestHelper helper, ItemStack raw) {
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        var fill = recipe("""
            [{"id":"s","start":true,"finish":true,"recipe":{"type":"create:filling",
              "ingredients":[{"type":"neoforge:single","fluid":"minecraft:water","amount":250}]}}]
            """, "[]", "[{\"results\":[{\"item\":\"minecraft:diamond\"}]}]");
        manager.replaceRecipes(List.of(holder("test:fill", fill))); GraphIndex.invalidate();
        var water = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        var fillInput = raw.copy();
        check(FillingBySpout.canItemBeFilled(level, fillInput), "Spout must see graph entry");
        check(FillingBySpout.getRequiredAmountForItem(level, fillInput, water) == 250, "Spout must retain fluid quantity");
        check(FillingBySpout.fillItem(level, 250, fillInput, water).is(Items.DIAMOND), "Spout must execute and settle graph");
        check(fillInput.getCount() == raw.getCount() - 1 && water.getAmount() == 750, "Spout must consume exactly one main input and configured fluid");

        var deploying = recipe("""
            [{"id":"s","start":true,"finish":true,"recipe":{"type":"create:deploying","ingredients":[{"item":"minecraft:flint"}],"keep_held_item":true}}]
            """, "[]", "[{\"results\":[{\"item\":\"minecraft:diamond\"}]}]");
        manager.replaceRecipes(List.of(holder("test:deploy", deploying))); GraphIndex.invalidate();
        helper.setBlock(new BlockPos(2, 1, 1), AllBlocks.DEPLOYER.getDefaultState());
        var deployer = (com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity)helper.getBlockEntity(new BlockPos(2, 1, 1));
        deployer.initialize();
        check(deployer.getPlayer() != null, "Deployer fixture must initialize its fake player");
        deployer.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT));
        var deploy = deployer.getRecipe(raw);
        check(deploy != null && deploy.value() instanceof com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe, "Native deployer must find graph with auxiliary input");
        var nativeDeploy = (com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe)deploy.value();
        check(nativeDeploy.shouldKeepHeldItem(), "Native keep_held_item must survive graph wrapper");
        check(nativeDeploy.rollResults(level.random).getFirst().is(Items.DIAMOND), "Deployer output must settle graph");

        var milling = recipe("[{\"id\":\"s\",\"start\":true,\"finish\":true,\"recipe\":{\"type\":\"create:milling\",\"processing_time\":20}}]", "[]",
            "[{\"results\":[{\"item\":\"minecraft:diamond\"}]}]");
        manager.replaceRecipes(List.of(holder("test:mill", milling))); GraphIndex.invalidate();
        helper.setBlock(new BlockPos(3, 1, 1), AllBlocks.MILLSTONE.getDefaultState());
        var mill = (com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity)helper.getBlockEntity(new BlockPos(3, 1, 1));
        mill.initialize(); mill.inputInv.setStackInSlot(0, raw.copy());
        try {
            var process = mill.getClass().getDeclaredMethod("process"); process.setAccessible(true); process.invoke(mill);
        } catch (ReflectiveOperationException e) { throw new AssertionError("Native mill processing hook failed", e); }
        check(mill.inputInv.getStackInSlot(0).getCount() == raw.getCount() - 1 && mill.outputInv.getStackInSlot(0).is(Items.DIAMOND), "Mill must atomically consume and emit graph result");
        var noDuration = recipe("[{\"id\":\"s\",\"start\":true,\"recipe\":{\"type\":\"create:milling\"}}]", "[]", "[]");
        manager.replaceRecipes(List.of(holder("test:zero_duration", noDuration))); GraphIndex.invalidate();
        check(!GraphIndex.get(level).graphs().containsKey("test:zero_duration"), "Unexecutable zero-duration milling must be rejected instead of stalling forever");
    }
}
