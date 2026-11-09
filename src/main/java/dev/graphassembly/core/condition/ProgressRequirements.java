package dev.graphassembly.core.condition;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement;
import dev.graphassembly.core.condition.mark.MarkRequirement;

import java.util.Objects;

/** Each domain owns its optional pattern and range constraints; both domains are ANDed. */
public record ProgressRequirements(MarkRequirement marks, CheckpointRequirement checkpoints) {
    public static final ProgressRequirements NONE = new ProgressRequirements(MarkRequirement.NONE, CheckpointRequirement.NONE);
    public ProgressRequirements {
        Objects.requireNonNull(marks);
        Objects.requireNonNull(checkpoints);
    }
}
