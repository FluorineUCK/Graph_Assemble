package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeViewStateCacheTest {
    @Test void rebuildingLayoutKeepsViewDataButOtherRecipesAndNewRuntimeDoNotShareIt() {
        var cache = new RecipeViewStateCache<Object, double[]>();
        var recipe = new Object();
        var first = cache.get(recipe, () -> new double[]{0, 0, 1});
        first[0] = 73; first[1] = -32; first[2] = 1.7;
        // JEI recreates its layout on resize; the factory must not reset its view.
        assertSame(first, cache.get(recipe, () -> new double[]{0, 0, 1}));
        assertNotSame(first, cache.get(new Object(), () -> new double[]{0, 0, 1}));
        cache.clear();
        assertArrayEquals(new double[]{0, 0, 1}, cache.get(recipe, () -> new double[]{0, 0, 1}));
    }
}
