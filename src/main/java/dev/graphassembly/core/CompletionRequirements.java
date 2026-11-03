package dev.graphassembly.core;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Null disables a condition; an enabled empty condition retains its matching mode. */
public record CompletionRequirements(MarkRequirement marks, CheckpointRequirement checkpoints,
                                     Map<String, IntRange> markRanges, Map<String, IntRange> checkpointCounts) {
    public CompletionRequirements {
        markRanges = Map.copyOf(markRanges);
        checkpointCounts = Map.copyOf(checkpointCounts);
    }

    public CompletionRequirements(MarkRequirement marks, CheckpointRequirement checkpoints) {
        this(marks, checkpoints, Map.of(), Map.of());
    }

    public boolean matches(Map<String, Integer> actualMarks, List<String> actualCheckpoints) {
        return matches(new MatchContext(actualMarks, actualCheckpoints));
    }

    boolean matches(MatchContext context) {
        Map<String, Integer> actualMarks = context.marks;
        List<String> actualCheckpoints = context.checkpoints;
        boolean marksMatch = marks == null || (marks.mode() == MarkRequirement.Mode.EXACT
            ? marks.values().equals(actualMarks)
            : marks.values().entrySet().stream().allMatch(e -> actualMarks.getOrDefault(e.getKey(), 0) >= e.getValue()));
        if (!marksMatch) return false;
        if (!markRanges.entrySet().stream().allMatch(e -> e.getValue().contains(actualMarks.getOrDefault(e.getKey(), 0))))
            return false;
        if (!checkpointCounts.isEmpty() && !checkpointCounts.entrySet().stream()
                .allMatch(e -> e.getValue().contains(context.checkpointCount(e.getKey())))) return false;
        if (checkpoints == null) return true;
        return switch (checkpoints.mode()) {
            case EXACT -> checkpoints.nodes().equals(actualCheckpoints);
            case UNORDERED -> actualCheckpoints.containsAll(checkpoints.nodes());
            case SUBSEQUENCE -> {
                int matched = 0;
                for (String node : actualCheckpoints) {
                    if (matched < checkpoints.nodes().size() && checkpoints.nodes().get(matched).equals(node)) matched++;
                }
                yield matched == checkpoints.nodes().size();
            }
        };
    }

    /** One matching operation shares a lazy count index across all output rules. Never persisted. */
    static final class MatchContext {
        private final Map<String, Integer> marks;
        private final List<String> checkpoints;
        private Map<String, Integer> counts;

        MatchContext(Map<String, Integer> marks, List<String> checkpoints) {
            this.marks = marks;
            this.checkpoints = checkpoints;
        }

        private int checkpointCount(String node) {
            if (counts == null) {
                counts = new HashMap<>();
                for (String checkpoint : checkpoints) counts.merge(checkpoint, 1, Integer::sum);
            }
            return counts.getOrDefault(node, 0);
        }
    }
}
