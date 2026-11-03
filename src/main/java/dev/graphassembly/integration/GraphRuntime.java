package dev.graphassembly.integration;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.*;
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
        if (input.isEmpty() || input.has(AllDataComponents.SEQUENCED_ASSEMBLY)) return new Lookup<>(false, List.of());
        Progress state;
        try { state = Workpiece.read(level, input); }
        catch (RuntimeException e) { return new Lookup<>(true, List.of()); }
        var index = GraphIndex.get(level);
        var candidates = new ArrayList<RecipeHolder<R>>();
        var descriptions = new ArrayList<String>();
        boolean unsupportedOutput = false;
        List<GraphIndex.Entry> entries;
        var sourceTypes=CreateNativeRecipes.sourceTypes(type);
        if (state == null) entries = sourceTypes.stream().flatMap(source->index.entries(source).stream()).toList();
        else {
            var graph = index.graphs().get(state.graphId());
            if (graph == null) return new Lookup<>(true, List.of());
            var preflight = GraphEngine.preflight(graph, state);
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
                var nativeType=index.nativeType(graph.id(),nodeId);
                if (!sourceTypes.contains(nativeType)) continue;
                if (!WorkpieceInputs.matches(level, graph, node, 0, input)) continue;
                boolean inputsMatch = true;
                for (int i = 0; i < auxiliary.size(); i++)
                    if (!WorkpieceInputs.matches(level, graph, node, i + 1, auxiliary.get(i))) inputsMatch = false;
                if (!preview && node.workpieceInputs().stream().anyMatch(role -> role.itemInput() > auxiliary.size())) inputsMatch = false;
                if (!inputsMatch) continue;
                // Runtime-only check: static recipe indexing also materializes non-Start nodes
                // with an untagged dummy item, so it must not attempt to advance graph progress.
                RecipeType<?> fillingType = com.simibubi.create.AllRecipeTypes.FILLING.getType();
                if (type == fillingType) {
                    var plan = GraphEngine.plan(graph, state, nodeId, BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
                    if (plan.settled() && OutputTrees.maxStacks(plan.pool()) > 1) {
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
                descriptions.add(graph.id() + "/" + nodeId + " " + node.recipe());
        }
        if (candidates.size() > 1 && !preview) {
            GraphAssembly.LOGGER.error("Ambiguous graph operation {} for {}: {}", type, input, descriptions);
            return new Lookup<>(true, List.of());
        }
        return new Lookup<>(state != null || unsupportedOutput || !candidates.isEmpty(), List.copyOf(candidates));
    }
    public static ItemStack complete(Level level, GraphDefinition graph, String node, Progress before, ItemStack snapshot) {
        var plan = GraphEngine.plan(graph, before, node, BuiltInRegistries.ITEM.getKey(snapshot.getItem()).toString());
        if (plan.settled() && OutputTrees.maxStacks(plan.pool()) > 1)
            throw new IllegalArgumentException("Multiple settlement outputs require completeAll, not a single-stack output API");
        var outputs = materialize(level, plan.roll(level.random::nextDouble), snapshot);
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst();
    }
    public static List<ItemStack> completeAll(Level level, GraphDefinition graph, String node, Progress before, ItemStack snapshot,
                                               net.minecraft.util.RandomSource random) {
        return materialize(level, GraphEngine.complete(graph, before, node,
            BuiltInRegistries.ITEM.getKey(snapshot.getItem()).toString(), random::nextDouble), snapshot);
    }
    private static List<ItemStack> materialize(Level level, Completion result, ItemStack snapshot) {
        if (result.settled()) return result.outputs().stream().map(output -> NativeSteps.output(level, output)).toList();
        ItemStack intermediate = result.item().equals(BuiltInRegistries.ITEM.getKey(snapshot.getItem()).toString()) ? snapshot : NativeSteps.stack(result.item());
        return List.of(Workpiece.write(intermediate, result.state()));
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
            var preflight = GraphEngine.preflight(graph, state);
            if (preflight.incompatibility() != null) return new Diagnostic("graph_assembly.stop.changed", preflight.incompatibility());
            if (!preflight.blocked().isEmpty() && preflight.allowed().isEmpty()
                    && WorkpieceParticipation.auxiliaries(graph, state, preflight).isEmpty()) {
                var reasons = Set.copyOf(preflight.blockedReasons().values());
                String key = reasons.size() > 1 ? "graph_assembly.stop.blocked"
                    : reasons.contains(GraphEngine.BlockedReason.PRIMARY_CONDITIONS) ? "graph_assembly.stop.conditions"
                    : "graph_assembly.stop.capacity";
                return new Diagnostic(key, "No executable next step: " + String.join("; ", preflight.blocked().values()));
            }
            return null;
        } catch (RuntimeException e) { return new Diagnostic("graph_assembly.stop.invalid", "Invalid saved progress: " + e.getMessage()); }
    }
}
