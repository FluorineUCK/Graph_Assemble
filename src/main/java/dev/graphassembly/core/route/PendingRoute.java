package dev.graphassembly.core.route;

/** Selected next operation; from is present only for an explicit directed edge. */
public record PendingRoute(String node, String from) {
    public PendingRoute {
        if(node==null || node.isBlank() || from!=null && from.isBlank()) throw new IllegalArgumentException("Invalid pending route");
    }
}
