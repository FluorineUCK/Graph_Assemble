package dev.graphassembly.core;

import java.util.List;

/** Directed semantic connection with optional, display-only plane coordinates. */
public record Edge(String from, String to, double weight, List<Waypoint> waypoints) {
    public Edge {
        waypoints = List.copyOf(waypoints);
    }

    public Edge(String from, String to, double weight) {
        this(from, to, weight, List.of());
    }

    public record Waypoint(double x, double y) {}
}
