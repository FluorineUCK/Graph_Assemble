package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphChromeLayoutTest {
    @Test void modeAndRightAlignedResetUseNativeSquareButtonsAndKeepTheCanvasUnchanged() {
        var buttons = GraphGuiLayout.navigation();
        assertEquals(2, buttons.size());
        var button = buttons.getFirst();
        assertEquals(18, button.right() - button.left());
        assertEquals(18, button.bottom() - button.top());
        assertTrue(button.contains(13, 11));
        assertFalse(button.contains(30, 11));
        assertFalse(button.contains(58, 11), "The old second button must no longer be clickable");
        assertEquals(8, GraphGuiLayout.CONTROLS.left() - button.right());
        var reset = buttons.getLast();
        assertEquals(GraphGuiLayout.RESET_VIEW, reset);
        assertEquals(18, reset.right() - reset.left());
        assertEquals(18, reset.bottom() - reset.top());
        assertEquals(GraphGuiLayout.DETAILS.right(), reset.right());
        assertEquals(8, reset.left() - GraphGuiLayout.CONTROLS.right());
        assertFalse(reset.intersects(button));
        assertEquals(new NodePresentation.Bounds(8, 32, 312, 189), GraphGuiLayout.CANVAS);
    }
    @Test void cameraAndBackgroundOriginFollowTheCanvasWhenTheHeaderMoves() {
        var canvas = GraphGuiLayout.CANVAS;
        assertEquals((canvas.left() + canvas.right()) / 2, GraphGuiLayout.canvasCenterX());
        assertEquals((canvas.top() + canvas.bottom()) / 2, GraphGuiLayout.canvasCenterY(),
            "Do not retain the old 107px origin after reserving a taller header");
    }
    @Test void viewModeStaysInsideThePngFrameWithPaddingAndDoesNotCoverTheCanvas() {
        for (var button : GraphGuiLayout.navigation()) {
            assertTrue(button.left() >= 12, "8px PNG frame plus 4px content padding");
            assertTrue(button.top() >= 10, "Do not draw the button on the top frame");
            assertTrue(button.bottom() + 4 <= GraphGuiLayout.CANVAS.top(), "Keep a gap before the canvas");
            assertFalse(button.contains(4, 5), "Old frame coordinates must not remain clickable");
        }
    }
    @Test void headerTextAndDetailsFitTheirAllocatedSpaceAtDifferentFontHeights() {
        for (var button : GraphGuiLayout.navigation()) assertFalse(button.intersects(GraphGuiLayout.CONTROLS));
        assertEquals(GraphGuiLayout.VIEW_MODE.left(), GraphGuiLayout.DETAILS.left());
        assertEquals(GraphGuiLayout.RESET_VIEW.right(), GraphGuiLayout.DETAILS.right());
        for (int height : new int[]{9, 10, 12, 18}) {
            for (var button : GraphGuiLayout.navigation()) {
                int modeY = GraphGuiLayout.centeredTextY(button, height);
                assertTrue(modeY >= button.top());
                assertTrue(modeY + height <= button.bottom());
            }
            int rows = GraphGuiLayout.detailRows(height);
            assertTrue(rows >= 1 && rows <= 2);
            for (int row = 0; row < rows; row++)
                assertTrue(GraphGuiLayout.detailY(row, height) + height <= GraphGuiLayout.DETAILS.bottom());
        }
        assertTrue(GraphGuiLayout.DETAILS.top() > GraphGuiLayout.CANVAS.bottom());
    }
}
