package dev.graphassembly.core.route;

import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.io.WeightedChoice;
import dev.graphassembly.core.condition.ProgressFacts;
import dev.graphassembly.core.condition.ProgressMatcher;
import dev.graphassembly.core.io.InputEligibility;

import java.util.*;
import java.util.function.DoubleSupplier;
/** Structural reachability, saved-route validation, and route-only planning/resolution. */
public final class RouteResolver {
    private RouteResolver() {}

    public static List<String> successors(GraphDefinition<?> graph, Progress state) {
        if (state == null) return graph.nodes().values().stream().filter(n -> n.roles().start()).map(NodeDefinition::id).toList();
        if (state.routing().pendingRoute() != null) {
            var route=state.routing().pendingRoute();
            return route.from()==null || admitted(graph.topology().outgoing(route.from()),state).stream().anyMatch(e->e.to().equals(route.node()))
                ? List.of(route.node()) : List.of();
        }
        if (state.routing().lockedNext() != null) return admitted(graph.topology().outgoing(state.current()),state).stream()
            .anyMatch(e->e.to().equals(state.routing().lockedNext())) ? List.of(state.routing().lockedNext()) : List.of();
        // A random node with no eligible draw is stopped, not an implicit choice route.
        if (graph.nodes().get(state.current()).routing().branchMode()==BranchMode.RANDOM) return List.of();
        var successors = new LinkedHashSet<String>();
        for (var edge : admitted(graph.topology().outgoing(state.current()),state)) {
            successors.add(edge.to());
        }
        return List.copyOf(successors);
    }

    public static String incompatibility(GraphDefinition<?> graph, Progress state) {
        if (graph == null) return "Graph is missing";
        if (state == null) return null;
        if (!graph.id().equals(state.graphId())) return "Graph ID changed: " + state.graphId();
        NodeDefinition<?> entry = graph.nodes().get(state.entry());
        if (entry == null) return "Entry node is missing: " + state.entry();
        if (!entry.roles().start()) return "Entry node is no longer a start: " + state.entry();
        NodeDefinition<?> current = graph.nodes().get(state.current());
        if (current == null) return "Current node is missing: " + state.current();
        var route = state.routing().pendingRoute();
        if (route != null) {
            if (state.routing().lockedNext() != null) return "Both random and explicit routes are saved";
            if (!route.equals((current.routing().redirect() == null ? null : current.routing().redirect().pendingRoute()))) return "Pending route configuration changed: " + state.current();
            if (!graph.nodes().containsKey(route.node())) return "Pending target is missing: " + route.node();
            if (route.from() != null && graph.topology().outgoing(route.from()).stream().noneMatch(edge -> edge.to().equals(route.node())))
                return "Pending edge is missing: " + route.from() + " -> " + route.node();
            return null;
        }
        List<Edge> outgoing = graph.topology().outgoing(state.current());
        if (state.routing().lockedNext() != null) {
            if (!graph.nodes().containsKey(state.routing().lockedNext())) return "Locked target is missing: " + state.routing().lockedNext();
            if (current.routing().branchMode() != BranchMode.RANDOM) return "Locked node is no longer random: " + state.current();
            boolean found = false;
            for (var edge : outgoing) {
                if (edge.to().equals(state.routing().lockedNext())) { found = true; break; }
            }
            if (!found)
                return "Locked target is no longer a successor: " + state.routing().lockedNext();
        } else if (current.routing().branchMode() == BranchMode.RANDOM && !randomCandidates(graph,state).isEmpty()) {
            return "Random node has no persisted target: " + state.current();
        }
        return null;
    }

    public static boolean canEnter(GraphDefinition<?> graph, Progress state, String nodeId) {
        return incompatibility(graph,state)==null && successors(graph,state).contains(nodeId);
    }

    /** Route-only result; the pipeline owns progress/carrier assembly. */
    public record PlannedRoute(RouteState state, List<Edge> randomBranches) {
        public PlannedRoute { randomBranches = List.copyOf(randomBranches); }
    }

    private static List<Edge> admitted(List<Edge> edges, Progress progress) {
        var facts=new ProgressFacts(progress);
        return edges.stream().filter(edge->ProgressMatcher.matches(edge.requirements(),facts)).toList();
    }

    private static List<Edge> randomCandidates(GraphDefinition<?> graph, Progress progress) {
        var eligibility=new InputEligibility(progress);
        return admitted(graph.topology().outgoing(progress.current()),progress).stream()
            .filter(edge->eligibility.primaryProblem(graph.nodes().get(edge.to()))==null).toList();
    }

    /** Evaluate after this step's grants/cleanup, before the one persisted random draw. */
    public static PlannedRoute plan(GraphDefinition<?> graph, NodeDefinition<?> node, Progress progress) {
        var redirect=node.routing().redirect();
        var state=new RouteState(null,redirect==null?null:redirect.pendingRoute());
        var branches=redirect==null && node.routing().branchMode()==BranchMode.RANDOM
            ? randomCandidates(graph,progress) : List.<Edge>of();
        return new PlannedRoute(state,branches);
    }

    /** Empty candidates preserve the selected route; a nonempty pool consumes one weighted choice. */
    public static RouteState resolve(RouteState state, List<Edge> randomBranches, DoubleSupplier random) {
        if (randomBranches.isEmpty()) return state;
        var target=WeightedChoice.choose(randomBranches,Edge::weight,random).to();
        return new RouteState(target,state.pendingRoute());
    }
}
