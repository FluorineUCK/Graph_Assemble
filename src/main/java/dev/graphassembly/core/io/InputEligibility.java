package dev.graphassembly.core.io;

import dev.graphassembly.core.condition.ProgressFacts;
import dev.graphassembly.core.condition.ProgressMatcher;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.route.RouteResolver;

import java.util.*;
/** One input snapshot's logical role evaluation; physical slots and native ingredients stay in adapters. */
public final class InputEligibility {
    private final Progress before;
    private ProgressFacts facts;

    public InputEligibility(Progress before) { this.before = before; }

    public boolean matches(WorkpieceInput input) {
        if (before == null || !input.currentNodes().isEmpty() && !input.currentNodes().contains(before.current())) return false;
        if (facts == null) facts = new ProgressFacts(before);
        return ProgressMatcher.matches(input.requirements(),facts);
    }
    /** Condition diagnostics precede capacity diagnostics, even if both reject this operation. */
    public PreflightResult.Problem primaryProblem(NodeDefinition<?> node) {
        var primary=node.inputs().workpieceInput(0);
        if(primary!=null && !matches(primary))
            return new PreflightResult.Problem(PreflightResult.BlockedReason.PRIMARY_CONDITIONS,
                "Node "+node.id()+": primary workpiece input conditions are not satisfied");
        var capacity=MarkAccounting.capacityProblem(node,before==null?null:before.marks());
        return capacity==null?null:new PreflightResult.Problem(PreflightResult.BlockedReason.MARK_CAPACITY,capacity);
    }
    /** Physical stack availability and native ingredient predicates remain adapter responsibilities. */
    public static boolean canParticipate(GraphDefinition<?> graph,NodeDefinition<?> node,int itemInput,Progress before) {
        var role=node.inputs().workpieceInput(itemInput);
        if(before==null) return role==null && (itemInput!=0 || node.roles().start());
        if(itemInput>0 && role==null) return false;
        if(!RouteResolver.canEnter(graph,before,node.id())) return false;
        var eligibility = new InputEligibility(before);
        return itemInput==0 ? eligibility.primaryProblem(node)==null : eligibility.matches(role);
    }
    public static Map<String, List<WorkpieceInput>> auxiliaries(GraphDefinition<?> graph, Progress state,
                                                               PreflightResult preflight) {
        if (state == null || preflight.incompatibility() != null) return Map.of();
        var candidates = new LinkedHashSet<>(preflight.allowed());
        candidates.addAll(preflight.blocked().keySet());
        var result = new LinkedHashMap<String, List<WorkpieceInput>>();
        var eligibility = new InputEligibility(state);
        for (var id : candidates) {
            var roles = graph.nodes().get(id).inputs().workpieceInputs().stream()
                .filter(role -> role.itemInput() > 0 && eligibility.matches(role)).toList();
            if (!roles.isEmpty()) result.put(id, roles);
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
