package dev.graphassembly.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.ToDoubleFunction;

/** Pure state transitions; call complete only after the native operation succeeds. */
public final class GraphEngine {
    private GraphEngine() {}
    private static final ThreadLocal<IntConsumer> EDGE_OBSERVER = new ThreadLocal<>();
    public interface Observation extends AutoCloseable { @Override void close(); }
    /** Opt-in, current-thread count of edges actually inspected by engine validation. */
    public static Observation observeEdges(IntConsumer observer) {
        IntConsumer previous = EDGE_OBSERVER.get();
        EDGE_OBSERVER.set(observer);
        return () -> { if (previous == null) EDGE_OBSERVER.remove(); else EDGE_OBSERVER.set(previous); };
    }

    public static List<String> allowed(GraphDefinition graph, Progress state) {
        return preflight(graph, state).allowed();
    }

    /** Structural traversal only: auxiliaries do not receive rewards or use the primary role's conditions. */
    public static boolean canEnter(GraphDefinition graph, Progress state, String nodeId) {
        return incompatibility(graph, state) == null && successors(graph, state).contains(nodeId);
    }

    /** Deterministic preflight; callers must reject these operations before consuming materials. */
    public static Map<String, String> blockedSteps(GraphDefinition graph, Progress state) {
        return preflight(graph, state).blocked();
    }

    /** One-operation validation result; never retain across state or recipe changes. */
    public enum BlockedReason { PRIMARY_CONDITIONS, REWARD_CAPACITY }
    public record Preflight(String incompatibility, List<String> allowed, Map<String, String> blocked,
                            Map<String, BlockedReason> blockedReasons) {}

    public static Preflight preflight(GraphDefinition graph, Progress state) {
        String reason = incompatibility(graph, state);
        if (reason != null) return new Preflight(reason, List.of(), Map.of(), Map.of());
        Map<String, String> blocked = new LinkedHashMap<>();
        Map<String, BlockedReason> blockedReasons = new LinkedHashMap<>();
        List<String> allowed = new ArrayList<>();
        for (String id : successors(graph, state)) {
            Node node = graph.nodes().get(id);
            WorkpieceInput primary = node.workpieceInput(0);
            boolean conditionsFailed = primary != null && !primary.matches(state);
            String problem = conditionsFailed
                ? "Node " + id + ": primary workpiece input conditions are not satisfied"
                : capacityProblem(node, state);
            if (problem != null) {
                blocked.put(id, problem);
                blockedReasons.put(id, conditionsFailed ? BlockedReason.PRIMARY_CONDITIONS : BlockedReason.REWARD_CAPACITY);
            }
            else allowed.add(id);
        }
        return new Preflight(reason, List.copyOf(allowed), java.util.Collections.unmodifiableMap(blocked),
            java.util.Collections.unmodifiableMap(blockedReasons));
    }

    private static List<String> successors(GraphDefinition graph, Progress state) {
        if (state == null) return graph.nodes().values().stream().filter(Node::start).map(Node::id).toList();
        if (state.pendingRoute() != null) return List.of(state.pendingRoute().node());
        if (state.lockedNext() != null) return List.of(state.lockedNext());
        var successors = new LinkedHashSet<String>();
        IntConsumer observer = EDGE_OBSERVER.get();
        for (var edge : graph.outgoing(state.current())) {
            if (observer != null) observer.accept(1);
            successors.add(edge.to());
        }
        return List.copyOf(successors);
    }

    private static String capacityProblem(Node node, Progress state) {
        if (state == null || node.rewards().isEmpty()) return null;
        int grants = state.rewardCounts().getOrDefault(node.id(), 0);
        if (node.rewardLimit() != null && grants >= node.rewardLimit()) return null;
        for (var reward : node.rewards().entrySet()) {
            int previous = state.marks().getOrDefault(reward.getKey(), 0);
            if ((long) previous + reward.getValue() > Integer.MAX_VALUE)
                return "Node " + node.id() + ": mark '" + reward.getKey() + "' would exceed " + Integer.MAX_VALUE
                    + " (" + previous + " + " + reward.getValue() + ")";
        }
        if (grants == Integer.MAX_VALUE)
            return "Node " + node.id() + ": reward counter would exceed " + Integer.MAX_VALUE;
        return null;
    }

