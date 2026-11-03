package dev.graphassembly.core;

import java.util.List;
import java.util.Objects;

/** Conditions for one logical native item ingredient; an empty currentNodes list disables that filter. */
public record WorkpieceInput(int itemInput, List<String> currentNodes, CompletionRequirements requirements) {
    public WorkpieceInput {
        if (itemInput < 0) throw new IllegalArgumentException("itemInput must be nonnegative");
        currentNodes = List.copyOf(currentNodes);
        Objects.requireNonNull(requirements, "requirements");
    }

    public boolean matches(Progress state) {
        return state != null && (currentNodes.isEmpty() || currentNodes.contains(state.current()))
            && requirements.matches(state.marks(), state.checkpoints());
    }
}
