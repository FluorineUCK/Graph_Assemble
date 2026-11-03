package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeViewportTest {
    @Test void scaleThreeAndResizeKeepCanvasControlsAndSlotsInsideJeiBody() {
        for (int[] screen : new int[][] {{640, 360}, {427, 240}, {400, 225}, {320, 240}, {854, 480}}) {
            var view = RecipeViewport.fit(screen[0], screen[1]);
            assertTrue(view.height() <= screen[1] - 110, "JEI header, tabs and padding must remain outside the recipe");
            assertTrue(view.width() <= screen[0] - 40);
            assertTrue(view.slotY() + 18 <= view.height());
            assertTrue(view.slotX(263) + 16 < view.width());
            assertTrue(view.canvasX() >= 0);
            assertTrue(view.canvasX() + 320 * view.scale() <= view.width());
            for (double value : new double[] {0, 20, 160, 225, 320})
                assertEquals(value, view.local(value * view.scale()), 1e-9, "Drawing and every pointer callback must invert one scale");
        }
        assertEquals(1, RecipeViewport.fit(640, 360).scale());
    }
}
