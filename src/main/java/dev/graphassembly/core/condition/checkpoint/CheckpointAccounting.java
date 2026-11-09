package dev.graphassembly.core.condition.checkpoint;

import dev.graphassembly.core.progress.ProgressMutation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One operation's private working copy; counts describe retained records only. */
public final class CheckpointAccounting {
    private final List<String> original;
    private List<String> working;

    public CheckpointAccounting(List<String> before) {
        original = List.copyOf(before);
    }

    private List<String> mutableRecords() {
        if (working == null) working = new ArrayList<>(original);
        return working;
    }

    public void record(String node) { mutableRecords().add(node); }

    public void clear(ProgressMutation.ClearCheckpoint action) {
        mutableRecords().removeIf(action.node()::equals);
    }

    public void pop() {
        if (!(working == null ? original : working).isEmpty()) mutableRecords().removeLast();
    }

    /** An unchanged operation reuses its immutable checkpoint list, including long histories. */
    public List<String> snapshot() { return working == null ? original : List.copyOf(working); }

    public static Map<String, Integer> counts(List<String> checkpoints) {
        Map<String, Integer> counts = new HashMap<>();
        for (String checkpoint : checkpoints) counts.merge(checkpoint, 1, Integer::sum);
        return Map.copyOf(counts);
    }
}
