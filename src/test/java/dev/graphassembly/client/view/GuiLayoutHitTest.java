package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiLayoutHitTest {
    @Test void visibleRectanglesAreTheHitRectanglesIncludingScaledAndResizedViews() {
        for (var rect : GraphGuiLayout.navigation()) {
            assertTrue(rect.contains(rect.left(), rect.top()));
            assertFalse(rect.contains(rect.right(), rect.top()));
            assertFalse(rect.contains(rect.left(), rect.bottom()));
            assertFalse(rect.contains(rect.left() - .01, rect.top()));
            assertFalse(rect.contains(rect.left(), rect.top() - .01));
            for (int[] screen : new int[][] {{640,360}, {427,240}, {550,330}, {854,480}}) {
                var viewport = RecipeViewport.fit(screen[0], screen[1]);
                double x = viewport.canvasX() + (rect.left() + rect.right()) / 2 * viewport.scale();
                double y = (rect.top() + rect.bottom()) / 2 * viewport.scale();
                assertTrue(rect.contains(viewport.local(x - viewport.canvasX()), viewport.local(y)));
                assertFalse(rect.contains(viewport.local((rect.right() + .01) * viewport.scale()), viewport.local(y)));
            }
        }
    }
}
