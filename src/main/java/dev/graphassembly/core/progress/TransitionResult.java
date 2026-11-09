package dev.graphassembly.core.progress;

import dev.graphassembly.core.io.OutputItem;

import java.util.*;
public sealed interface TransitionResult {
    record Continued(Progress progress,String carrierItem) implements TransitionResult {
        public Continued { Objects.requireNonNull(progress); Objects.requireNonNull(carrierItem); }
    }
    record Settled(List<OutputItem> outputs) implements TransitionResult {
        public Settled { outputs=List.copyOf(outputs); }
    }
}
