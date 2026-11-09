package dev.graphassembly.client.view;

import dev.graphassembly.client.view.GraphProjection.Projected;
import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.definition.GraphLayout;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphWaypointsTest {
    @Test void authoredPlanePathIsDrawnAndPickedInsteadOfItsOldChord() {
        var edge = new Edge("a", "b", 1);
        var edgeLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(edge.from(), edge.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(30, 70), new GraphLayout.Waypoint(90, 70)))));
        var nodes = Map.of("a", new Projected(30, 40, 1), "b", new Projected(90, 40, 1));
        var paths = GraphEdges.layoutWithTopology(GraphEdges.Topology.of(List.of(edge), edgeLayout), nodes, false);
        assertEquals(List.of(nodes.get("a"), new Projected(30, 70, 1), new Projected(90, 70, 1), nodes.get("b")),
            paths.getFirst().points());
        assertEquals(edge, GraphEdges.hit(paths, 60, 70));
        assertNull(GraphEdges.hit(paths, 60, 40), "The old center-to-center chord is no longer an edge");
    }

    @Test void panningPastTheMiddleKeepsAnArrowOnTheVisibleAuthoredSegment() {
        var edge = new Edge("a", "b", 1);
        var edgeLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(edge.from(), edge.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(-1000, 80), new GraphLayout.Waypoint(1000, 80)))));
        var nodes = Map.of("a", new Projected(-1000, 40, 1), "b", new Projected(1000, 160, 1));
        var area = new NodePresentation.Bounds(8, 32, 312, 189);
        var prepared = GraphEdges.layoutRouted(GraphEdges.Topology.of(List.of(edge), edgeLayout), nodes, false,
            List.of(), area, GraphEdges.PlaneTransform.IDENTITY);
        var path = prepared.paths().getFirst();
        var arrow = GraphEdges.arrow(path);
        assertTrue(area.contains(arrow.tip().x(), arrow.tip().y()), "A visible authored line needs an on-screen direction marker");
        assertEquals(edge, GraphEdges.hitPrepared(prepared, arrow.tip().x(), arrow.tip().y()));
        assertEquals(new Projected(-1000, 80, 1), path.points().get(1), "Do not reroute or truncate the authored shape");
    }

    @Test void panAndZoomUseTheSameTransformForNodesBendsAndPicking() {
        var edge = new Edge("a", "b", 1);
        var edgeLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(edge.from(), edge.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(-30, 10), new GraphLayout.Waypoint(30, 10)))));
        for (double zoom : new double[]{.25, 1, 2.5}) {
            var transform = new GraphEdges.PlaneTransform(173, 105, zoom);
            var a = transform.project(new GraphLayout.Waypoint(-30, -20));
            var b = transform.project(new GraphLayout.Waypoint(30, -20));
            var paths = GraphEdges.layoutRouted(GraphEdges.Topology.of(List.of(edge), edgeLayout), Map.of("a", a, "b", b),
                false, List.of(), GraphGuiLayout.CANVAS, transform);
            assertEquals(List.of(a, transform.project(edgeLayout.edge(edge).waypoints().get(0)), transform.project(edgeLayout.edge(edge).waypoints().get(1)), b),
                paths.paths().getFirst().points());
            assertEquals(edge, GraphEdges.hitPrepared(paths, 173, 105 + 10 * zoom));
        }
    }

    @Test void expandedPreviewsNeverReplaceAnAuthoredContourOrLoopWithDetours() {
        var edge = new Edge("a", "a", 1);
        var edgeLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(edge.from(), edge.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(30, 70), new GraphLayout.Waypoint(90, 70)))));
        var node = new Projected(60, 40, 1);
        var surface = new NodePresentation.Surface("a", 1, true, true,
            new NodePresentation.Transform(20, 20, 1), 80, 35);
        var layout = GraphEdges.layoutRouted(GraphEdges.Topology.of(List.of(edge), edgeLayout), Map.of("a", node),
            false, List.of(surface), GraphGuiLayout.CANVAS, GraphEdges.PlaneTransform.IDENTITY);
        assertEquals(List.of(node, new Projected(30, 70, 1), new Projected(90, 70, 1), node), layout.paths().getFirst().points());
        var arrow = GraphEdges.arrow(layout.paths().getFirst());
        assertTrue(arrow.tip().y() > surface.bounds().bottom());
    }

    @Test void sphereIgnoresPlaneWaypointsAndLegacyEdgesKeepTheirOriginalArrow() {
        var a = new Projected(30, 40, 1); var b = new Projected(90, 40, 1);
        var edge = new Edge("a", "b", 1);
        var edgeLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(edge.from(), edge.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(60, 140)))));
        var sphere = GraphEdges.layoutWithTopology(GraphEdges.Topology.of(List.of(edge), edgeLayout), Map.of("a", a, "b", b), true).getFirst();
        assertEquals(List.of(a, b), sphere.points());
        assertFalse(sphere.authored());
        assertEquals(72, GraphEdges.arrow(sphere).tip().x());
        var plane = GraphEdges.layout(List.of(new Edge("a", "b", 1)), Map.of("a", a, "b", b), false).getFirst();
        assertEquals(GraphEdges.arrow(plane), GraphEdges.arrow(sphere));
    }

    @Test void duplicateBendsDoNotBecomeDegenerateDirectionMarkers() {
        var edge = new Edge("a", "b", 1);
        var edgeLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(edge.from(), edge.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(30, 40), new GraphLayout.Waypoint(90, 40)))));
        var paths = GraphEdges.layoutWithTopology(GraphEdges.Topology.of(List.of(edge), edgeLayout), Map.of("a", new Projected(30, 40, 1), "b", new Projected(90, 40, 1)), false);
        var arrow = GraphEdges.arrow(paths.getFirst());
        assertEquals(new Projected(60, 40, 1), arrow.tip());
        assertTrue(arrow.left().x() < arrow.tip().x());
    }

    @Test void editingOnlyBendsInvalidatesTheRasterMesh() {
        var nodes = Map.of("a", new Projected(30, 40, 1), "b", new Projected(90, 40, 1));
        var first = new Edge("a", "b", 1);
        var firstLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(first.from(), first.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(60, 70)))));
        var second = new Edge("a", "b", 1);
        var secondLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(second.from(), second.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(60, 90)))));
        var raster = new GraphRasterPlan(GraphGuiLayout.CANVAS);
        assertFalse(raster.update(GraphEdges.layoutWithTopology(GraphEdges.Topology.of(List.of(first), firstLayout), nodes, false), List.of(), nodes));
        assertTrue(raster.update(GraphEdges.layoutWithTopology(GraphEdges.Topology.of(List.of(first), firstLayout), nodes, false), List.of(), nodes));
        assertFalse(raster.update(GraphEdges.layoutWithTopology(GraphEdges.Topology.of(List.of(second), secondLayout), nodes, false), List.of(), nodes));
    }

    @Test void previewOccludingCenterAndQuartersLeavesAnArrowInTheRemainingGap() {
        var edge = new Edge("a", "b", 1);
        var edgeLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(edge.from(), edge.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(30, 80), new GraphLayout.Waypoint(290, 80)))));
        var nodes = Map.of("a", new Projected(30, 80, 1), "b", new Projected(290, 80, 1));
        var surface = new NodePresentation.Surface("obstacle", 1, true, true,
            new NodePresentation.Transform(90, 60, 1), 140, 40);
        var path = GraphEdges.layoutRouted(GraphEdges.Topology.of(List.of(edge), edgeLayout), nodes, false,
            List.of(surface), GraphGuiLayout.CANVAS, GraphEdges.PlaneTransform.IDENTITY).paths().getFirst();
        var arrow = GraphEdges.arrow(path);
        assertFalse(surface.bounds().contains(arrow.tip().x(), arrow.tip().y()));
        assertFalse(surface.bounds().contains(arrow.left().x(), arrow.left().y()));
        assertFalse(surface.bounds().contains(arrow.right().x(), arrow.right().y()));
        assertEquals(edge, NodePresentation.pick(List.of(surface), List.of(path), GraphGuiLayout.CANVAS,
            arrow.tip().x(), arrow.tip().y()).edge());
    }

    @Test void coincidentRouteDoesNotInventADirectionOrUnpickableWings() {
        var point = new Projected(100, 80, 1);
        var edge = new Edge("a", "b", 1);
        var edgeLayout = new GraphLayout(Map.of(), Map.of(new GraphLayout.Endpoint(edge.from(), edge.to()), new GraphLayout.EdgeLayout(List.of(new GraphLayout.Waypoint(100, 80)))));
        var path = GraphEdges.layoutWithTopology(GraphEdges.Topology.of(List.of(edge), edgeLayout), Map.of("a", point, "b", point), false).getFirst();
        var arrow = GraphEdges.arrow(path);
        assertEquals(point, arrow.tip());
        assertEquals(point, arrow.left());
        assertEquals(point, arrow.right());
    }
}
