package dev.graphassembly.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.*;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.diagnostics.PerformanceMetrics;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** Rebuilt when the recipe-holder snapshot changes, including datapack reload and server sync. */
public final class GraphIndex {
    private static final Map<RecipeManager, GraphIndex> CACHE = new WeakHashMap<>();
    private final List<RecipeHolder<GraphRecipe>> snapshot;
    private final Map<net.minecraft.resources.ResourceLocation, RecipeHolder<?>> nativeSnapshot = new LinkedHashMap<>();
    private final Map<String, GraphDefinition> graphs = new LinkedHashMap<>();
    private final Map<String, String> disabled = new LinkedHashMap<>();
    private final Map<String, Ingredient> entrances = new HashMap<>();
    public record Entry(GraphDefinition graph, Node node, Ingredient ingredient) {}
    private final Map<RecipeType<?>, List<Entry>> entriesByType = new IdentityHashMap<>();
    private final Map<String, RecipeType<?>> nativeTypes = new HashMap<>();

    public static synchronized GraphIndex get(Level level) {
        var recipes = level.getRecipeManager().getAllRecipesFor(GraphAssembly.RECIPE_TYPE.get());
        var old = CACHE.get(level.getRecipeManager());
        if (old != null && sameRecipes(old.snapshot, recipes) && old.sameNativeReferences(level)) return old;
        var index = new GraphIndex(level, recipes);
        CACHE.put(level.getRecipeManager(), index);
        return index;
    }
    public static synchronized void invalidate() { CACHE.clear(); }
    /** Pure cache-identity peek: callers can gate projections without rebuilding external codecs. */
    public static synchronized boolean cached(Level level,GraphIndex index) {
        return index!=null && CACHE.get(level.getRecipeManager())==index;
    }

    /** RecipeHolder equality may compare IDs only; a same-ID datapack replacement still changes the graph. */
    private static boolean sameRecipes(List<RecipeHolder<GraphRecipe>> before, List<RecipeHolder<GraphRecipe>> now) {
        if (before.size() != now.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            PerformanceMetrics.indexHolderComparison();
            var a = before.get(i); var b = now.get(i);
            if (!a.id().equals(b.id()) || a.value() != b.value()) return false;
        }
        return true;
    }

