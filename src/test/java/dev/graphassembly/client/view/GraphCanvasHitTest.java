package dev.graphassembly.client.view;

import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.client.view.GraphProjection.Projected;
import dev.graphassembly.client.view.NodePresentation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphCanvasHitTest {
    private static final Bounds VIEWPORT = new Bounds(0, 20, 320, 189);
    private static final Edge LOOP = new Edge("fill", "fill", 1);

    @Test void expandedSelfLoopHasAVisiblePickTargetOutsideItsNode() {
        var node = new Surface("fill", 1, true, true, new Transform(120, 54.5, 1.25), 64, 84);
        var surfaces = List.of(node);
        var paths = GraphEdges.layout(List.of(LOOP), Map.of("fill", new Projected(160, 107, 1)), false, surfaces, VIEWPORT);
        assertLoopSelectable(surfaces, paths);
        var center = NodePresentation.pick(surfaces, paths, VIEWPORT, 160, 107);
        assertEquals(node, center.node());
        assertNull(center.edge()); // Keep material/animation priority; do not fix by clicking through nodes.
    }

    @Test void expandedLoopRemainsPickableWhenPanningClipsItsTopOrZoomFillsTheCanvasHeight() {
        for (var transform : List.of(new Transform(120, 15, 1.25), new Transform(40, -19, 3),
                new Transform(-50, 15, 1.25), new Transform(260, 15, 1.25), new Transform(40, -100, 4.5))) {
            var node = new Surface("fill", 1, true, true, transform, 80, 84);
            var surfaces = List.of(node); var b = node.bounds();
            var paths = GraphEdges.layout(List.of(LOOP), Map.of("fill",
                new Projected((b.left() + b.right()) / 2, (b.top() + b.bottom()) / 2, 1)), false, surfaces, VIEWPORT);
            assertLoopSelectable(surfaces, paths);
        }
    }

    @Test void collapsedLoopsAndReciprocalLanesKeepTheirExistingGeometry() {
        var forward = new Edge("fill", "next", 1); var reverse = new Edge("next", "fill", 3);
        var edges = List.of(LOOP, forward, reverse);
        var nodes = Map.of("fill", new Projected(60, 107, 1), "next", new Projected(260, 107, 1));
        var surfaces = List.of(new Surface("fill", 1, true, false, new Transform(55, 102, 1), 10, 10),
            new Surface("next", 1, true, true, new Transform(220, 54.5, 1.25), 64, 84));
        var paths = GraphEdges.layout(edges, nodes, false, surfaces, VIEWPORT);
        assertEquals(GraphEdges.layout(edges, nodes, false), paths);
        assertEquals(LOOP, NodePresentation.pick(surfaces, paths, VIEWPORT, 60, 89).edge());
        assertEquals(forward, NodePresentation.pick(surfaces, paths, VIEWPORT, 160, 115).edge());
        assertEquals(reverse, NodePresentation.pick(surfaces, paths, VIEWPORT, 160, 99).edge());
    }

    @Test void rotatedSphereLoopUsesLocalScaleAndBackHemisphereCannotBePicked() {
        for (double yaw : new double[]{0, Math.PI}) {
            var p = GraphProjection.project(new GraphProjection.Point(0, 0, 1), yaw, 0, 65, 160, 107);
            double scale = NodePresentation.effectiveScale(true, 1, p.depth());
            var node = new Surface("fill", p.depth(), p.hittable(), p.hittable(),
                new Transform(p.x() - 64 * scale / 2, p.y() - 84 * scale / 2, scale), 64, 84);
            var surfaces = List.of(node);
            var paths = GraphEdges.layout(List.of(LOOP), Map.of("fill", p), true, surfaces, VIEWPORT);
            if (p.hittable()) assertLoopSelectable(surfaces, paths);
            else {
                assertFalse(paths.getFirst().hittable());
                for (var point : paths.getFirst().points()) {
                    var hit = NodePresentation.pick(surfaces, paths, VIEWPORT, point.x(), point.y());
                    assertNull(hit.node()); assertNull(hit.edge());
                }
            }
        }
    }

    @Test void nodeAndMaterialAreasStillOccludeEdgesAndOutsideViewportCannotBePicked() {
        var node = new Surface("fill", .5, true, true, new Transform(120, 54.5, 1.25), 64, 84);
        var paths = GraphEdges.layout(List.of(LOOP), Map.of("fill", new Projected(160, 107, .5)), true, List.of(node), VIEWPORT);
        var a = paths.getFirst().points().get(1); var b = paths.getFirst().points().get(2);
        double x = (a.x() + b.x()) / 2, y = (a.y() + b.y()) / 2;
        assertEquals(LOOP, NodePresentation.pick(List.of(node), paths, VIEWPORT, x, y).edge());
        var nearer = new Surface("nearer", .9, true, true, new Transform(x - 10, y - 10, 1.5), 64, 84);
        var covered = NodePresentation.pick(List.of(node, nearer), paths, VIEWPORT, x, y);
        assertEquals(nearer, covered.node()); assertNull(covered.edge());
        var attachment = paths.getFirst().points().getFirst();
        var attached = NodePresentation.pick(List.of(node), paths, VIEWPORT, attachment.x(), attachment.y());
        assertEquals(node, attached.node()); assertNull(attached.edge());
        var clipped = GraphEdges.layout(List.of(LOOP), Map.of("fill", new Projected(160, 37, 1)), false);
        assertEquals(LOOP, GraphEdges.hit(clipped, 160, 19));
        var outside = NodePresentation.pick(List.of(), clipped, VIEWPORT, 160, 19);
        assertNull(outside.node()); assertNull(outside.edge());
    }

    private static void assertLoopSelectable(List<Surface> surfaces, List<GraphEdges.Path> paths) {
        var points = paths.getFirst().points();
        for (int i = 1; i < points.size(); i++) {
            var a = points.get(i - 1); var b = points.get(i);
            for (int sample = 0; sample <= 20; sample++) {
                double t = sample / 20.0, x = a.x() + (b.x() - a.x()) * t, y = a.y() + (b.y() - a.y()) * t;
                var hit = NodePresentation.pick(surfaces, paths, VIEWPORT, x, y);
                if (LOOP.equals(hit.edge())) { assertNull(hit.node()); return; }
            }
        }
        fail("Self-loop has no visible selectable segment outside the animation hit rectangle");
    }
}
