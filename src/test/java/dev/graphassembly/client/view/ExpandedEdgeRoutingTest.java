package dev.graphassembly.client.view;

import com.google.gson.JsonParser;
import dev.graphassembly.core.*;
import dev.graphassembly.client.view.GraphProjection.Projected;
import dev.graphassembly.client.view.NodePresentation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpandedEdgeRoutingTest {
    @Test void expandedSelfLoopAvoidsNeighborPanelsNotJustItsOwn() {
        var edges = List.of(new Edge("press", "deploy", 1), new Edge("deploy", "press", 1),
            new Edge("deploy", "fill", 1), new Edge("fill", "fill", 1), new Edge("fill", "cut", 1), new Edge("cut", "mill", 1));
        for (double zoom : new double[]{1.12, 1.2544}) for (double panY : new double[]{0, -20}) {
            Map<String, Projected> nodes = new LinkedHashMap<>();
            List<Surface> surfaces = new ArrayList<>();
            String[] ids = {"press", "deploy", "fill", "cut", "mill"};
            double[] positions = {-85, -40, 5, 50, 90};
            for (int i = 0; i < ids.length; i++) {
                var p = new Projected(160 + positions[i] * zoom, 107 + panY - 27 * zoom, 1);
                nodes.put(ids[i], p);
                double width = i == 4 ? 80 : 49;
                surfaces.add(new Surface(ids[i], 1, true, true,
                    new Transform(p.x() - width * zoom / 2, p.y() - 84 * zoom / 2, zoom), width, 84));
            }
            var paths = GraphEdges.layout(edges, nodes, false, surfaces, VIEWPORT);
            for (var path : paths) assertArrowSelectable(path, paths, surfaces);
        }
    }
    private static final Bounds VIEWPORT = new Bounds(0, 20, 320, 189);
    private static final Edge FORWARD = new Edge("n1", "n2", 1), REVERSE = new Edge("n2", "n1", 3);

    @Test void bothDirectionsRemainSelectableAfterDefaultSixNodeLayoutExpands() {
        var graph = GraphCodec.parse("test:six", JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"n0","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"n1","recipe":{"type":"create:pressing"}},
                      {"id":"n2","recipe":{"type":"create:pressing"}},
                      {"id":"n3","recipe":{"type":"create:pressing"}},
                      {"id":"n4","recipe":{"type":"create:pressing"}},
                      {"id":"n5","recipe":{"type":"create:pressing"}}],"edges":[]}
            """).getAsJsonObject());
        for (double zoom : new double[]{1, 1.25}) {
            Map<String, Projected> projected = new LinkedHashMap<>();
            List<Surface> surfaces = new ArrayList<>();
            GraphProjection.layout(graph, false).forEach((id, p) -> {
                var center = new Projected(160 + p.x() * zoom, 107 + p.y() * zoom, 1);
                projected.put(id, center);
                boolean expanded = NodePresentation.expanded(false, zoom, true, true);
                surfaces.add(surface(id, center, zoom, expanded));
            });
            for (var edges : List.of(List.of(FORWARD, REVERSE), List.of(REVERSE, FORWARD))) {
                var paths = GraphEdges.layout(edges, projected, false, surfaces, VIEWPORT);
                for (var path : paths) {
                    assertSelectable(path, paths, surfaces, "zoom=" + zoom);
                    assertArrowSelectable(path, paths, surfaces);
                }
            }
        }
    }

    @Test void reciprocalArrowsRemainDistinctWhenOnlyOneSideFitsTheViewport() {
        var a = new Projected(305, 65, 1); var b = new Projected(305, 145, 1);
        var nodes = Map.of("n1", a, "n2", b);
        var surfaces = List.of(surface("n1", a, 1.25, true), surface("n2", b, 1.25, true));
        Map<Edge, List<Projected>> first = new HashMap<>();
        for (var edges : List.of(List.of(FORWARD, REVERSE), List.of(REVERSE, FORWARD))) {
            var paths = GraphEdges.layout(edges, nodes, false, surfaces, VIEWPORT);
            for (var path : paths) {
                assertArrowSelectable(path, paths, surfaces);
                var previous = first.putIfAbsent(path.edge(), path.points());
                if (previous != null) assertEquals(previous, path.points(), "Declaration order must not choose the visible direction");
            }
        }
    }

    @Test void overlappingProjectedCentersStillHaveNonzeroDirectedArrowSegments() {
        for (var center : List.of(new Projected(160, 107, 1), new Projected(160, 185, 1))) {
            var surfaces = List.of(surface("n1", center, 1.25, true), surface("n2", center, 1.5, true));
            var paths = GraphEdges.layout(List.of(FORWARD, REVERSE), Map.of("n1", center, "n2", center), false, surfaces, VIEWPORT);
            for (var path : paths) assertArrowSelectable(path, paths, surfaces);
        }
    }

    @Test void singleEdgesWithMixedSizesAndPartialExpansionExposeTheirWholeArrow() {
        for (var centers : List.of(List.of(new Projected(100, 107, 1), new Projected(160, 107, 1)),
                List.of(new Projected(90, 70, 1), new Projected(125, 100, 1)),
                List.of(new Projected(260, 60, 1), new Projected(300, 100, 1)))) {
            var a = centers.getFirst(); var b = centers.getLast();
            for (boolean secondExpanded : new boolean[]{false, true}) {
                var second = secondExpanded ? new Surface("n2", 1, true, true,
                    new Transform(b.x() - 80 * 1.5 / 2, b.y() - 84 * 1.5 / 2, 1.5), 80, 84)
                    : surface("n2", b, 1, false);
                var surfaces = List.of(surface("n1", a, 1.25, true), second);
                var paths = GraphEdges.layout(List.of(FORWARD), Map.of("n1", a, "n2", b), false, surfaces, VIEWPORT);
                assertArrowSelectable(paths.getFirst(), paths, surfaces);
            }
        }
    }

    @Test void sphereRoutingRetainsBackfaceExclusionAndNodePriority() {
        for (double yaw : new double[]{0, Math.PI}) for (double zoom : new double[]{1, 2}) {
            var a = GraphProjection.project(new GraphProjection.Point(-.2, -.3, .93), yaw, 0, 65 * zoom, 160, 107);
            var b = GraphProjection.project(new GraphProjection.Point(.2, .3, .93), yaw, 0, 65 * zoom, 160, 107);
            var surfaces = List.of(surface("n1", a, NodePresentation.effectiveScale(true, zoom, a.depth()), a.hittable()),
                surface("n2", b, NodePresentation.effectiveScale(true, zoom, b.depth()), b.hittable()));
            var paths = GraphEdges.layout(List.of(FORWARD, REVERSE), Map.of("n1", a, "n2", b), true, surfaces, VIEWPORT);
            if (a.hittable()) {
                for (var path : paths) assertArrowSelectable(path, paths, surfaces);
                var hit = NodePresentation.pick(surfaces, paths, VIEWPORT, a.x(), a.y());
                assertNotNull(hit.node()); assertNull(hit.edge(), "Never fix an obscured edge by allowing click-through");
            } else for (var path : paths) {
                assertFalse(path.hittable());
                for (var point : path.points()) {
                    var hit = NodePresentation.pick(surfaces, paths, VIEWPORT, point.x(), point.y());
                    assertNull(hit.node()); assertNull(hit.edge());
                }
            }
        }
    }

    private static Surface surface(String id, Projected p, double scale, boolean expanded) {
        // Actual native pressing layout: subcategory width 25 + two 12px paddings, height 84.
        return expanded ? new Surface(id, p.depth(), p.hittable(), true,
            new Transform(p.x() - 49 * scale / 2, p.y() - 84 * scale / 2, scale), 49, 84)
            : new Surface(id, p.depth(), p.hittable(), false, new Transform(p.x() - 5, p.y() - 5, 1), 10, 10);
    }

    private static void assertSelectable(GraphEdges.Path target, List<GraphEdges.Path> paths, List<Surface> surfaces, String context) {
        for (int i = 1; i < target.points().size(); i++) {
            var a = target.points().get(i - 1); var b = target.points().get(i);
            for (int sample = 0; sample <= 200; sample++) {
                double t = sample / 200.0;
                var hit = NodePresentation.pick(surfaces, paths, VIEWPORT, a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
                if (target.edge().equals(hit.edge())) { assertNull(hit.node()); return; }
            }
        }
        fail(context + ": no exposed pick target for " + target.edge());
    }

    private static void assertArrowSelectable(GraphEdges.Path path, List<GraphEdges.Path> paths, List<Surface> surfaces) {
        var arrow = GraphEdges.arrow(path);
        for (var end : List.of(arrow.left(), arrow.right())) for (int i = 0; i <= 10; i++) {
            double t = i / 10.0, x = arrow.tip().x() + (end.x() - arrow.tip().x()) * t,
                y = arrow.tip().y() + (end.y() - arrow.tip().y()) * t;
            assertTrue(VIEWPORT.contains(x, y), "Arrow must remain inside the canvas");
            var hit = NodePresentation.pick(surfaces, paths, VIEWPORT, x, y);
            assertNull(hit.node(), "Arrow must not be painted under a node");
            assertEquals(path.edge(), hit.edge(), "Both arrowheads must identify their own direction");
        }
    }
}
