package dev.graphassembly.core.condition;

import dev.graphassembly.core.condition.checkpoint.CheckpointMatcher;
import dev.graphassembly.core.condition.mark.MarkMatcher;

/** Pure conditions: no route, mutation, device or random dependencies. */
public final class ProgressMatcher {
    private ProgressMatcher() {}
    public static boolean matches(ProgressRequirements requirements, ProgressFacts facts) {
        return MarkMatcher.matches(requirements.marks(), facts)
            && CheckpointMatcher.matches(requirements.checkpoints(), facts);
    }
}
