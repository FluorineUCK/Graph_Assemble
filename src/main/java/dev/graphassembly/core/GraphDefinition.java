package dev.graphassembly.core;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable graph value and its derived, ordered outgoing adjacency. */
public final class GraphDefinition {
    private final String id;
    private final JsonElement ingredient;
    private final String transitionalItem;
    private final Map<String, Node> nodes;
    private final List<Edge> edges;
    private final List<OutputRule> outputs;
    private final Map<String, List<Edge>> outgoing;

    public GraphDefinition(String id, JsonElement ingredient, String transitionalItem,
                           Map<String, Node> nodes, List<Edge> edges, List<OutputRule> outputs) {
        this.id = id;
        this.ingredient = ingredient == null ? null : ingredient.deepCopy();
        this.transitionalItem = transitionalItem;
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges = List.copyOf(edges);
        this.outputs = List.copyOf(outputs);
        Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
        for (Edge edge : this.edges) adjacency.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        adjacency.replaceAll((ignored, list) -> List.copyOf(list));
        this.outgoing = Collections.unmodifiableMap(adjacency);
    }

    public String id() { return id; }
    public JsonElement ingredient() { return ingredient == null ? null : ingredient.deepCopy(); }
    public String transitionalItem() { return transitionalItem; }
    public Map<String, Node> nodes() { return nodes; }
    public List<Edge> edges() { return edges; }
    public List<OutputRule> outputs() { return outputs; }
    public List<Edge> outgoing(String nodeId) { return outgoing.getOrDefault(nodeId, List.of()); }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GraphDefinition graph)) return false;
        return Objects.equals(id, graph.id) && Objects.equals(ingredient, graph.ingredient)
            && Objects.equals(transitionalItem, graph.transitionalItem) && nodes.equals(graph.nodes)
            && edges.equals(graph.edges) && outputs.equals(graph.outputs);
    }
    @Override public int hashCode() { return Objects.hash(id, ingredient, transitionalItem, nodes, edges, outputs); }
    @Override public String toString() {
        return "GraphDefinition[id=" + id + ", ingredient=" + ingredient + ", transitionalItem=" + transitionalItem
            + ", nodes=" + nodes + ", edges=" + edges + ", outputs=" + outputs + "]";
    }
}
