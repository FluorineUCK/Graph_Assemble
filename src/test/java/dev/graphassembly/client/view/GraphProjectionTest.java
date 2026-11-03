package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import dev.graphassembly.core.GraphCodec;
import static org.junit.jupiter.api.Assertions.*;

class GraphProjectionTest {
    @Test void lineRasterizationClipsToViewportBeforeChoosingStepCount() {
        var segment = GraphProjection.clipSegment(-1e300, 100, 1e300, 100, 0, 20, 320, 189);
        assertNotNull(segment);
        assertEquals(0, segment[0], 0.001);
        assertEquals(320, segment[2], 0.001);
        assertEquals(100, segment[1], 0.001);
        assertEquals(100, segment[3], 0.001);
        assertNull(GraphProjection.clipSegment(-1e300, -100, 1e300, -100, 0, 20, 320, 189));
        assertNull(GraphProjection.clipSegment(Double.POSITIVE_INFINITY, 50, 0, 50, 0, 20, 320, 189));
        var normal = GraphProjection.clipSegment(100, 100, 200, 150, 0, 20, 320, 189);
        assertArrayEquals(new double[]{100, 100, 200, 150}, normal, 0.001);
    }
    @Test void extremeFiniteSphereCoordinatesRemainFinite() {
        for (String coordinate : new String[]{"1e300", "1e-300"}) {
            var json = JsonParser.parseString("{\"transitional_item\":\"minecraft:stone\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"nodes\":[{\"id\":\"s\",\"start\":true,\"sphere_position\":[" + coordinate + ",0,0],\"recipe\":{\"type\":\"create:pressing\"}}],\"edges\":[]}").getAsJsonObject();
            var point = GraphProjection.layout(GraphCodec.parse("test:graph", json), true).get("s");
            assertEquals(1, point.x(), 1e-12);
            assertEquals(0, point.y());
        }
    }
    @Test void backHemisphereCannotBeClickedAndRotatingMovesItToFront() {
        var back = GraphProjection.project(new GraphProjection.Point(0, 0, -1), 0, 0, 100, 150, 100);
        assertFalse(back.hittable());
        var front = GraphProjection.project(new GraphProjection.Point(0, 0, -1), Math.PI, 0, 100, 150, 100);
        assertTrue(front.hittable());
        assertEquals(150, front.x(), 0.001);
        assertEquals(100, front.y(), 0.001);
    }
}
