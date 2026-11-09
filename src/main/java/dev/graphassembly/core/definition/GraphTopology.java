package dev.graphassembly.core.definition;

import dev.graphassembly.core.definition.basic.Edge;

import java.util.*;
/** Ordered semantic connections and their derived outgoing adjacency. */
public final class GraphTopology {
    private final List<Edge> edges;
    private final Map<String,List<Edge>> outgoing;
    public GraphTopology(List<Edge> edges) {
        this.edges = List.copyOf(edges);
        Map<String,List<Edge>> index = new LinkedHashMap<>();
        for (Edge edge : this.edges) index.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        index.replaceAll((ignored, values) -> List.copyOf(values));
        outgoing = Collections.unmodifiableMap(index);
    }
    public List<Edge> edges() { return edges; }
    public List<Edge> outgoing(String node) { return outgoing.getOrDefault(node,List.of()); }
    @Override public boolean equals(Object other) { return other instanceof GraphTopology t && edges.equals(t.edges); }
    @Override public int hashCode() { return edges.hashCode(); }
    @Override public String toString() { return "GraphTopology[edges=" + edges + "]"; }
}
