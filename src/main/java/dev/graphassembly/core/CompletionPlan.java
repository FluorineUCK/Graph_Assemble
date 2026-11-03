package dev.graphassembly.core;

import java.util.List;
import java.util.function.DoubleSupplier;

/** Deterministic post-operation state and eligible result tree; planning never draws randomness. */
public record CompletionPlan(Progress state, String item, List<OutputNode> pool, List<Edge> branches, boolean settled) {
    public CompletionPlan { pool = List.copyOf(pool); branches = List.copyOf(branches); }
    public Completion roll(DoubleSupplier random) {
        if (settled) return new Completion(null, null, OutputTrees.roll(pool, random), true);
        var next = state;
        if (!branches.isEmpty()) {
            var target = GraphEngine.choose(branches, Edge::weight, random).to();
            next = new Progress(state.graphId(), state.entry(), state.current(), state.marks(), state.checkpoints(), state.rewardCounts(), target, state.history(), state.pendingRoute());
        }
        return new Completion(next, item, List.of(), false);
    }
}
