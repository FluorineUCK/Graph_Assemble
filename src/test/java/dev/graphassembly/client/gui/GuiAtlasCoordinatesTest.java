package dev.graphassembly.client.gui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static dev.graphassembly.client.gui.GraphGuiAssets.Sprite.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public UV contract of the artist-rearranged PNG, not screen/widget coordinates. */
class GuiAtlasCoordinatesTest {
    @Test void finalizedFilterPanelUsesOnlyOpaqueSeparatedArtwork() {
        assertArrayEquals(new int[] {336, 0, 232, 95}, rectangle(FILTER_PANEL));
        assertArrayEquals(new int[] {336, 95, 232, 20}, rectangle(FILTER_PANEL_UPPER));
        assertArrayEquals(new int[] {336, 116, 232, 20}, rectangle(FILTER_PANEL_MIDDLE));
        assertArrayEquals(new int[] {336, 137, 232, 19}, rectangle(FILTER_PANEL_LOWER));
        assertArrayEquals(new int[] {336, 156, 241, 30}, rectangle(FILTER_PANEL_FOOTER));
    }

    private static int[] rectangle(GraphGuiAssets.Sprite sprite) {
        return new int[] {sprite.x(), sprite.y(), sprite.width(), sprite.height()};
    }

    private record Region(GraphGuiAssets.Sprite sprite, int x, int y, int width, int height) {}

    @Test void readsEachSpriteFromItsRelocatedArtwork() {
        var regions = List.of(
            new Region(GRAPH_PANEL, 0, 0, 320, 224),
            new Region(FILTER_PANEL, 336, 0, 232, 95),
            new Region(FILTER_PANEL_UPPER, 336, 95, 232, 20),
            new Region(FILTER_PANEL_MIDDLE, 336, 116, 232, 20),
            new Region(FILTER_PANEL_LOWER, 336, 137, 232, 19),
            new Region(FILTER_PANEL_FOOTER, 336, 156, 241, 30),
            new Region(ADD, 592, 16, 16, 16),
            new Region(REMOVE, 608, 16, 16, 16),
            new Region(CLEAR, 608, 16, 16, 16),
            new Region(CONFIRM, 624, 16, 16, 16),
            new Region(SPHERE, 640, 16, 16, 16),
            new Region(PLANE, 656, 16, 16, 16),
            new Region(FILTER_CHOICE, 592, 47, 115, 18),
            new Region(FILTER_ICON_SLOT, 351, 33, 18, 18)
        );
        assertEquals(GraphGuiAssets.Sprite.values().length, regions.size());
        for (var region : regions) {
            var sprite = region.sprite();
            assertArrayEquals(new int[] {region.x(), region.y(), region.width(), region.height()},
                new int[] {sprite.x(), sprite.y(), sprite.width(), sprite.height()}, sprite.name());
        }
    }

    @Test void selectedRowIconFrameReusesTheExistingPanelInset() {
        var sprite = FILTER_ICON_SLOT;
        assertArrayEquals(new int[] {351, 33, 18, 18},
            new int[] {sprite.x(), sprite.y(), sprite.width(), sprite.height()});
        assertEquals(0, sprite.border());
        assertEquals(GraphGuiAssets.Fallback.JEI_SLOT, sprite.fallback());
    }
}
