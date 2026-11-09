package dev.graphassembly.core.definition.basic;

import dev.graphassembly.core.io.WorkpieceInput;
import dev.graphassembly.core.io.OutputNode;
import dev.graphassembly.core.io.OutputTrees;
import dev.graphassembly.core.io.WeightedOutput;
import dev.graphassembly.core.progress.ProgressMutation;
import dev.graphassembly.core.route.BranchMode;
import dev.graphassembly.core.route.RouteRedirect;
import dev.graphassembly.core.settlement.FinishMode;
import dev.graphassembly.core.settlement.OutputRule;

import com.google.gson.JsonElement;
import java.util.*;
/** Graph semantics plus an opaque, caller-owned immutable operation description. */
public record NodeDefinition<O>(String id, NodeRoles roles, O operation, InputPolicy inputs,
                             NodeEffects effects, RoutingPolicy routing, SettlementPolicy settlement) {
    public NodeDefinition {
        Objects.requireNonNull(roles); Objects.requireNonNull(operation); Objects.requireNonNull(inputs);
        Objects.requireNonNull(effects); Objects.requireNonNull(routing); Objects.requireNonNull(settlement);
        effects = new NodeEffects(effects.carrierOverride(),effects.markAmounts(),effects.maxMarkApplications(),
            effects.recordCheckpoint() || roles.start() || roles.finish(),effects.mutations());
    }
    public record NodeRoles(boolean start, boolean finish) {}//branch? merge?
    public record InputPolicy(JsonElement ingredientOverride, List<WorkpieceInput> workpieceInputs) {
        public InputPolicy { ingredientOverride=ingredientOverride==null?null:ingredientOverride.deepCopy(); workpieceInputs=List.copyOf(workpieceInputs); }
        @Override public JsonElement ingredientOverride() { return ingredientOverride==null?null:ingredientOverride.deepCopy(); }
        public WorkpieceInput workpieceInput(int itemInput) { return workpieceInputs.stream().filter(i->i.itemInput()==itemInput).findFirst().orElse(null); }
    }
    /** Per-operation mark amounts and optional application limit remain recipe configuration, not visit counts. */
    public record NodeEffects(String carrierOverride, Map<String,Integer> markAmounts, Integer maxMarkApplications,
                              boolean recordCheckpoint, List<ProgressMutation> mutations) {
        public NodeEffects { markAmounts=Map.copyOf(markAmounts); mutations=List.copyOf(mutations); }
    }
    public record RoutingPolicy(BranchMode branchMode, RouteRedirect redirect) {}
    public record SettlementPolicy(FinishMode finishMode, List<OutputRule> outputRules, List<OutputNode> scrapEntries) {
        public SettlementPolicy { outputRules=List.copyOf(outputRules); scrapEntries=List.copyOf(scrapEntries); }
        public List<WeightedOutput> scrapLeaves() { return OutputTrees.leaves(scrapEntries); }
    }
}
