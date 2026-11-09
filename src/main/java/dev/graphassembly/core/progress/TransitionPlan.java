package dev.graphassembly.core.progress;

import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.io.OutputNode;

import java.util.*;
/** Deterministic plan. Randomness is consumed only by GraphPipeline.resolve. */
public sealed interface TransitionPlan {
    record ContinuationPlan(Progress progress,String carrierItem,List<Edge> randomBranches) implements TransitionPlan {
        public ContinuationPlan { Objects.requireNonNull(progress); Objects.requireNonNull(carrierItem); randomBranches=List.copyOf(randomBranches);
            if(progress.routing().lockedNext()!=null && progress.routing().pendingRoute()!=null
                || !randomBranches.isEmpty() && (progress.routing().pendingRoute()!=null || progress.routing().lockedNext()!=null))
                throw new IllegalArgumentException("Continuation routes must be mutually exclusive"); }
    }
    record SettlementPlan(List<OutputNode> outputPool) implements TransitionPlan {
        public SettlementPlan { outputPool=List.copyOf(outputPool); }
    }
}
