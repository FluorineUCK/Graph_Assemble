package dev.graphassembly.client.view;

import dev.graphassembly.client.view.GraphProjection.Projected;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import dev.graphassembly.client.view.NodePresentation.Surface;
import dev.graphassembly.client.view.NodePresentation.Transform;
import dev.graphassembly.core.Edge;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The task-base implementation is frozen as LegacyGraphEdges in test sources only. */
class GraphEdgesDifferentialTest {
    private static final Bounds VIEWPORT = new Bounds(0, 20, 320, 189);

    @Test void largeFiniteCancellationKeepsLegacyCanvasHitInEveryPickSeam() {
        var edge = new Edge("a", "b", 1);
        var points = List.of(new Projected(1e20, 100, 1), new Projected(100, 100, 1));
        var previous = List.of(new LegacyGraphEdges.Path(edge, points, true));
        var current = List.of(new GraphEdges.Path(edge, points, true));
        var prepared = GraphEdges.prepare(current);
        assertEquals(edge, LegacyGraphEdges.hit(previous, 90, 100));
        assertEquals(edge, GraphEdges.hitPrepared(prepared, 90, 100));
        assertEquals(edge, GraphEdges.hit(current, 90, 100));
        assertEquals(edge, NodePresentation.pickPrepared(List.of(), prepared, VIEWPORT, 90, 100).edge());
    }

    @Test void defaultSixNodeReciprocalLoopsDuplicatesExpansionAndPanningMatchTaskBase() {
        var edges = List.of(new Edge("n0", "n1", 1), new Edge("n1", "n0", 3),
            new Edge("n1", "n2", 1), new Edge("n2", "n3", 1), new Edge("n3", "n3", 1),
            new Edge("n3", "n4", 1), new Edge("n4", "n5", 1), new Edge("n0", "n1", 7));
        var topology = GraphEdges.Topology.of(edges);
        for (double zoom : new double[]{1, 1.25, 1.8})
            for (double panX : new double[]{-70, 0, 75})
                for (double panY : new double[]{-35, 0, 40}) {
                    Map<String, Projected> nodes = new LinkedHashMap<>();
                    List<Surface> surfaces = new ArrayList<>();
                    for (int i = 0; i < 6; i++) {
                        double angle = i * Math.PI / 3 - Math.PI / 2;
                        var p = new Projected(160 + panX + 100 * Math.cos(angle) * zoom,
                            107 + panY + 65 * Math.sin(angle) * zoom, 1);
                        var id = "n" + i;
                        nodes.put(id, p);
                        boolean expanded = zoom > 1 && (i % 2 == 0 || zoom > 1.5);
                        surfaces.add(surface(id, p, zoom, expanded));
                    }
                    compare(edges, topology, nodes, false, surfaces, "zoom=" + zoom + " pan=" + panX + "," + panY);
                }
    }

    @Test void sphereBackfaceCoincidenceAndViewportEdgeMatchTaskBase() {
        var edges = List.of(new Edge("a", "b", 1), new Edge("b", "a", 2),
            new Edge("a", "a", 1), new Edge("b", "c", 1));
        var topology = GraphEdges.Topology.of(edges);
        for (var nodes : List.of(
            Map.of("a", new Projected(305, 65, 1), "b", new Projected(305, 145, 1), "c", new Projected(160, 100, -1)),
            Map.of("a", new Projected(160, 107, 1), "b", new Projected(160, 107, 1), "c", new Projected(180, 107, -1)),
            Map.of("a", new Projected(160, 185, 1), "b", new Projected(160, 185, 1), "c", new Projected(160, 107, -1)))) {
            var surfaces = List.of(surface("a", nodes.get("a"), 1.25, true),
                surface("b", nodes.get("b"), 1.5, true), surface("c", nodes.get("c"), 1, false));
            compare(edges, topology, nodes, true, surfaces, nodes.toString());
        }
    }

