package dev.graphassembly.client.view;

import dev.graphassembly.client.view.NodePresentation.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanvasFrameClippingTest {
    @Test void fractionalResponsiveScaleNeverRoundsTheClipOutOntoTheFrameOrDetails() {
        var canvas = GraphGuiLayout.CANVAS;
        for (int[] screen : new int[][]{{640, 360}, {400, 300}, {427, 240}, {320, 240}, {550, 330}}) {
            var view = RecipeViewport.fit(screen[0], screen[1]);
            double x = 71 + view.canvasX(), y = 85, scale = view.scale();
            double left = x + canvas.left() * scale, top = y + canvas.top() * scale;
            double right = x + canvas.right() * scale, bottom = y + canvas.bottom() * scale;
            var clip = GraphGuiLayout.canvasScissor(left, top, right, bottom);
            assertTrue(clip.left() >= left && clip.top() >= top && clip.right() <= right && clip.bottom() <= bottom,
                "Fractional scale " + scale + " must clip inside, not outside: " + clip);
            assertTrue(clip.left() - left < 1 && clip.top() - top < 1
                && right - clip.right() < 1 && bottom - clip.bottom() < 1);
            assertTrue(clip.right() > clip.left() && clip.bottom() > clip.top());
        }
        assertEquals(new GraphGuiLayout.Scissor(160, 105, 464, 274),
            GraphGuiLayout.canvasScissor(160, 105, 464, 274), "Integer-scale rendering does not lose a pixel");
        assertEquals(new GraphGuiLayout.Scissor(11, 21, 11, 21),
            GraphGuiLayout.canvasScissor(10.2, 20.2, 10.8, 20.8), "Subpixel areas become empty, not negative");
    }

    @Test void zoomedMillstoneCanBePickedInsideButNotThroughTheFrame() {
        // The user's screenshot: native_ui, four zoom steps, pan=(10,75).
        double zoom = Math.pow(1.12, 4);
        double x = 160 + 10 + 90 * zoom, y = 107 + 75 - 27 * zoom;
        var mill = new Surface("mill", 1, true, true,
            new Transform(x - 40 * zoom, y - 42 * zoom, zoom), 80, 84);
        var nodes = List.of(mill);
        assertSame(mill, NodePresentation.hit(nodes, GraphGuiLayout.CANVAS, 311, 140));
        assertNull(NodePresentation.hit(nodes, GraphGuiLayout.CANVAS, 313, 140),
            "The enlarged millstone must not receive hover/clicks through the right PNG frame");
        var left = new Surface("press", 1, true, true, new Transform(-50, 50, 1.5), 80, 84);
        assertNull(NodePresentation.hit(List.of(left), GraphGuiLayout.CANVAS, 7, 100));

        var borderOnly = new Surface("offscreen", 1, true, true, new Transform(312, 80, 1.5), 80, 84);
        var plan = new GraphRasterPlan(GraphGuiLayout.CANVAS);
        plan.update(List.of(), List.of(borderOnly), Map.of());
        assertTrue(plan.commands().isEmpty(), "An animation wholly behind the frame is not drawn");
    }

    @Test void everyCanvasEdgeRejectsCoveredNodesAtPlaneAndSphereZoomExtremes() {
        var canvas = GraphGuiLayout.CANVAS;
        for (boolean sphere : new boolean[]{false, true}) for (double zoom : new double[]{.25, 1.25, 3}) {
            double scale = NodePresentation.effectiveScale(sphere, zoom, 1);
            for (double[] edge : new double[][]{{canvas.left(), 100, -1, 0}, {canvas.right(), 100, 1, 0}, {160, canvas.top(), 0, -1}, {160, canvas.bottom(), 0, 1}}) {
                var node = new Surface("edge", 1, true, true,
                    new Transform(edge[0] - 40 * scale, edge[1] - 42 * scale, scale), 80, 84);
                var nodes = List.of(node);
                assertSame(node, NodePresentation.hit(nodes, canvas, edge[0] - edge[2] * .1, edge[1] - edge[3] * .1));
                var outside = NodePresentation.pick(nodes, List.of(), canvas, edge[0] + edge[2] * .1, edge[1] + edge[3] * .1);
                assertNull(outside.node()); assertNull(outside.edge());
                assertEquals(new PreviewMouse(-10000, -10000),
                    NodePresentation.previewMouse(node, outside.node(), edge[0], edge[1]));
            }
        }
    }
}
