package dev.graphassembly.core.condition.checkpoint;

import dev.graphassembly.core.condition.ProgressFacts;
import java.util.List;

/** Pure matching of retained checkpoint sequences and counts, without consuming records. */
public final class CheckpointMatcher {
    private CheckpointMatcher() {}

    public static boolean matches(CheckpointRequirement requirement, ProgressFacts facts) {
        if (!requirement.counts().entrySet().stream().allMatch(e -> e.getValue().contains(facts.checkpointCount(e.getKey())))) return false;
        var match = requirement.match();
        if (match == null) return true;
        List<String> actual = facts.checkpoints();
        return switch (match.mode()) {
            case EXACT -> match.nodes().equals(actual);
            case UNORDERED -> actual.containsAll(match.nodes());
            case SUBSEQUENCE -> {
                int matched = 0;
                for (String node : actual) {
                    if (matched < match.nodes().size() && match.nodes().get(matched).equals(node)) matched++;
                }
                yield matched == match.nodes().size();
            }
        };
    }
}
