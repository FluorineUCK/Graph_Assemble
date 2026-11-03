package dev.graphassembly.client.view;

import dev.graphassembly.core.BranchMode;

/** Presentation only; graph branching and persisted random locks remain engine-owned. */
public final class NextStepPresentation {
    private NextStepPresentation() {}
    public static String workpieceKey(int availableCount, boolean locked) {
        if (availableCount == 0) return "graph_assembly.next.none";
        if (locked) return "graph_assembly.next.locked";
        return availableCount == 1 ? "graph_assembly.next.single" : "graph_assembly.next.choice";
    }
    public static String branchKey(BranchMode mode, int successorCount) {
        if (successorCount == 0) return "graph_assembly.branch.none";
        if (mode == BranchMode.RANDOM) return "graph_assembly.branch.random";
        return successorCount == 1 ? "graph_assembly.branch.single" : "graph_assembly.branch.choice";
    }
    public static String edgeKey(BranchMode mode, int successorCount) {
        if (mode == BranchMode.RANDOM) return "graph_assembly.edge.random";
        return successorCount == 1 ? "graph_assembly.edge.single" : "graph_assembly.edge.choice";
    }
}