    public static String incompatibility(GraphDefinition graph, Progress state) {
        if (graph == null) return "Graph is missing";
        if (state == null) return null;
        if (!graph.id().equals(state.graphId())) return "Graph ID changed: " + state.graphId();
        Node entry = graph.nodes().get(state.entry());
        if (entry == null) return "Entry node is missing: " + state.entry();
        if (!entry.start()) return "Entry node is no longer a start: " + state.entry();
        Node current = graph.nodes().get(state.current());
        if (current == null) return "Current node is missing: " + state.current();
        var route = state.pendingRoute();
        if (route != null) {
            if (state.lockedNext() != null) return "Both random and explicit routes are saved";
            if (!route.equals(current.route())) return "Pending route configuration changed: " + state.current();
            if (!graph.nodes().containsKey(route.node())) return "Pending target is missing: " + route.node();
            if (route.from() != null && graph.outgoing(route.from()).stream().noneMatch(edge -> edge.to().equals(route.node())))
                return "Pending edge is missing: " + route.from() + " -> " + route.node();
            return null;
        }
        List<Edge> outgoing = graph.outgoing(state.current());
        if (state.lockedNext() != null) {
            if (!graph.nodes().containsKey(state.lockedNext())) return "Locked target is missing: " + state.lockedNext();
            if (current.branchMode() != BranchMode.RANDOM) return "Locked node is no longer random: " + state.current();
            boolean found = false;
            IntConsumer observer = EDGE_OBSERVER.get();
            for (var edge : outgoing) {
                if (observer != null) observer.accept(1);
                if (edge.to().equals(state.lockedNext())) { found = true; break; }
            }
            if (!found)
                return "Locked target is no longer a successor: " + state.lockedNext();
        } else if (current.branchMode() == BranchMode.RANDOM && !outgoing.isEmpty()) {
            return "Random node has no persisted target: " + state.current();
        }
        return null;
    }

    public static Completion complete(GraphDefinition graph, Progress state, String nodeId,
                                      String currentItem, DoubleSupplier random) {
        return plan(graph, state, nodeId, currentItem).roll(random);
    }

    public static CompletionPlan plan(GraphDefinition graph, Progress state, String nodeId, String currentItem) {
        Preflight preflight = preflight(graph, state);
        if (preflight.incompatibility() != null) throw new IllegalArgumentException(preflight.incompatibility());
        String blocked = preflight.blocked().get(nodeId);
        if (blocked != null) throw new IllegalArgumentException(blocked);
        if (!preflight.allowed().contains(nodeId)) throw new IllegalArgumentException("Node is not allowed: " + nodeId);
        Node node = graph.nodes().get(nodeId);
        String entry = state == null ? nodeId : state.entry();
        Map<String, Integer> marks = new LinkedHashMap<>(state == null ? Map.of() : state.marks());
        Map<String, Integer> rewardCounts = new LinkedHashMap<>(state == null ? Map.of() : state.rewardCounts());
        int grants = rewardCounts.getOrDefault(nodeId, 0);
        if (!node.rewards().isEmpty() && (node.rewardLimit() == null || grants < node.rewardLimit())) {
            node.rewards().forEach((mark, amount) -> marks.merge(mark, amount, Math::addExact));
            rewardCounts.put(nodeId, Math.incrementExact(grants));
        }
        List<String> checkpoints = state == null ? List.of() : state.checkpoints();
        if (node.start() || node.finish() || node.checkpoint()) {
            checkpoints = new ArrayList<>(checkpoints);
            checkpoints.add(nodeId);
        }
        if (!node.progressActions().isEmpty()) {
            checkpoints = new ArrayList<>(checkpoints);
            for (var action : node.progressActions()) action.apply(marks, checkpoints);
        }
        String item = node.transitionalItem() != null ? node.transitionalItem()
                : state == null ? graph.transitionalItem() : currentItem;
        List<String> history = new ArrayList<>(state == null ? List.of() : state.history());
        history.add(nodeId);
        if (history.size() > 3) history = history.subList(history.size() - 3, history.size());
        if (node.finish()) {
            List<OutputNode> pool = new ArrayList<>();
            List<OutputRule> rules = new ArrayList<>(graph.outputs());
            rules.addAll(node.outputs());
            var matchContext = new CompletionRequirements.MatchContext(marks, checkpoints);
            for (OutputRule rule : rules) {
                if ((rule.start() == null || rule.start().equals(entry))
                        && (rule.finish() == null || rule.finish().equals(nodeId))
                        && rule.requirements().matches(matchContext)) {
                    pool.addAll(rule.entries());
                }
            }
            if (!pool.isEmpty()) return new CompletionPlan(null, null, pool, List.of(), true);
            if (node.finishMode() == FinishMode.STRICT) {
                return new CompletionPlan(null, null, node.scrapEntries(), List.of(), true);
            }
        }
        List<Edge> outgoing = graph.outgoing(nodeId);
        return new CompletionPlan(new Progress(graph.id(), entry, nodeId, marks, checkpoints, rewardCounts,
                null, history, node.route()), item, List.of(), node.route() == null && node.branchMode() == BranchMode.RANDOM ? outgoing : List.of(), false);
    }

    static <T> T choose(List<T> pool, ToDoubleFunction<T> weight, DoubleSupplier random) {
        double scale = pool.stream().mapToDouble(weight).max().orElseThrow();
        double total = pool.stream().mapToDouble(value -> weight.applyAsDouble(value) / scale).sum();
        double draw = random.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0 || draw >= 1) throw new IllegalArgumentException("Random value must be in [0, 1)");
        // A positive first interval contains zero even when scaling underflows its width.
        if (draw == 0) return pool.getFirst();
        double cursor = draw * total;
        for (T value : pool) {
            cursor -= weight.applyAsDouble(value) / scale;
            if (cursor < 0) return value;
        }
        return pool.getLast();
    }
}
