package dev.graphassembly.compat.jei;

import dev.graphassembly.client.view.RecipeViewStateCache;
import dev.graphassembly.core.Edge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphViewStateTest {
    @Test void resetCameraRestoresCurrentModesInitialCameraWithoutClearingSelection() throws Exception {
        var graph = dev.graphassembly.core.GraphCodec.parse("test:reset", com.google.gson.JsonParser.parseString(
            java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/data/graph_assembly/recipe/graph_filter.json")))
            .getAsJsonObject());
        for (boolean sphere : new boolean[]{false, true}) {
            var initial = new GraphViewState(sphere, graph);
            var view = new GraphViewState(!sphere, graph);
            view.selectMode(sphere);
            view.panX = 121; view.panY = -73; view.zoom = 2.6; view.yaw = .7; view.pitch = -1.2;
            view.selected = "body_ready"; view.selectedFinish = "assemble";
            view.selectedEdge = graph.edges().getFirst();
            var edge = view.selectedEdge;
            view.resetCamera(graph);
            assertEquals(sphere, view.sphere);
            assertEquals(initial.panX, view.panX); assertEquals(initial.panY, view.panY);
            assertEquals(initial.zoom, view.zoom); assertEquals(initial.yaw, view.yaw); assertEquals(initial.pitch, view.pitch);
            assertEquals("body_ready", view.selected); assertEquals("assemble", view.selectedFinish); assertSame(edge, view.selectedEdge);
            view.resetCamera(graph);
            assertEquals(initial.zoom, view.zoom); assertEquals(initial.panX, view.panX);
        }
    }
    @Test void compactAuthoredAndLegacyGraphsKeepTheirOriginalInitialCamera() {
        var json = com.google.gson.JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"position":[-40,-30],"recipe":{"type":"create:pressing"}},
                      {"id":"f","finish":true,"position":[40,30],"recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"s","to":"f","waypoints":[[0,-50]]}],"outputs":[]}
            """).getAsJsonObject();
        var compact = new GraphViewState(false, dev.graphassembly.core.GraphCodec.parse("test:compact", json));
        assertEquals(1, compact.zoom); assertEquals(0, compact.panX); assertEquals(0, compact.panY);
        json.getAsJsonArray("edges").get(0).getAsJsonObject().remove("waypoints");
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("position", com.google.gson.JsonParser.parseString("[-500,-500]"));
        var legacy = new GraphViewState(false, dev.graphassembly.core.GraphCodec.parse("test:legacy", json));
        assertEquals(1, legacy.zoom); assertEquals(0, legacy.panX); assertEquals(0, legacy.panY);
    }
    @Test void spaciousAuthoredGraphStartsWithItsWholeContourVisibleAndKeepsUserCameraOnRebuild() throws Exception {
        var graph = dev.graphassembly.core.GraphCodec.parse("test:spacious", com.google.gson.JsonParser.parseString(
            java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/data/graph_assembly/recipe/graph_filter.json")))
            .getAsJsonObject());
        var states = new RecipeViewStateCache<dev.graphassembly.core.GraphDefinition, GraphViewState>();
        var view = states.get(graph, () -> new GraphViewState(false, graph));
        var points = new java.util.ArrayList<dev.graphassembly.core.Edge.Waypoint>();
        graph.nodes().values().forEach(n -> points.add(new dev.graphassembly.core.Edge.Waypoint(n.position().get(0), n.position().get(1))));
        graph.edges().forEach(e -> points.addAll(e.waypoints()));
        var canvas = dev.graphassembly.client.view.GraphGuiLayout.CANVAS;
        for (var p : points) {
            double x = dev.graphassembly.client.view.GraphGuiLayout.canvasCenterX() + view.panX + p.x() * view.zoom;
            double y = dev.graphassembly.client.view.GraphGuiLayout.canvasCenterY() + view.panY + p.y() * view.zoom;
            assertTrue(x >= canvas.left() + 7.9 && x <= canvas.right() - 7.9
                && y >= canvas.top() + 7.9 && y <= canvas.bottom() - 7.9, "Contour clipped at " + p);
        }
        assertTrue(view.zoom >= .25 && view.zoom < 1);
        view.zoom = 2; view.panX = 34; view.panY = -87;
        var rebuilt = states.get(graph, () -> new GraphViewState(false, graph));
        assertSame(view, rebuilt);
        assertEquals(2, rebuilt.zoom); assertEquals(34, rebuilt.panX); assertEquals(-87, rebuilt.panY);
        var sphere = new GraphViewState(true, graph);
        assertEquals(1, sphere.zoom); assertEquals(0, sphere.panX); assertEquals(0, sphere.panY);
    }
    @Test void finishScopeAndOrdinarySelectionAreIndependentAndDoNotMoveCamera() {
        var graph = dev.graphassembly.core.GraphCodec.parse("test:view", com.google.gson.JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                      {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],"outputs":[]}
            """).getAsJsonObject());
        var view = new GraphViewState(false);
        view.zoom = 2; view.panX = 40; view.yaw = .8;
        assertTrue(view.selectFinish(graph, "f"));
        assertEquals("f", view.selectedFinish); assertEquals("f", view.selected);
        assertFalse(view.selectFinish(graph, "s"));
        view.selectNode(graph, null);
        view.selectedEdge = null;
        assertEquals("f", view.detailsNode(), "Empty canvas keeps scoped finish details");
        assertEquals("f", view.selectedFinish); assertNull(view.selected);
        assertEquals(2, view.zoom); assertEquals(40, view.panX); assertEquals(.8, view.yaw);
        view.selectNode(graph, "s");
        assertEquals("s", view.detailsNode(), "Ordinary selection takes detail priority");
        view.selected = null; view.selectedEdge = new Edge("s", "f", 1);
        assertNull(view.detailsNode(), "An edge takes detail priority over finish scope");
        view.selectNode(graph, "s"); view.selectedEdge = null;
        assertEquals("f", view.selectedFinish); assertEquals("s", view.selected);
        view.overview();
        assertNull(view.selectedFinish); assertEquals("s", view.selected);
        view.selectNode(graph, "f");
        assertEquals("f", view.selectedFinish);
        view.overview(); assertNull(view.selected); assertNull(view.selectedFinish);
        assertEquals(2, view.zoom); assertEquals(40, view.panX); assertEquals(.8, view.yaw);
    }
    @Test void selectingAModeIsIdempotentAndPreservesCameraAndSelection() {
        var view = new GraphViewState(false);
        view.zoom = 2; view.panX = 40; view.panY = -16;
        view.yaw = .8; view.pitch = -.3; view.selected = "press";
        view.selectedEdge = new Edge("press", "fill", 1);
        var edge = view.selectedEdge;
        assertFalse(view.selectMode(false));
        assertFalse(view.sphere);
        assertTrue(view.selectMode(true));
        assertTrue(view.sphere);
        assertFalse(view.selectMode(true));
        assertTrue(view.sphere);
        assertEquals(2, view.zoom); assertEquals(40, view.panX); assertEquals(-16, view.panY);
        assertEquals(.8, view.yaw); assertEquals(-.3, view.pitch);
        assertEquals("press", view.selected); assertSame(edge, view.selectedEdge);
        assertTrue(view.selectMode(false));
        assertFalse(view.sphere);
        assertEquals(2, view.zoom); assertEquals("press", view.selected);
    }
    @Test void rebuildRetainsCameraAndSelectionWithoutSharingAnotherGraphsState() {
        var states = new RecipeViewStateCache<Object, GraphViewState>();
        var graph = new Object();
        var before = states.get(graph, () -> new GraphViewState(false));
        before.zoom = 2; before.panX = 40; before.panY = -16;
        before.yaw = .8; before.pitch = -.3; before.sphere = true;
        before.selected = "press"; before.selectedFinish = "finish_gold";
        before.selectedEdge = new Edge("press", "fill", 1);
        var after = states.get(graph, () -> new GraphViewState(false));
        assertSame(before, after);
        assertEquals(2, after.zoom); assertEquals(40, after.panX); assertEquals(-16, after.panY);
        assertEquals(.8, after.yaw); assertEquals(-.3, after.pitch); assertTrue(after.sphere);
        assertEquals("press", after.selected); assertEquals("finish_gold", after.selectedFinish); assertEquals(before.selectedEdge, after.selectedEdge);
        var other = states.get(new Object(), () -> new GraphViewState(false));
        assertEquals(1, other.zoom); assertNull(other.selected);
    }
}
