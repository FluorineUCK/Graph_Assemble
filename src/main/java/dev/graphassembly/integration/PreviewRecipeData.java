package dev.graphassembly.integration;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.recipe.RecipeMode;
import dev.graphassembly.core.settlement.FinishMode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/** Materializes inline preview data without asserting that a device can execute it. */
public final class PreviewRecipeData {
    private PreviewRecipeData() {}
    /** Explicit static-view limitation, not an invalid runtime graph or a materialized item. */
    public static final class UnknownDynamicCarrier extends IllegalArgumentException {
        public UnknownDynamicCarrier(String node){super("Dynamic native carrier is not statically known before node "+node);}
    }
    // Pure structural metadata: this dataflow must not initialize native game/codec adapters.
    public static boolean dynamicCarrier(NodeDefinition<OperationSpec> node) {
        return switch(node.operation().recipe().get("type").getAsString()) {
            case "graph_assembly:anvil", "graph_assembly:brewing", "minecraft:smithing_trim", "minecraft:crafting_special_armordye", "minecraft:crafting_special_repairitem" -> true;
            default -> false;
        };
    }

    /** A representative inline view, not a new entrance or an execution recipe. */
    public static JsonObject forNode(GraphDefinition<OperationSpec> graph, NodeDefinition<OperationSpec> node) {
        if ((node.operation().recipeMode() == RecipeMode.NATIVE)) return node.operation().recipe();
        if (isOperationDescriptor(node.operation().recipe()) && !node.operation().recipe().has("recipe_id"))
            return node.operation().recipe().deepCopy();
        if (node.roles().start() && node.inputs().workpieceInput(0) == null) {
            return materialize(node.operation().recipe(), node.inputs().ingredientOverride() == null ? graph.defaults().ingredient() : node.inputs().ingredientOverride(),
                node.effects().carrierOverride() == null ? graph.defaults().transitionalItem() : node.effects().carrierOverride());
        }
        // A carrier override changes the OUTPUT of this step, never its input.
        // At a multi-carrier merge, show one consistent input/output pair rather
        // than presenting alternative carriers as simultaneously consumed items.
        var inputs = carriedInputs(graph, node);
        String input = inputs.isEmpty() ? graph.defaults().transitionalItem() : inputs.getFirst();
        return materialize(node.operation().recipe(), input, node.effects().carrierOverride() == null ? input : node.effects().carrierOverride());
    }

    /** Reachable carrier identities before a node; finite dataflow also handles loops.
     * History/reward predicates still belong to runtime; current_nodes filters primary edges here. */
    public static List<String> carriedInputs(GraphDefinition<OperationSpec> graph, NodeDefinition<OperationSpec> target) {
        // Null item is an unknown native result, never the graph default. Keeping it in
        // the same finite traversal also prevents mixed known/unknown merges looking complete.
        record State(String node, String item) {}
        var queue = new ArrayDeque<State>();
        var seen = new HashSet<State>();
        var inputs = new LinkedHashSet<String>();
        boolean unknown=false;
        for (var node : graph.nodes().values()) if (node.roles().start() && node.inputs().workpieceInput(0) == null) {
            var seed = new State(node.id(),dynamicCarrier(node)?null:node.effects().carrierOverride() == null ? graph.defaults().transitionalItem() : node.effects().carrierOverride());
            if (seen.add(seed)) queue.add(seed);
        }
        while (!queue.isEmpty()) {
            var state = queue.remove();
            var previous = graph.nodes().get(state.node());
            if (previous.roles().finish() && previous.settlement().finishMode() == FinishMode.STRICT) continue;
            var targets = previous.routing().redirect() == null
                ? graph.topology().outgoing(state.node()).stream().map(edge -> edge.to()).toList()
                : List.of(previous.routing().redirect().pendingRoute().node());
            for (var nextId : targets) {
                var next = graph.nodes().get(nextId);
                var primary = next.inputs().workpieceInput(0);
                if (primary != null && !primary.currentNodes().isEmpty() && !primary.currentNodes().contains(state.node())) continue;
                if (next.id().equals(target.id())) {if(state.item()==null)unknown=true;else inputs.add(state.item());}
                var after = new State(next.id(),dynamicCarrier(next)?null:next.effects().carrierOverride() == null ? state.item() : next.effects().carrierOverride());
                if (seen.add(after)) queue.add(after);
            }
        }
        if(unknown)throw new UnknownDynamicCarrier(target.id());
        return List.copyOf(inputs);
    }

    /** Supplier candidates are authoritative display data. Hide only proven internal items;
     * an unknown dynamic carrier means hide none, not discard the supplier's auxiliaries. */
    public static List<String> nativeInputsToHide(GraphDefinition<OperationSpec> graph,NodeDefinition<OperationSpec> node) {
        if(node.roles().start()||node.operation().completeRecipe())return List.of();
        try{return carriedInputs(graph,node);}
        catch(UnknownDynamicCarrier unknown){return List.of();}
    }

    public static JsonObject materialize(JsonObject source, String inputItem, String outputItem) {
        JsonObject input = new JsonObject();
        input.addProperty("item", inputItem);
        return materialize(source, input, outputItem);
    }

    /** Keep tag, alternatives and component-sensitive entrance ingredients intact. */
    public static JsonObject materialize(JsonObject source, JsonElement primaryInput, String outputItem) {
        if (source.has("recipe_id"))
            throw new IllegalArgumentException("Native recipe references must be resolved unchanged, not materialized");
        JsonObject recipe = source.deepCopy();
        // Own operation descriptors do not encode a primary or an output.
        if (isOperationDescriptor(recipe)) return recipe;
        JsonElement input = primaryInput.deepCopy();
        JsonObject output = new JsonObject();
        output.addProperty("id", outputItem);
        String type = recipe.get("type").getAsString();
        switch (type) {
            case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking" -> {
                recipe.add("ingredient", input);
                recipe.add("result", output);
            }
            default -> {
                prepend(recipe, "ingredients", input);
                prepend(recipe, "results", output);
            }
        }
        return recipe;
    }

    private static boolean isOperationDescriptor(JsonObject recipe) {
        return switch (recipe.get("type").getAsString()) {
            case "graph_assembly:anvil", "graph_assembly:brewing" -> true;
            default -> false;
        };
    }

    private static void prepend(JsonObject recipe, String field, JsonElement primary) {
        JsonArray values = new JsonArray();
        values.add(primary);
        if (recipe.has(field)) recipe.getAsJsonArray(field).forEach(values::add);
        recipe.add(field, values);
    }
}
