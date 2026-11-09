package dev.graphassembly.gametest;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Set;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class ThreePathOutputsGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void actualKubeScriptIsExecutableAndSettlesAllThreeRoutes(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("kubejs")) { helper.succeed(); return; }
        String id = "example:three_path_outputs";
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow();
        check(holder.value() instanceof GraphRecipe, "The actual example script must register one graph");
        var recipe = (GraphRecipe) holder.value();
        var index = GraphIndex.get(helper.getLevel());
        check(index.graphs().containsKey(id), "Example must pass native codecs and ambiguity checks: " + index.disabledReason(id));
        var graph = index.graphs().get(id);
        check(graph.nodes().size() == 7 && graph.outputRules().isEmpty(), "One graph, three local finish rules");
        for (int n : new int[]{1, 2}) check(run(helper, graph, "cut", n, "finish_iron").is(Items.IRON_INGOT), "Iron route");
        for (int n : new int[]{2, 3}) check(run(helper, graph, "wire", n, "finish_gold").is(Items.GOLD_INGOT), "Gold route");
        check(run(helper, graph, "fill", 1, "finish_diamond").is(Items.DIAMOND), "Diamond route");
        for (int n : new int[]{1, 4})
            check(Set.of(Items.GOLD_NUGGET, Items.REDSTONE).contains(run(helper, graph, "wire", n, "finish_gold").getItem()), "Gold scrap pool isolation");
        check(Set.of(Items.IRON_NUGGET, Items.GRAVEL).contains(run(helper, graph, "cut", 3, "finish_iron").getItem()), "Iron scrap pool isolation");
        check(Set.of(Items.QUARTZ, Items.FLINT).contains(run(helper, graph, "fill", 2, "finish_diamond").getItem()), "Diamond scrap pool isolation");
        helper.succeed();
    }

    private static ItemStack run(GameTestHelper helper, GraphDefinition<OperationSpec> graph, String step, int visits, String end) {
        var level = helper.getLevel();
        var stack = GraphRuntime.complete(level, graph, "blank", null, new ItemStack(Items.RAW_IRON));
        for (int i = 0; i < visits; i++) {
            NativeSteps.create(level, graph, graph.nodes().get(step), stack);
            stack = GraphRuntime.complete(level, graph, step, Workpiece.read(level, stack), stack);
            check(Workpiece.has(stack) && stack.getCount() == 1, "Intermediate workpiece must retain graph progress");
        }
        stack = GraphRuntime.complete(level, graph, end, Workpiece.read(level, stack), stack);
        check(!stack.isEmpty() && !Workpiece.has(stack), "Success and scrap must both remove graph progress");
        var finalStack = stack;
        var failure = graph.nodes().get(end).settlement().scrapLeaves().stream()
            .filter(out -> out.item().equals(BuiltInRegistries.ITEM.getKey(finalStack.getItem()).toString())).findFirst();
        check(stack.getCount() == failure.map(out -> out.count()).orElse(1), "Materialized output count");
        return stack;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
