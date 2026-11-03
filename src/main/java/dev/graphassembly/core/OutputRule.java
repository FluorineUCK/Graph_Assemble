package dev.graphassembly.core;

import java.util.List;

public record OutputRule(String start, String finish, CompletionRequirements requirements,
                         List<OutputNode> entries) {
    public OutputRule(String start, String finish, CompletionRequirements requirements,
                      java.util.Collection<? extends OutputNode> entries) {
        this(start, finish, requirements, new java.util.ArrayList<>(entries));
    }
    public OutputRule {
        java.util.Objects.requireNonNull(requirements, "requirements");
        entries = List.copyOf(entries);
    }
    /** Leaf inventory for indexing/presentation only, never for settlement selection. */
    public List<WeightedOutput> results() { return OutputTrees.leaves(entries); }
}
