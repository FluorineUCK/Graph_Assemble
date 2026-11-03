package dev.graphassembly.compat.kubejs;

import com.google.gson.*;
import dev.graphassembly.core.GraphCodec;

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
    public JsonObject build() { GraphCodec.parse("graph_assembly:script_validation", json); return json.deepCopy(); }
}