    private GraphIndex(Level level, List<RecipeHolder<GraphRecipe>> recipes) {
        PerformanceMetrics.indexBuild();
        snapshot = List.copyOf(recipes);
        Map<String, Map<String, Recipe<?>>> templates = new HashMap<>();
        for (var holder : recipes) {
            String id = holder.id().toString();
            String nodeId = "<graph>";
            try {
                var graph = holder.value().definition(id);
                var intermediate = NativeSteps.stack(graph.transitionalItem());
                var nodes = new HashMap<String, Recipe<?>>();
                for (var node : graph.nodes().values()) {
                    nodeId = node.id();
                    if (node.recipe().has("recipe_id")) {
                        var reference = net.minecraft.resources.ResourceLocation.parse(node.recipe().get("recipe_id").getAsString());
                        nativeSnapshot.put(reference, level.getRecipeManager().byKey(reference).orElse(null));
                    }
                    if (node.transitionalItem() != null) NativeSteps.stack(node.transitionalItem());
                    nodes.put(node.id(), NativeSteps.template(level, graph, node, intermediate));
                    if (node.start()) entrances.put(key(id, node.id()), NativeSteps.ingredient(level,
                        node.ingredient() == null ? graph.ingredient() : node.ingredient()));
                    node.outputs().forEach(rule -> rule.results().forEach(out -> NativeSteps.output(level, out)));
                    node.scrap().forEach(out -> NativeSteps.output(level, out));
                }
                graph.outputs().forEach(rule -> rule.results().forEach(out -> NativeSteps.output(level, out)));
                graphs.put(id, graph);
                templates.put(id, nodes);
            } catch (RuntimeException e) { disabled.put(id, "Node " + nodeId + ": " + e.getMessage()); }
        }
        record Entrance(GraphDefinition graph, Node node) {}
        List<Entrance> starts = new ArrayList<>();
        for (var graph : graphs.values()) for (var node : graph.nodes().values()) if (node.start()) starts.add(new Entrance(graph, node));
        for (int i = 0; i < starts.size(); i++) for (int j = i + 1; j < starts.size(); j++) {
            var a = starts.get(i); var b = starts.get(j);
            if (!runtimeConditions(a.node()) && !runtimeConditions(b.node()) && overlap(entrance(a.graph().id(), a.node().id()), entrance(b.graph().id(), b.node().id())) &&
                overlap(templates.get(a.graph().id()).get(a.node().id()), templates.get(b.graph().id()).get(b.node().id())))
                conflict(a.graph(), a.node(), b.graph(), b.node());
        }
        for (var graph : graphs.values()) for (var current : graph.nodes().values()) {
            if (current.branchMode() == BranchMode.RANDOM || current.route() != null) continue;
            var targets = graph.outgoing(current.id()).stream().map(e -> graph.nodes().get(e.to())).distinct().toList();
            for (int i = 0; i < targets.size(); i++) for (int j = i + 1; j < targets.size(); j++) {
                var a = targets.get(i); var b = targets.get(j);
                if (!runtimeConditions(a) && !runtimeConditions(b) && overlap(templates.get(graph.id()).get(a.id()), templates.get(graph.id()).get(b.id()))) conflict(graph, a, graph, b);
            }
        }
        disabled.forEach((id, error) -> GraphAssembly.LOGGER.error("Disabled graph {}: {}", id, error));
        disabled.keySet().forEach(graphs::remove);
        for (var graph : graphs.values()) for (var node : graph.nodes().values()) {
            // Capture only admitted nodes, without exposing or retaining mutable recipe templates.
            RecipeType<?> type = templates.get(graph.id()).get(node.id()).getType();
            nativeTypes.put(key(graph.id(), node.id()), type);
            if (node.start()) entriesByType.computeIfAbsent(type, ignored -> new ArrayList<>())
                .add(new Entry(graph, node, entrance(graph.id(), node.id())));
        }
        entriesByType.replaceAll((ignored, entries) -> List.copyOf(entries));
    }
    private boolean sameNativeReferences(Level level) {
        // No JSON reparsing on the hot lookup path; compare captured holder identities only.
        for (var reference : nativeSnapshot.entrySet())
            if (level.getRecipeManager().byKey(reference.getKey()).orElse(null) != reference.getValue()) return false;
        return true;
    }
    private void conflict(GraphDefinition ga, Node a, GraphDefinition gb, Node b) {
        String reason = "Ambiguous " + a.recipe().get("type") + " conditions: " + ga.id() + "/" + a.id() + " <-> " + gb.id() + "/" + b.id()
            + " (" + a.recipe() + " / " + b.recipe() + ")";
        disabled.merge(ga.id(), reason, (x, y) -> x + "; " + y);
        if (!ga.id().equals(gb.id())) disabled.merge(gb.id(), reason, (x, y) -> x + "; " + y);
    }
    private static boolean runtimeConditions(Node node) {
        String type = node.recipe().get("type").getAsString();
        return !node.workpieceInputs().isEmpty() || type.equals("create:mixing") || type.equals("create:compacting")
            || type.equals("minecraft:crafting_shaped") || type.equals("minecraft:crafting_shapeless");
    }
    private static boolean overlap(Ingredient a, Ingredient b) {
        if (!a.isSimple() || !b.isSimple()) return false; // dynamic predicates are checked at execution time
        return Arrays.stream(a.getItems()).anyMatch(b::test);
    }
    private static boolean overlap(Recipe<?> first, Recipe<?> second) {
        if (!(first instanceof ProcessingRecipe<?, ?> a) || !(second instanceof ProcessingRecipe<?, ?> b)) return false;
        if (a.getType() != b.getType()) return false;
        if (a.getIngredients().size() != b.getIngredients().size() || a.getFluidIngredients().size() != b.getFluidIngredients().size()) return false;
        for (int i = 1; i < a.getIngredients().size(); i++) if (!overlap(a.getIngredients().get(i), b.getIngredients().get(i))) return false;
        for (int i = 0; i < a.getFluidIngredients().size(); i++) {
            var x = a.getFluidIngredients().get(i).ingredient(); var y = b.getFluidIngredients().get(i).ingredient();
            if (!x.isSimple() || !y.isSimple() || Arrays.stream(x.getStacks()).noneMatch(y::test)) return false;
        }
        return true;
    }
    private static String key(String graph, String node) { return graph + "\u0000" + node; }
    public Ingredient entrance(String graph, String node) { return entrances.get(key(graph, node)); }
    public List<Entry> entries(RecipeType<?> type) { return entriesByType.getOrDefault(type, List.of()); }
    /** Actual decoded native identity; null for missing or disabled graphs/nodes. Never decodes on lookup. */
    public RecipeType<?> nativeType(String graph, String node) { return nativeTypes.get(key(graph, node)); }
    public Map<String, GraphDefinition> graphs() { return Collections.unmodifiableMap(graphs); }
    public String disabledReason(String id) { return disabled.get(id); }
    public Map<String, String> disabledReasons() { return Collections.unmodifiableMap(disabled); }
}
