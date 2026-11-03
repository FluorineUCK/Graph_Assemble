package dev.graphassembly.client.view;

import java.util.*;
import dev.graphassembly.filter.FilterConfiguration;

/** Local editing state; no reference to an item component or network. */
public final class FilterDraft {
    private String graph;
    private final LinkedHashSet<String> targets;
    private final Set<String> inverted = new HashSet<>();
    private FilterConfiguration.Mode mode = FilterConfiguration.Mode.ANY;
    public FilterDraft(String graph, Collection<String> targets) { this.graph = graph; this.targets = new LinkedHashSet<>(targets); }
    public FilterDraft(FilterConfiguration configuration) {
        this(configuration.graph(), configuration.targets());
        inverted.addAll(configuration.inverted()); mode = configuration.mode();
    }
    public String graph() { return graph; }
    public List<String> targets() { return List.copyOf(targets); }
    public Set<String> inverted() { return Set.copyOf(inverted); }
    public FilterConfiguration.Mode mode() { return mode; }
    public void setMode(FilterConfiguration.Mode mode) { this.mode = Objects.requireNonNull(mode); }
    public FilterConfiguration configuration() { return new FilterConfiguration(graph, targets(), inverted, mode); }
    public void chooseGraph(String graph) { if (!this.graph.equals(graph)) { this.graph = graph; clear(); } }
    public void add(String node) { add(node, false); }
    public void add(String node, boolean inverse) { if (targets.add(node) && inverse) inverted.add(node); }
    public void remove(String node) { targets.remove(node); inverted.remove(node); }
    public void clear() { targets.clear(); inverted.clear(); }
    public FilterConfiguration.Problem capacityProblem() {
        return FilterConfiguration.problem(graph, targets(), inverted, mode);
    }
    public boolean canConfirm(Set<String> validNodes) { return !graph.isBlank() && !targets.isEmpty() && validNodes.containsAll(targets) && capacityProblem() == null; }
}
