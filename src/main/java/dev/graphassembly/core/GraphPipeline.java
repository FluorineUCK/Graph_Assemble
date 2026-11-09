package dev.graphassembly.core;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.io.InputEligibility;
import dev.graphassembly.core.io.PreflightResult;
import dev.graphassembly.core.io.OutputTrees;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.progress.ProgressTransition;
import dev.graphassembly.core.progress.TransitionPlan;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.RouteResolver;
import dev.graphassembly.core.settlement.SettlementPlanner;

import java.util.*;
import java.util.function.DoubleSupplier;
/** Fixed operation order: preflight, apply, settle or route; resolve randomness separately. */
public final class GraphPipeline {
    private GraphPipeline() {}
    public static List<String> allowed(GraphDefinition<?> graph, Progress state) {
        return preflight(graph, state).allowed();
    }

    public static PreflightResult preflight(GraphDefinition<?> graph, Progress state) {
        String reason = RouteResolver.incompatibility(graph, state);
        if (reason != null) return new PreflightResult(reason, List.of(), Map.of());
        Map<String, PreflightResult.Problem> blocked = new LinkedHashMap<>();
        List<String> allowed = new ArrayList<>();
        var eligibility = new InputEligibility(state);
        for (String id : RouteResolver.successors(graph, state)) {
            NodeDefinition<?> node = graph.nodes().get(id);
            var problem = eligibility.primaryProblem(node);
            if (problem != null) {
                blocked.put(id, problem);
            }
            else allowed.add(id);
        }
        return new PreflightResult(reason, allowed, blocked);
    }

    public static TransitionPlan plan(GraphDefinition<?> graph,Progress before,String nodeId,String currentItem) {
        var preflight=preflight(graph,before);
        if(preflight.incompatibility()!=null) throw new IllegalArgumentException(preflight.incompatibility());
        var blocked=preflight.blocked().get(nodeId);
        if(blocked!=null) throw new IllegalArgumentException(blocked.message());
        if(!preflight.allowed().contains(nodeId)) throw new IllegalArgumentException("Node is not allowed: "+nodeId);
        var node=graph.nodes().get(nodeId);
        var applied=ProgressTransition.apply(graph.id(),graph.defaults(),node,before,currentItem);
        return switch(SettlementPlanner.plan(graph.outputRules(),node,applied.progress())) {
            case SettlementPlanner.Decision.Settle settlement -> new TransitionPlan.SettlementPlan(settlement.outputPool());
            case SettlementPlanner.Decision.Continue ignored -> {
                var route = RouteResolver.plan(graph,node,applied.progress());
                yield new TransitionPlan.ContinuationPlan(applied.progress().withRouting(route.state()),
                    applied.carrierItem(),route.randomBranches());
            }
        };
    }
    public static TransitionResult resolve(TransitionPlan plan,DoubleSupplier random) {
        return switch(plan) {
            case TransitionPlan.SettlementPlan settlement -> new TransitionResult.Settled(OutputTrees.roll(settlement.outputPool(),random));
            case TransitionPlan.ContinuationPlan continuation -> {
                var progress=continuation.progress();
                var route=RouteResolver.resolve(progress.routing(),continuation.randomBranches(),random);
                if(route!=progress.routing()) progress=progress.withRouting(route);
                yield new TransitionResult.Continued(progress,continuation.carrierItem());
            }
        };
    }
}
