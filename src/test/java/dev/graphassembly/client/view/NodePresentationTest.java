package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NodePresentationTest {
    @Test void machineBadgeExtendsVisibleAndHittablePreviewWithoutMovingNativeOrigin() {
        var origin = new NodePresentation.Transform(10, 12, 2);
        var preview = NodePresentation.expandedPreview("campfire", 1, true, origin, 82, 12, true);
        var badge = preview.machineBadgeX();
        var viewport = new NodePresentation.Bounds(174, 12, 201, 60);

        assertEquals(82, badge);
        assertSame(origin, preview.transform());
        assertTrue(viewport.intersects(preview.bounds()));
        assertSame(preview, NodePresentation.hit(java.util.List.of(preview), viewport, 200, 43));
        assertNull(NodePresentation.hit(java.util.List.of(preview), viewport, 201, 43));
        assertFalse(viewport.intersects(NodePresentation.expandedPreview("ordinary", 1, true, origin, 82, 12, false).bounds()));
    }
    @Test void dynamicNativeNodesNeverDescribeAnInheritedOrDefaultStaticCarrier() {
        for(String serializer:java.util.List.of("minecraft:crafting_special_armordye","minecraft:crafting_special_repairitem","minecraft:smithing_trim","minecraft:crafting_shapeless")) {
            for(boolean finish:java.util.List.of(false,true)) {
                var json=com.google.gson.JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:leather_chestplate"},"transitional_item":"minecraft:iron_nugget",
                     "nodes":[{"id":"node","start":true,"finish":%s,"recipe":{"type":"%s"}}],"edges":[],"outputs":[]}
                    """.formatted(finish,serializer)).getAsJsonObject();
                var graph=dev.graphassembly.codec.GraphCodec.parse("test:carrier_tooltip",json);
                assertEquals(serializer.equals("minecraft:crafting_shapeless"),NodePresentation.showStaticCarrier(graph.nodes().get("node")),
                    "Dynamic native nodes omit unprovable carrier claims in both intermediate and finish tooltip paths");
            }
        }
    }
    @Test void planeThresholdsAndRotatedDepth() {
        assertEquals(1, NodePresentation.effectiveScale(false, 1, 0));
        assertFalse(NodePresentation.expanded(false, 1, true, true));
        assertTrue(NodePresentation.expanded(false, 1.25, true, true));
        assertTrue(NodePresentation.expanded(true, 1.10, true, true));
        assertFalse(NodePresentation.expanded(true, 1.09, true, true));
        var front = GraphProjection.project(new GraphProjection.Point(0, 0, 1), 0, 0, 65, 100, 40);
        var back = GraphProjection.project(new GraphProjection.Point(0, 0, 1), Math.PI, 0, 65, 100, 40);
        assertEquals(front.x(), back.x(), 1e-9);
        assertTrue(NodePresentation.effectiveScale(true, 1, front.depth()) > 1.25);
        assertFalse(NodePresentation.expanded(true, 2, back.depth() > 0, true));
    }
    @Test void rejectsInvalidDisplayNumbers() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, -1}) {
            assertFalse(NodePresentation.expanded(true, invalid, true, true));
            assertThrows(IllegalArgumentException.class, () -> new NodePresentation.Transform(0, 0, invalid));
            assertTrue(Double.isNaN(NodePresentation.effectiveScale(true, invalid, 1)));
        }
        assertTrue(Double.isNaN(NodePresentation.effectiveScale(true, 1, 3)));
        assertThrows(IllegalArgumentException.class, () -> new NodePresentation.Transform(Double.NaN, 0, 1));
    }
    @Test void depthScaleAndPerNodeHysteresis() {
        assertEquals(1.5, NodePresentation.effectiveScale(true, 1, 1), 1e-9);
        assertEquals(.75, NodePresentation.effectiveScale(true, 1, -1), 1e-9);
        assertFalse(NodePresentation.expanded(false, 1.2, true, true));
        assertTrue(NodePresentation.expanded(true, 1.2, true, true));
        assertFalse(NodePresentation.expanded(true, 2, false, true));
        assertFalse(NodePresentation.expanded(true, 2, true, false));
        var t = new NodePresentation.Transform(100, 40, 1.5);
        assertEquals(20, t.localX(130), 1e-9);
        assertEquals(10, t.localY(55), 1e-9);
    }
}
