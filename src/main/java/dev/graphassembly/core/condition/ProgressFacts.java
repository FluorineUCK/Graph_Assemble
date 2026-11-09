package dev.graphassembly.core.condition;

import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.condition.checkpoint.CheckpointAccounting;

import java.util.*;
/** One immutable snapshot and a lazy, per-match-operation checkpoint count index. */
public final class ProgressFacts {
    private final Map<String,Integer> marks;
    private final List<String> checkpoints;
    private Map<String,Integer> counts;
    public ProgressFacts(Map<String,Integer> marks,List<String> checkpoints) {
        this.marks=Map.copyOf(marks); this.checkpoints=List.copyOf(checkpoints);
    }
    public ProgressFacts(Progress progress) { this(progress.marks().values(),progress.checkpoints()); }
    public Map<String,Integer> marks() { return marks; }
    public List<String> checkpoints() { return checkpoints; }
    public int checkpointCount(String node) {
        if(counts==null) counts=CheckpointAccounting.counts(checkpoints);
        return counts.getOrDefault(node,0);
    }
}
