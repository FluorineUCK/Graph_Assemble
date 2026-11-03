package dev.graphassembly.compat.jei;

import dev.graphassembly.core.Edge;
import dev.graphassembly.core.GraphDefinition;
import dev.graphassembly.client.view.GraphGuiLayout;
import dev.graphassembly.client.view.GraphProjection;

/** Session-only view state. No recipe, world, native layout or active input gesture is retained. */
final class GraphViewState {
    boolean sphere;
    double yaw, pitch, zoom = 1, panX, panY;
    String selected;
    String selectedFinish;
    Edge selectedEdge;

    GraphViewState(boolean sphere) { this.sphere = sphere; }

    GraphViewState(boolean sphere, GraphDefinition graph) {
        this(sphere);
        resetCamera(graph);
    }

    /** Restore this mode's initial camera without changing selection or finish scope. */
    void resetCamera(GraphDefinition graph) {
        yaw = pitch = panX = panY = 0;
        zoom = 1;
        // Reuse the authored-route overview on explicit reset. Cached view state
        // still prevents resize/reopen from moving the user's camera.
        if (sphere || graph.edges().stream().allMatch(e -> e.waypoints().isEmpty())) return;
        var xs = new java.util.DoubleSummaryStatistics();
        var ys = new java.util.DoubleSummaryStatistics();
        GraphProjection.layout(graph, false).values().forEach(p -> { xs.accept(p.x()); ys.accept(p.y()); });
        graph.edges().forEach(e -> e.waypoints().forEach(p -> { xs.accept(p.x()); ys.accept(p.y()); }));
        double width = GraphGuiLayout.CANVAS.right() - GraphGuiLayout.CANVAS.left() - 16;
        double height = GraphGuiLayout.CANVAS.bottom() - GraphGuiLayout.CANVAS.top() - 16;
        if (xs.getMin() >= -width / 2 && xs.getMax() <= width / 2
            && ys.getMin() >= -height / 2 && ys.getMax() <= height / 2) return;
        zoom = Math.max(.25, Math.min(1, Math.min(width / Math.max(1, xs.getMax() - xs.getMin()),
            height / Math.max(1, ys.getMax() - ys.getMin()))));
        panX = -(xs.getMin() + xs.getMax()) / 2 * zoom;
        panY = -(ys.getMin() + ys.getMax()) / 2 * zoom;
    }

    /** Ordinary node/edge details take priority; empty canvas keeps the active finish scope. */
    String detailsNode() {
        return selected != null ? selected : selectedEdge == null ? selectedFinish : null;
    }

    boolean selectFinish(dev.graphassembly.core.GraphDefinition graph, String id) {
        var node = graph.nodes().get(id);
        if (node == null || !node.finish()) return false;
        selectedFinish = selected = id;
        selectedEdge = null;
        return true;
    }

    void selectNode(dev.graphassembly.core.GraphDefinition graph, String id) {
        selected = id;
        if (id != null && graph.nodes().get(id).finish()) selectFinish(graph, id);
    }

    void overview() {
        if (selectedFinish != null && selectedFinish.equals(selected)) selected = null;
        selectedFinish = null;
    }

    /** Explicit selection, not a toggle: reselecting the active mode must not move the view. */
    boolean selectMode(boolean sphere) {
        if (this.sphere == sphere) return false;
        this.sphere = sphere;
        return true;
    }
}
