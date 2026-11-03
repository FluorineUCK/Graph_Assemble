package dev.graphassembly.client.view;

import dev.graphassembly.core.Edge;
import dev.graphassembly.client.view.GraphProjection.Projected;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class GraphEdgesTest {
    @Test void largeSparsePassRejectsDistantPathsBeforeExactDistances() {
        var paths = new ArrayList<GraphEdges.Path>();
        for (int i = 0; i < 512; i++) paths.add(new GraphEdges.Path(new Edge("n" + i, "m" + i, 1),
            List.of(new Projected(1000 + i * 8, 1000, 1), new Projected(1004 + i * 8, 1000, 1)), true));
        paths.add(new GraphEdges.Path(new Edge("near", "target", 1),
            List.of(new Projected(0, 0, 1), new Projected(10, 0, 1)), true));
        var diagnostics = new GraphEdges.Diagnostics();
        var prepared = GraphEdges.prepare(paths, diagnostics);
        assertEquals(new Edge("near", "target", 1), GraphEdges.hitPrepared(prepared, 5, 0));
        assertEquals(513, diagnostics.boundsEvaluations());
        assertEquals(1, diagnostics.exactDistanceEvaluations());
        assertEquals(513, diagnostics.spatialPreparations());
    }
    @Test void preparedHitsPruneDistantPathsAndNeverFollowAnOldProjection() {
        var edge = new Edge("a", "b", 1);
        var first = GraphEdges.layout(List.of(edge), Map.of("a", new Projected(10, 20, 1), "b", new Projected(40, 20, 1)), false);
        var diagnostics = new GraphEdges.Diagnostics();
        var prepared = GraphEdges.prepare(first, diagnostics);
        assertNull(GraphEdges.hitPrepared(prepared, 100, 100));
        assertEquals(0, diagnostics.exactDistanceEvaluations());
        assertEquals(edge, GraphEdges.hitPrepared(prepared, 25, 20));
        var moved = GraphEdges.layout(List.of(edge), Map.of("a", new Projected(10, 50, 1), "b", new Projected(40, 50, 1)), false);
        prepared = GraphEdges.prepare(moved);
        assertNull(GraphEdges.hitPrepared(prepared, 25, 20));
        assertEquals(edge, GraphEdges.hitPrepared(prepared, 25, 50));
    }

    @Test void expandedRoutingUsesOneTopologyAndCandidateReplacementWithoutWholeTrialCopies() {
        var diagnostics = new GraphEdges.Diagnostics();
        var edges = List.of(new Edge("a", "b", 1), new Edge("b", "a", 1), new Edge("a", "a", 1));
        var topology = GraphEdges.Topology.of(edges, diagnostics);
        var nodes = Map.of("a", new Projected(80, 90, 1), "b", new Projected(220, 90, 1));
        var surfaces = List.of(new NodePresentation.Surface("a", 1, true, true,
            new NodePresentation.Transform(55, 55, 1), 50, 70),
            new NodePresentation.Surface("obstacle", 2, true, false,
                new NodePresentation.Transform(140, 80, 1), 40, 30));
        var viewport = new NodePresentation.Bounds(0, 0, 320, 189);
        var prepared = GraphEdges.layoutPrepared(topology, nodes, false, surfaces, viewport, diagnostics);
        assertEquals(edges, prepared.paths().stream().map(GraphEdges.Path::edge).toList());
        assertEquals(1, diagnostics.topologyPreparations());
        assertTrue(diagnostics.trialReplacements() > 0);
        assertEquals(edges.size() + diagnostics.trialReplacements(), diagnostics.spatialPreparations());
    }
    @Test void preparedTopologyCanBeReusedAcrossChangedProjections() {
        var edges = List.of(new Edge("a", "b", 1), new Edge("b", "a", 1));
        var topology = GraphEdges.Topology.of(edges);
        var first = GraphEdges.layoutWithTopology(topology, Map.of("a", new Projected(10, 20, 1), "b", new Projected(40, 20, 1)), false);
        var second = GraphEdges.layoutWithTopology(topology, Map.of("a", new Projected(10, 50, 1), "b", new Projected(40, 50, 1)), false);
        assertEquals(edges, first.stream().map(GraphEdges.Path::edge).toList());
        assertEquals(edges, second.stream().map(GraphEdges.Path::edge).toList());
        assertNotEquals(first, second);
    }
    @Test void projectedSphereLanesRemainPickableAtDifferentZoomsAndHideBackHemisphere() {
        var forward = new Edge("a", "b", 1); var reverse = new Edge("b", "a", 3);
        for (double zoom : new double[]{.25, 1, 3}) {
            var a = GraphProjection.project(new GraphProjection.Point(-.6, 0, .8), .3, .2, 65 * zoom, 160, 107);
            var b = GraphProjection.project(new GraphProjection.Point(.6, 0, .8), .3, .2, 65 * zoom, 160, 107);
            var paths = GraphEdges.layout(List.of(forward, reverse), Map.of("a", a, "b", b), true);
            for (var path : paths) {
                var left = path.points().get(1); var right = path.points().get(2);
                assertEquals(path.edge(), GraphEdges.hit(paths, (left.x() + right.x()) / 2, (left.y() + right.y()) / 2));
            }
            var hidden = GraphEdges.layout(List.of(forward, reverse), Map.of("a", a, "b", new Projected(b.x(), b.y(), -1)), true);
            assertTrue(hidden.stream().noneMatch(GraphEdges.Path::hittable));
            assertNull(GraphEdges.hit(hidden, (a.x() + b.x()) / 2, (a.y() + b.y()) / 2));
        }
    }

    @Test void singleEdgesLoopsAndCoincidentReciprocalNodesStayFiniteAndSelectable() {
        var forward = new Edge("a", "b", 1); var reverse = new Edge("b", "a", 1); var loop = new Edge("a", "a", 1);
        var a = new Projected(60, 100, 1); var b = new Projected(260, 100, 1);
        var single = GraphEdges.layout(List.of(forward, loop), Map.of("a", a, "b", b), false);
        assertEquals(List.of(a, b), single.getFirst().points());
        assertEquals(forward, GraphEdges.hit(single, 160, 100));
        assertEquals(loop, GraphEdges.hit(single, 60, 82));
        assertNull(GraphEdges.hit(single, 160, 140));
        var coincident = GraphEdges.layout(List.of(forward, reverse), Map.of("a", a, "b", a), false);
        assertEquals(forward, GraphEdges.hit(coincident, 60, 118));
        assertEquals(reverse, GraphEdges.hit(coincident, 60, 82));
        assertTrue(coincident.stream().flatMap(path -> path.points().stream()).allMatch(p -> Double.isFinite(p.x()) && Double.isFinite(p.y())));
    }

    @Test void reciprocalDirectionsAreIndividuallyPickableRegardlessOfDeclarationOrder() {
        var forward = new Edge("a", "b", 1);
        var reverse = new Edge("b", "a", 3);
        var nodes = Map.of("a", new Projected(60, 100, 1), "b", new Projected(260, 100, 1));
        for (var order : List.of(List.of(forward, reverse), List.of(reverse, forward))) {
            var paths = GraphEdges.layout(order, nodes, false);
            assertEquals(forward, GraphEdges.hit(paths, 160, 108));
            assertEquals(reverse, GraphEdges.hit(paths, 160, 92));
        }
    }
}