    @Test void exactPickBoundaryTieAndNonfiniteFallbackMatchTaskBase() {
        var a = new Edge("z", "a", 1); var b = new Edge("a", "z", 1);
        var paths = List.of(new GraphEdges.Path(a, List.of(new Projected(10, 40, 1), new Projected(90, 40, 1)), true),
            new GraphEdges.Path(b, List.of(new Projected(10, 40, 1), new Projected(90, 40, 1)), true),
            new GraphEdges.Path(new Edge("n", "n", 1), List.of(new Projected(Double.NaN, 40, 1), new Projected(20, 40, 1)), true));
        var old = List.of(new LegacyGraphEdges.Path(a, paths.get(0).points(), true),
            new LegacyGraphEdges.Path(b, paths.get(1).points(), true),
            new LegacyGraphEdges.Path(paths.get(2).edge(), paths.get(2).points(), true));
        var prepared = GraphEdges.prepare(paths);
        assertEquals(b, GraphEdges.hitPrepared(prepared, 50, 40), "Equal-distance overlap sorts by from ID");
        for (double x : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, 6, 10, 50, 90, 94, Double.POSITIVE_INFINITY})
            for (double y : new double[]{36, 39.9999999, 40, 44, 44.0000001, Double.NaN})
                assertEquals(LegacyGraphEdges.hit(old, x, y), GraphEdges.hitPrepared(prepared, x, y), x + "," + y);
        for (double x : new double[]{0, 320}) for (double y : new double[]{20, 189}) {
            assertEquals(NodePresentation.pick(List.of(), paths, VIEWPORT, x, y),
                NodePresentation.pickPrepared(List.of(), prepared, VIEWPORT, x, y));
        }
    }

    private static Surface surface(String id, Projected p, double scale, boolean expanded) {
        return expanded ? new Surface(id, p.depth(), p.hittable(), true,
            new Transform(p.x() - 49 * scale / 2, p.y() - 84 * scale / 2, scale), 49, 84)
            : new Surface(id, p.depth(), p.hittable(), false, new Transform(p.x() - 5, p.y() - 5, 1), 10, 10);
    }

    private static void compare(List<Edge> edges, GraphEdges.Topology topology, Map<String, Projected> nodes,
                                boolean sphere, List<Surface> surfaces, String caseName) {
        var previous = LegacyGraphEdges.layout(edges, nodes, sphere, surfaces, VIEWPORT);
        var prepared = GraphEdges.layoutPrepared(topology, nodes, sphere, surfaces, VIEWPORT);
        var current = prepared.paths();
        assertEquals(previous.size(), current.size(), caseName);
        for (int i = 0; i < previous.size(); i++) {
            assertEquals(previous.get(i).edge(), current.get(i).edge(), caseName + " edge " + i);
            assertEquals(previous.get(i).points(), current.get(i).points(), caseName + " points " + i);
            assertEquals(previous.get(i).hittable(), current.get(i).hittable(), caseName + " hittable " + i);
        }
        for (int y = 20; y <= 189; y += 9) for (int x = 0; x <= 320; x += 9) {
            assertPick(previous, current, prepared, surfaces, x, y, caseName);
        }
        for (var path : current) {
            var arrow = GraphEdges.arrow(path);
            for (var p : List.of(arrow.tip(), arrow.left(), arrow.right()))
                for (double dx : new double[]{-4, 0, 4}) for (double dy : new double[]{-4, 0, 4})
                    assertPick(previous, current, prepared, surfaces, p.x() + dx, p.y() + dy, caseName);
            for (var p : path.points()) for (double offset : new double[]{-4.0000001, -4, 0, 4, 4.0000001})
                assertPick(previous, current, prepared, surfaces, p.x(), p.y() + offset, caseName);
        }
    }

    private static void assertPick(List<LegacyGraphEdges.Path> previous, List<GraphEdges.Path> current,
                                   GraphEdges.Prepared prepared, List<Surface> surfaces, double x, double y, String caseName) {
        assertEquals(LegacyGraphEdges.hit(previous, x, y), GraphEdges.hitPrepared(prepared, x, y), caseName + " edge " + x + "," + y);
        var node = NodePresentation.hit(surfaces, VIEWPORT, x, y);
        var selected = NodePresentation.pickPrepared(surfaces, prepared, VIEWPORT, x, y);
        assertEquals(node, selected.node(), caseName + " node " + x + "," + y);
        assertEquals(node == null && VIEWPORT.contains(x, y) ? GraphEdges.hit(current, x, y) : null,
            selected.edge(), caseName + " priority " + x + "," + y);
    }
}
