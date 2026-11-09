package dev.graphassembly.core.settlement;

import dev.graphassembly.core.condition.ProgressRequirements;
import dev.graphassembly.core.io.OutputNode;
import dev.graphassembly.core.io.OutputTrees;
import dev.graphassembly.core.io.WeightedOutput;

import java.util.List;

public record OutputRule(String start, String finish, ProgressRequirements requirements,
                         List<OutputNode> entries) {
    public OutputRule {
        java.util.Objects.requireNonNull(requirements, "requirements");
        entries = List.copyOf(entries);
    }
    /** Leaf inventory for indexing/presentation only, never for settlement selection. */
    public List<WeightedOutput> leaves() { return OutputTrees.leaves(entries); }
}
