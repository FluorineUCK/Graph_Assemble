package dev.graphassembly.compat.kubejs;

import com.google.gson.*;
import dev.graphassembly.codec.GraphCodec;

/** Only emits author data; defaults and execution are never reimplemented in the script layer. */
public final class GraphBuilder {
    private final JsonObject json = new JsonObject();
    private final JsonArray nodes = new JsonArray(), edges = new JsonArray(), outputs = new JsonArray();
    public GraphBuilder(String transitionalItem) {
        json.addProperty("type", "graph_assembly:assembly"); json.addProperty("transitional_item", transitionalItem);
        json.add("nodes", nodes); json.add("edges", edges); json.add("outputs", outputs);
    }
    public GraphBuilder ingredient(JsonElement ingredient) { json.add("ingredient", ingredient.deepCopy()); return this; }
    public GraphBuilder node(String id, JsonObject recipe, JsonObject options) {
        JsonObject node = options.deepCopy(); node.addProperty("id", id); node.add("recipe", recipe.deepCopy()); nodes.add(node); return this;
    }
    public GraphBuilder node(String id, JsonObject recipe) { return node(id, recipe, new JsonObject()); }
    public GraphBuilder edge(String from, String to) { return weightedEdge(from, to, 1); }
    public GraphBuilder edge(String from, String to, JsonObject options) {
        JsonObject edge = options.deepCopy(); edge.addProperty("from", from); edge.addProperty("to", to); edges.add(edge); return this;
    }
    public GraphBuilder weightedEdge(String from, String to, double weight) {
        JsonObject edge = new JsonObject(); edge.addProperty("from", from); edge.addProperty("to", to); edge.addProperty("weight", weight); edges.add(edge); return this;
    }
    public GraphBuilder bidirectionalEdge(String from, String to) { edge(from, to); edge(to, from); return this; }
    public GraphBuilder output(JsonObject rule) { outputs.add(rule.deepCopy()); return this; }
    public GraphBuilder finishOutput(String nodeId, JsonArray results) { return finishOutput(nodeId, results, new JsonObject()); }
    public GraphBuilder finishOutput(String nodeId, JsonArray results, JsonObject requirements) {
        var node = findNode(nodeId);
        var rule = new JsonObject();
        rule.add("results", results.deepCopy()); rule.add("requirements", requirements.deepCopy());
        append(node, "outputs", rule);
        node.addProperty("finish", true);
        return this;
    }
    public GraphBuilder scrap(String nodeId, JsonArray results) {
        var node = findNode(nodeId);
        for (var result : results) append(node, "scrap", result.deepCopy());
        return this;
    }
    /** Changes only logical primary input 0, retaining its current_nodes and all auxiliary roles. */
    public GraphBuilder requirements(String nodeId, JsonObject requirements) {
        var node = findNode(nodeId);
        var inputs = node.has("workpiece_inputs") ? node.getAsJsonArray("workpiece_inputs") : new JsonArray();
        JsonObject primary = null;
        for (var input : inputs) if (input.getAsJsonObject().get("item_input").getAsInt() == 0) {
            primary = input.getAsJsonObject(); break;
        }
        if (primary == null) { primary = new JsonObject(); primary.addProperty("item_input", 0); inputs.add(primary); }
        primary.add("requirements", requirements.deepCopy()); node.add("workpiece_inputs", inputs);
        return this;
    }
    public GraphBuilder conditionalEdge(String from, String to, JsonObject requirements) { return conditionalEdge(from, to, requirements, 1); }
    public GraphBuilder conditionalEdge(String from, String to, JsonObject requirements, double weight) {
        var options = new JsonObject(); options.add("requirements", requirements.deepCopy()); options.addProperty("weight", weight);
        return edge(from, to, options);
    }
    public GraphBuilder branchMode(String nodeId, String mode) { findNode(nodeId).addProperty("branch_mode", mode); return this; }
    public GraphBuilder progressAction(String nodeId, JsonObject action) { append(findNode(nodeId), "progress_actions", action.deepCopy()); return this; }
    private JsonObject findNode(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Node id must not be empty");
        for (var node : nodes) if (node.getAsJsonObject().get("id").getAsString().equals(id)) return node.getAsJsonObject();
        throw new IllegalArgumentException("Unknown node: " + id + "; add the node before configuring it");
    }
    private static void append(JsonObject target, String field, JsonElement value) {
        if (!target.has(field)) target.add(field, new JsonArray());
        target.getAsJsonArray(field).add(value);
    }
    public JsonObject build() { GraphCodec.parse("graph_assembly:script_validation", json); return json.deepCopy(); }
}
