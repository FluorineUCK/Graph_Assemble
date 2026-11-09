package dev.graphassembly.core.route;

/** A redirect selects the next operation without editing completed history. */
public sealed interface RouteRedirect {
    record GoToNode(String node) implements RouteRedirect {}
    record EnterEdge(String from,String to) implements RouteRedirect {}
    default PendingRoute pendingRoute() {
        return switch(this) {
            case GoToNode action -> new PendingRoute(action.node(),null);
            case EnterEdge action -> new PendingRoute(action.to(),action.from());
        };
    }
}
