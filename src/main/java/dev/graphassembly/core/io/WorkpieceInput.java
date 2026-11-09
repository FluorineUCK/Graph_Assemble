package dev.graphassembly.core.io;

import dev.graphassembly.core.condition.ProgressRequirements;

import java.util.List;
import java.util.Objects;

/** Conditions for one logical native item ingredient; an empty currentNodes list disables that filter. */
public record WorkpieceInput(int itemInput, List<String> currentNodes, ProgressRequirements requirements) {
    public WorkpieceInput {
        if (itemInput < 0) throw new IllegalArgumentException("itemInput must be nonnegative");
        currentNodes = List.copyOf(currentNodes);
        Objects.requireNonNull(requirements, "requirements");
    }

}
