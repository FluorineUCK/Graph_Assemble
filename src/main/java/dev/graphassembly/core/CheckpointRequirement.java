package dev.graphassembly.core;

import java.util.List;

public record CheckpointRequirement(CheckpointMode mode, List<String> nodes) {
    public CheckpointRequirement { nodes = List.copyOf(nodes); }
}
