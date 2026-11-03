package dev.graphassembly.client.gui;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class GuiAtlasTest {
    @Test void atlasReplacementAcceptsIntegerHdScalingButRejectsWrongAspectOrDimensions() {
        assertTrue(GraphGuiAssets.matchesAtlasSize(1024, 512));
        assertTrue(GraphGuiAssets.matchesAtlasSize(2048, 1024));
        assertFalse(GraphGuiAssets.matchesAtlasSize(1024, 1024));
        assertFalse(GraphGuiAssets.matchesAtlasSize(1536, 768));
        assertFalse(GraphGuiAssets.matchesAtlasSize(0, 0));
        assertEquals(0xFF71D4AF, GuiColors.color("node.start"));
        assertEquals(0xFFF3EBDE, GuiColors.color("filter.choice"));
        assertThrows(IllegalArgumentException.class, () -> GuiColors.color("missing"));
    }

    @Test void shippedPngContainsEveryDeclaredRegion() throws Exception {
        var file = Path.of("src/main/resources/assets/graph_assembly", GraphGuiAssets.ATLAS_PNG);
        assertTrue(java.nio.file.Files.exists(file), "Missing artist-editable atlas PNG");
        var image = ImageIO.read(file.toFile());
        assertNotNull(image);
        assertEquals(GraphGuiAssets.ATLAS_WIDTH, image.getWidth());
        assertEquals(GraphGuiAssets.ATLAS_HEIGHT, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        for (var sprite : GraphGuiAssets.Sprite.values()) {
            boolean visible = false;
            for (int y = sprite.y(); y < sprite.y() + sprite.height(); y++)
                for (int x = sprite.x(); x < sprite.x() + sprite.width(); x++)
                    visible |= (image.getRGB(x, y) >>> 24) != 0;
            assertTrue(visible, "Empty placeholder: " + sprite);
        }
    }

    @Test void nineSlicesCoverEveryTargetPixelExactlyOnceEvenWhenSmall() {
        for (var sprite : GraphGuiAssets.Sprite.values()) {
            for (int[] size : new int[][] {{1,1}, {3,8}, {18,14}, {75,17}, {321,229}}) {
                var covered = new boolean[size[1]][size[0]];
                for (var slice : GuiNineSlice.slices(sprite, size[0], size[1])) {
                    assertTrue(slice.sourceWidth() > 0 && slice.sourceHeight() > 0);
                    assertTrue(slice.u() >= sprite.x() && slice.v() >= sprite.y());
                    assertTrue(slice.u() + slice.sourceWidth() <= sprite.x() + sprite.width());
                    assertTrue(slice.v() + slice.sourceHeight() <= sprite.y() + sprite.height());
                    for (int y = slice.y(); y < slice.y() + slice.height(); y++) {
                        for (int x = slice.x(); x < slice.x() + slice.width(); x++) {
                            assertFalse(covered[y][x], "Overlapping slices: " + sprite);
                            covered[y][x] = true;
                        }
                    }
                }
                for (var row : covered) for (boolean pixel : row) assertTrue(pixel, "Gap: " + sprite);
            }
        }
        assertTrue(GuiNineSlice.slices(GraphGuiAssets.Sprite.GRAPH_PANEL, 0, 10).isEmpty());
    }

    @Test void nineSliceCornersKeepTheirNativeSize() {
        var parts = GuiNineSlice.slices(GraphGuiAssets.Sprite.GRAPH_PANEL, 75, 32);
        var corner = parts.getFirst();
        assertEquals(11, corner.width());
        assertEquals(11, corner.height());
        assertEquals(11, corner.sourceWidth());
        assertEquals(11, corner.sourceHeight());
    }

    @Test void resizedPanelDoesNotStretchFramePixelsIntoContent() throws Exception {
        var image = ImageIO.read(Path.of("src/main/resources/assets/graph_assembly", GraphGuiAssets.ATLAS_PNG).toFile());
        var center = GuiNineSlice.slices(GraphGuiAssets.Sprite.GRAPH_PANEL, 240, 128).get(4);
        int background = image.getRGB(160, 112);
        for (int y = center.v(); y < center.v() + center.sourceHeight(); y++)
            for (int x = center.u(); x < center.u() + center.sourceWidth(); x++)
                assertEquals(background, image.getRGB(x, y), "Panel stretch region includes frame at " + x + "," + y);
    }

    @Test void rowTextFrameStretchesOnlyTheChoiceStripMiddle() {
        var sprite = GraphGuiAssets.Sprite.FILTER_CHOICE;
        var parts = GuiNineSlice.slices(sprite, 168, 18);
        assertEquals(9, parts.size());
        assertEquals(8, parts.get(0).width());
        assertEquals(152, parts.get(1).width());
        assertEquals(8, parts.get(2).width());
        assertEquals(sprite.x(), parts.get(0).u());
        assertEquals(sprite.x() + 8, parts.get(1).u());
        assertEquals(sprite.x() + sprite.width() - 8, parts.get(2).u());
    }

    @Test void atlasRegionsAreSeparateAndInsideThePng() {
        for (var a : GraphGuiAssets.Sprite.values()) {
            assertTrue(a.x() >= 0 && a.y() >= 0);
            assertTrue(a.x() + a.width() <= GraphGuiAssets.ATLAS_WIDTH);
            assertTrue(a.y() + a.height() <= GraphGuiAssets.ATLAS_HEIGHT);
            for (var b : GraphGuiAssets.Sprite.values()) {
                if (a == b) continue;
                if (a == GraphGuiAssets.Sprite.FILTER_ICON_SLOT && b == GraphGuiAssets.Sprite.FILTER_PANEL
                    || b == GraphGuiAssets.Sprite.FILTER_ICON_SLOT && a == GraphGuiAssets.Sprite.FILTER_PANEL) continue;
                if (a == GraphGuiAssets.Sprite.CLEAR && b == GraphGuiAssets.Sprite.REMOVE
                    || b == GraphGuiAssets.Sprite.CLEAR && a == GraphGuiAssets.Sprite.REMOVE) continue;
                assertFalse(a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                    && a.y() < b.y() + b.height() && b.y() < a.y() + a.height(), a + " overlaps " + b);
            }
        }
    }
}
