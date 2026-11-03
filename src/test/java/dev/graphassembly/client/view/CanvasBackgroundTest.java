package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanvasBackgroundTest {
    private final NodePresentation.Bounds area = new NodePresentation.Bounds(8, 20, 312, 189);
    @Test void textureFollowsTheSamePanAndZoomAsGraphWorldCoordinates() {
        var original = CanvasBackground.uv(area, 160, 107, 1, 16);
        var moved = CanvasBackground.uv(area, 165, 104, 1, 16);
        assertEquals(fract(original.left() - 5.0 / 16), moved.left(), 1e-10);
        assertEquals(fract(original.top() + 3.0 / 16), moved.top(), 1e-10);
        var zoomed = CanvasBackground.uv(area, 165, 104, 2, 16);
        assertEquals((moved.right() - moved.left()) / 2, zoomed.right() - zoomed.left(), 1e-10);
    }
    @Test void negativeAndVeryLargePanKeepBoundedSeamlessUvPhase() {
        for (double x : new double[]{-10000000, -17, -.25, 0, 17, 10000000}) {
            var a = CanvasBackground.uv(area, x, x, .25, 16);
            var b = CanvasBackground.uv(area, x + 4, x + 4, .25, 16);
            assertTrue(a.left() >= 0 && a.left() < 1);
            assertTrue(a.top() >= 0 && a.top() < 1);
            assertEquals(a, b, "Panning by one displayed tile must repeat exactly");
            assertEquals(304 / 4.0, a.right() - a.left(), 1e-10);
        }
    }
    private static double fract(double d) { return d - Math.floor(d); }
}
