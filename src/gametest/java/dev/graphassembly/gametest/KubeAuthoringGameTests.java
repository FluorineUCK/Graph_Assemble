package dev.graphassembly.gametest;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.integration.sequence.SequenceStepRecipe;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

@GameTestHolder("graph_assembly_examples")
@PrefixGameTestTemplate(false)
public final class KubeAuthoringGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void fluentRegistrationPreservesConditionsAndPerExitPools(GameTestHelper helper) {
        var index = GraphIndex.get(helper.getLevel());
        var graph = index.graphs().get("authoring:fluent_graph");
        helper.assertTrue(graph != null, "Rhino fluent graph must load: " + index.disabledReasons());
        var start = ((TransitionResult.Continued) GraphPipeline.resolve(
            GraphPipeline.plan(graph, null, "start", "minecraft:netherite_ingot"), () -> 0)).progress();
        helper.assertTrue(GraphPipeline.allowed(graph, start).equals(java.util.List.of("good")), "Conditional edge filters ineligible random route");
        var node = graph.nodes().get("good");
        helper.assertTrue(node.inputs().workpieceInputs().size() == 2
            && node.inputs().workpieceInput(0).currentNodes().equals(java.util.List.of("start")), "Primary requirements preserve current_nodes and auxiliary role");
        helper.assertTrue(node.settlement().outputRules().getFirst().leaves().size() == 2
            && node.settlement().outputRules().getFirst().leaves().getFirst().weight() == 3,
            "Per-exit weighted pool remains on its exit");
        helper.assertTrue(graph.outputRules().size() == 1 && node.settlement().scrapLeaves().getFirst().item().equals("minecraft:flint"), "Global pool and scrap remain independent");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void fluentNativeSequenceKeepsComponentsAndRecipeSnapshot(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var holder = manager.byKey(ResourceLocation.parse("authoring:fluent_sequence")).orElseThrow();
        helper.assertTrue(holder.value() instanceof SequencedAssemblyRecipe, "Fluent sequence must be real native Create recipe");
        var recipe = (SequencedAssemblyRecipe) holder.value();
        helper.assertTrue(recipe.getLoops() == 2 && recipe.getSequence().size() == 1, "Outer Create owns loops and steps");
        var step = (SequenceStepRecipe) recipe.getSequence().getFirst().getRecipe();
        helper.assertTrue(step.step().getAsJsonObject("recipe").get("cookingtime").getAsInt() == 31,
            "Upstream recipe mutations after step() cannot change snapshot");
        helper.assertTrue(recipe.resultPool.getFirst().getStack().is(Items.IRON_PICKAXE)
            && recipe.resultPool.getFirst().getStack().getDamageValue() == 5
            && recipe.resultPool.get(1).getChance() == 0.25F, "Native result components and weights survive");
        helper.assertTrue(manager.byKey(ResourceLocation.parse("authoring:nested_heat")).isEmpty(), "Nested recipe is consumed");
        helper.succeed();
    }
}
