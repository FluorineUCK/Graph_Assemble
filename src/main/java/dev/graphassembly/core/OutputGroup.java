package dev.graphassembly.core;

import java.util.List;
import java.util.Objects;

public record OutputGroup(Mode mode, List<OutputNode> results, double weight, double chance) implements OutputNode {
    public enum Mode { ALL, WEIGHTED }
    public OutputGroup {
        Objects.requireNonNull(mode);
        results = List.copyOf(results);
        if (mode == Mode.WEIGHTED && results.isEmpty()) throw new IllegalArgumentException("Weighted output group must not be empty");
    }
}
