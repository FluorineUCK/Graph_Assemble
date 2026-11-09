package dev.graphassembly.core.io;

import java.util.List;
import java.util.Objects;

public record OutputGroup(Mode mode, List<OutputNode> children, double weight, double chance) implements OutputNode {
    public enum Mode { ALL, WEIGHTED }
    public OutputGroup {
        Objects.requireNonNull(mode);
        children = List.copyOf(children);
        if (mode == Mode.WEIGHTED && children.isEmpty()) throw new IllegalArgumentException("Weighted output group must not be empty");
    }
}
