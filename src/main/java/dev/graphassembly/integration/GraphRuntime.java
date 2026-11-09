package dev.graphassembly.integration;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.io.InputEligibility;
import dev.graphassembly.core.io.PreflightResult;
import dev.graphassembly.core.io.OutputTrees;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.integration.sequence.NativeSequences;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.progress.TransitionPlan;
import dev.graphassembly.core.progress.TransitionResult;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.Predicate;

public final class GraphRuntime {
    private GraphRuntime() {}
    public record Lookup<R extends Recipe<?>>(boolean handled, List<RecipeHolder<R>> recipes) {
        public Optional<RecipeHolder<R>> first() { return recipes.stream().findFirst(); }
    }
    public static <R extends ProcessingRecipe<?, ?>> Lookup<R> lookup(Level level, ItemStack input, RecipeType<R> type, Class<R> clazz,
                                                                    Predicate<RecipeHolder<R>> matches) {
        return lookup(level, input, type, clazz, matches, false);
    }
    public static <R extends ProcessingRecipe<?, ?>> Lookup<R> lookup(Level level, ItemStack input, RecipeType<R> type, Class<R> clazz,
                                                                    Predicate<RecipeHolder<R>> matches, boolean preview) {
        return lookup(level, input, type, clazz, matches, preview, List.of());
    }
    public static <R extends ProcessingRecipe<?, ?>> Lookup<R> lookup(Level level, ItemStack input, RecipeType<R> type, Class<R> clazz,
                                                                    Predicate<RecipeHolder<R>> matches, boolean preview, List<ItemStack> auxiliary) {
        if (input.isEmpty()) return new Lookup<>(false, List.of());
        if (input.has(AllDataComponents.SEQUENCED_ASSEMBLY) && !NativeSequences.currentStep(level,input))
            return new Lookup<>(!NativeSequences.allowsUpstream(level,input,type,clazz,matches), List.of());
        Progress state;
        try { state = Workpiece.read(level, input); }
        catch (RuntimeException e) { return new Lookup<>(true, List.of()); }
        var index = GraphIndex.get(level);
        var candidates = new ArrayList<RecipeHolder<R>>();
        var descriptions = new ArrayList<String>();
        boolean unsupportedOutput = false;
        List<GraphIndex.Entry> entries;
        var sourceTypes=CreateNativeRecipes.sourceTypes(type);
        if (state == null) entries = sourceTypes.stream().flatMap(source->NativeSequences.entries(level,index,source).stream()).toList();
        else {
            var graph = index.graphs().get(state.graphId());
            if (graph == null) return new Lookup<>(true, List.of());
            var preflight = GraphPipeline.preflight(graph, state);
            if (preflight.incompatibility() != null) return new Lookup<>(true, List.of());
            entries = new ArrayList<>();
            for (String nodeId : preflight.allowed())
                entries.add(new GraphIndex.Entry(graph, graph.nodes().get(nodeId), null));
        }
        for (var entry : entries) {
                if (state == null) dev.graphassembly.diagnostics.PerformanceMetrics.rawEntryCandidate();
                else dev.graphassembly.diagnostics.PerformanceMetrics.stepCandidate();
                var graph = entry.graph();
                var node = entry.node();
                String nodeId = node.id();
                if (state == null && !entry.ingredient().test(input)) continue;
                var nativeType=NativeSequences.type(level,index,entry);
                if (!sourceTypes.contains(nativeType)) continue;
                if (!WorkpieceInputs.matches(level, graph, node, 0, input)) continue;
                boolean inputsMatch = true;
                for (int i = 0; i < auxiliary.size(); i++)
                    if (!WorkpieceInputs.matches(level, graph, node, i + 1, auxiliary.get(i))) inputsMatch = false;
                if (!preview && node.inputs().workpieceInputs().stream().anyMatch(role -> role.itemInput() > auxiliary.size())) inputsMatch = false;
                if (!inputsMatch) continue;
                // Runtime-only check: static recipe indexing also materializes non-Start nodes
                // with an untagged dummy item, so it must not attempt to advance graph progress.
                RecipeType<?> fillingType = com.simibubi.create.AllRecipeTypes.FILLING.getType();
                if (type == fillingType) {
                    var plan = GraphPipeline.plan(graph, state, nodeId, BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
                    if (plan instanceof TransitionPlan.SettlementPlan settlement && OutputTrees.maxStacks(settlement.outputPool()) > 1) {
                        unsupportedOutput = true;
                        continue;
                    }
                }
                var recipe = nativeType==type?NativeSteps.create(level,graph,node,input)
                    :CreateNativeRecipes.processing(level,graph,node,input,type);
                if(recipe==null)continue;
                if (!clazz.isInstance(recipe)) continue;
                var holder = new RecipeHolder<>(ResourceLocation.parse(graph.id()), clazz.cast(recipe));
                // Never attach a closure to a shared recipe object. Snapshot before machines shrink the input.
                var snapshot = input.copyWithCount(1);
                GraphOperation.attach(recipe, new GraphOperation(level, graph, node, snapshot, auxiliary));
                if (!matches.test(holder)) continue;
                candidates.add(holder);
                descriptions.add(graph.id() + "/" + nodeId + " " + node.operation().recipe());
        }
        if (candidates.size() > 1 && !preview) {
            GraphAssembly.LOGGER.error("Ambiguous graph operation {} for {}: {}", type, input, descriptions);
            return new Lookup<>(true, List.of());
        }
        return new Lookup<>(state != null || input.has(AllDataComponents.SEQUENCED_ASSEMBLY) || unsupportedOutput || !candidates.isEmpty(), List.copyOf(candidates));
    }
    public static ItemStack complete(Level level, GraphDefinition<OperationSpec> graph, String node, Progress before, ItemStack snapshot) {
        var plan = GraphPipeline.plan(graph, before, node, BuiltInRegistries.ITEM.getKey(snapshot.getItem()).toString());
        if (plan instanceof TransitionPlan.SettlementPlan settlement && OutputTrees.maxStacks(settlement.outputPool()) > 1)
            throw new IllegalArgumentException("Multiple settlement outputs require completeAll, not a single-stack output API");
        var outputs = materialize(level, GraphPipeline.resolve(plan, level.random::nextDouble), snapshot);
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst();
    }
    public static List<ItemStack> completeAll(Level level, GraphDefinition<OperationSpec> graph, String node, Progress before, ItemStack snapshot,
                                               net.minecraft.util.RandomSource random) {
        return materialize(level, GraphPipeline.resolve(GraphPipeline.plan(graph, before, node, BuiltInRegistries.ITEM.getKey(snapshot.getItem()).toString()), random::nextDouble), snapshot);
    }
    private static List<ItemStack> materialize(Level level, TransitionResult result, ItemStack snapshot) {
        return switch (result) {
            case TransitionResult.Settled settled -> settled.outputs().stream().map(output -> NativeSteps.output(level, output)).toList();
            case TransitionResult.Continued continued -> {
                ItemStack intermediate = continued.carrierItem().equals(BuiltInRegistries.ITEM.getKey(snapshot.getItem()).toString())
                    ? snapshot : NativeSteps.stack(continued.carrierItem());
                yield List.of(Workpiece.write(intermediate, continued.progress()));
            }
        };
    }
    public static String problem(Level level, ItemStack input) {
        var problem = diagnostic(level, input);
        return problem == null ? null : problem.detail();
    }
    public record Diagnostic(String key, String detail) {}
    public static Diagnostic diagnostic(Level level, ItemStack input) {
        try {
            var state = Workpiece.read(level, input);
            return diagnostic(level, state);
        } catch (RuntimeException e) { return new Diagnostic("graph_assembly.stop.invalid", "Invalid saved progress: " + e.getMessage()); }
    }
    public static Diagnostic diagnostic(Level level, Progress state) {
        try {
            if (state == null) return null;
            var index = GraphIndex.get(level);
            var graph = index.graphs().get(state.graphId());
            if (graph == null) return index.disabledReason(state.graphId()) == null
                ? new Diagnostic("graph_assembly.stop.missing", "Missing graph: " + state.graphId())
                : new Diagnostic("graph_assembly.stop.disabled", index.disabledReason(state.graphId()));
            var preflight = GraphPipeline.preflight(graph, state);
            if (preflight.incompatibility() != null) return new Diagnostic("graph_assembly.stop.changed", preflight.incompatibility());
            if (!preflight.blocked().isEmpty() && preflight.allowed().isEmpty()
                    && InputEligibility.auxiliaries(graph, state, preflight).isEmpty()) {
                var reasons = Set.copyOf(preflight.blocked().values().stream().map(PreflightResult.Problem::reason).toList());
                String key = reasons.size() > 1 ? "graph_assembly.stop.blocked"
                    : reasons.contains(PreflightResult.BlockedReason.PRIMARY_CONDITIONS) ? "graph_assembly.stop.conditions"
                    : "graph_assembly.stop.capacity";
                return new Diagnostic(key, "No executable next step: " + String.join("; ", preflight.blocked().values().stream().map(PreflightResult.Problem::message).toList()));
            }
            return null;
        } catch (RuntimeException e) { return new Diagnostic("graph_assembly.stop.invalid", "Invalid saved progress: " + e.getMessage()); }
    }
}
