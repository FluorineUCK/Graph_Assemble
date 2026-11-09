package dev.graphassembly.core.definition;

import dev.graphassembly.core.definition.basic.Edge;

import java.util.*;
/** Display-only metadata, never consulted by state transitions. */
public record GraphLayout(Map<String,NodeLayout> nodeLayouts, Map<Endpoint,EdgeLayout> edgeLayouts) {
    public GraphLayout {
        nodeLayouts = Collections.unmodifiableMap(new LinkedHashMap<>(nodeLayouts));
        edgeLayouts = Collections.unmodifiableMap(new LinkedHashMap<>(edgeLayouts));
    }
    public record Endpoint(String from, String to) {}
    public record Waypoint(double x, double y) {}
    public record NodeLayout(List<Double> position, List<Double> spherePosition) {
        public NodeLayout { position=List.copyOf(position); spherePosition=List.copyOf(spherePosition); }
    }
    public record EdgeLayout(List<Waypoint> waypoints) {
        public EdgeLayout { waypoints=List.copyOf(waypoints); }
    }
    public NodeLayout node(String id) { return nodeLayouts.getOrDefault(id,new NodeLayout(List.of(),List.of())); }
    public EdgeLayout edge(Edge edge) { return edgeLayouts.getOrDefault(new Endpoint(edge.from(),edge.to()),new EdgeLayout(List.of())); }
}
