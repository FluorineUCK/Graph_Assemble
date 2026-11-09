package dev.graphassembly.core.definition.basic;

import dev.graphassembly.core.condition.ProgressRequirements;
import java.util.Objects;

/** Directed connection with optional admission conditions; waypoints belong to GraphLayout. */
public record Edge(String from, String to, double weight, ProgressRequirements requirements) {
    public Edge { Objects.requireNonNull(requirements); }
    public Edge(String from, String to, double weight) { this(from,to,weight,ProgressRequirements.NONE); }
}
