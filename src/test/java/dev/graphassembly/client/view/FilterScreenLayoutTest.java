package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FilterScreenLayoutTest {
    @Test void finalizedPanelSegmentsCoverTheLogicalBackgroundWithoutStretchingTheRepeatingBody() {
        var layout = FilterScreenLayout.fit(640, 360);
        assertEquals(new NodePresentation.Bounds(0, 0, 241, 226), layout.panel());
        assertEquals(new NodePresentation.Bounds(0, 0, 232, 95), layout.panelHeader());
        assertEquals(new NodePresentation.Bounds(0, 95, 232, 115), layout.panelUpper());
        assertEquals(java.util.List.of(
            new NodePresentation.Bounds(0, 115, 232, 135),
            new NodePresentation.Bounds(0, 135, 232, 155),
            new NodePresentation.Bounds(0, 155, 232, 175),
            new NodePresentation.Bounds(0, 175, 232, 177)), layout.panelMiddleTiles());
        assertEquals(new NodePresentation.Bounds(0, 177, 232, 196), layout.panelLower());
        assertEquals(new NodePresentation.Bounds(0, 196, 241, 226), layout.panelFooter());
    }

    @Test void selectedRowFramesContainIconAndTextWithoutTouchingRemoveButton() {
        var layout = FilterScreenLayout.fit(640, 360);
        for (int row = 0; row < FilterScreenLayout.ROWS; row++) {
            var line = layout.row(row);
            var iconFrame = layout.rowIconFrame(row);
            var textFrame = layout.rowTextFrame(row);
            var icon = layout.rowIcon(row);
            var label = layout.rowLabel(row);
            var remove = layout.removeButton(row);
            assertEquals(18, iconFrame.right() - iconFrame.left());
            assertEquals(18, iconFrame.bottom() - iconFrame.top());
            assertEquals(18, textFrame.bottom() - textFrame.top());
            assertEquals(line.top() + 1, iconFrame.top());
            assertEquals(line.top() + 1, textFrame.top());
            assertTrue(iconFrame.left() <= icon.left() && icon.right() <= iconFrame.right());
            assertTrue(iconFrame.top() <= icon.top() && icon.bottom() <= iconFrame.bottom());
            assertTrue(textFrame.left() < label.left() && label.right() < textFrame.right());
            assertTrue(textFrame.top() <= label.top() && label.bottom() <= textFrame.bottom());
            assertFalse(iconFrame.intersects(textFrame));
            assertFalse(textFrame.intersects(remove));
        }
    }

    @Test void selectedRowFramesStaySeparatedFromRemoveButtonsOnSmallScreens() {
        var layout = FilterScreenLayout.fit(280, 200);
        assertTrue(layout.transform().scale() < 1);
        for (int row = 0; row < FilterScreenLayout.ROWS; row++) {
            var icon = layout.toScreen(layout.rowIconFrame(row));
            var text = layout.toScreen(layout.rowTextFrame(row));
            var remove = layout.toScreen(layout.removeButton(row));
            assertTrue(icon.right() <= text.left());
            assertTrue(text.right() <= remove.left());
            assertEquals(18 * layout.transform().scale(), icon.bottom() - icon.top(), 1e-8);
            assertEquals(18 * layout.transform().scale(), text.bottom() - text.top(), 1e-8);
            var center = layout.toScreen((layout.rowTextFrame(row).left() + layout.rowTextFrame(row).right()) / 2,
                (layout.rowTextFrame(row).top() + layout.rowTextFrame(row).bottom()) / 2);
            var local = layout.toLocal(center.x(), center.y());
            assertTrue(layout.rowTextFrame(row).contains(local.x(), local.y()));
        }
    }

    @Test void sidebarReservationIncludesPreviewAndDeclinesInsufficientSpace() {
        for (var layout : java.util.List.of(FilterScreenLayout.fit(640,360), FilterScreenLayout.fit(427,240))) {
            var area = layout.screenAreaForSidebar(32);
            assertEquals(layout.screenPixels(layout.occupation()), area);
            assertTrue(area.right() >= layout.screenPixels(layout.previewOccupation()).right());
            assertTrue(area.bottom() >= layout.screenPixels(layout.previewOccupation()).bottom());
            assertTrue(layout.screenWidth() - area.right() >= 32);
        }
        assertNull(FilterScreenLayout.fit(320,240).screenAreaForSidebar(32));
    }
    @Test void extraAreasAreEmptyWithoutAFilterAndRecomputedFromActiveLayout() {
        assertTrue(FilterScreenLayout.screenExtraAreas(null).isEmpty());
        var normal = FilterScreenLayout.fit(640, 360);
        var small = FilterScreenLayout.fit(280, 200);
        for (var layout : java.util.List.of(normal, small)) {
            assertEquals(java.util.List.of(layout.screenPixels(layout.nativeExtraArea()),
                layout.screenPixels(layout.previewOccupation())), FilterScreenLayout.screenExtraAreas(layout));
        }
        assertNotEquals(FilterScreenLayout.screenExtraAreas(normal), FilterScreenLayout.screenExtraAreas(small));
    }
    @Test void nativePreviewAndWholeInterfaceFitNormalCompactAndScaledWindows() {
        for (int[] size : new int[][] {{640,360}, {426,240}, {320,360}, {312,360}, {280,200}}) {
            var layout = FilterScreenLayout.fit(size[0], size[1]);
            assertEquals(241, layout.panelWidth());
            assertEquals(226, layout.panel().bottom());
            var preview = layout.preview();
            assertEquals(layout.panel().right() + 8, preview.x());
            assertEquals(layout.panel().bottom() - 52, preview.y());
            assertEquals(-200, preview.z());
            assertEquals(4, preview.scale());
            assertEquals(new NodePresentation.Bounds(241, 186, 321, 234), layout.nativeExtraArea());
            assertEquals(new NodePresentation.Bounds(241, 174, 321, 238), layout.previewOccupation());
            var occupied = layout.toScreen(layout.occupation());
            assertTrue(occupied.left() >= 8 - 1e-8 && occupied.top() >= 8 - 1e-8);
            assertTrue(occupied.right() <= size[0] - 8 + 1e-8 && occupied.bottom() <= size[1] - 8 + 1e-8);
            assertEquals((size[0] - occupied.right() + occupied.left()) / 2, occupied.left(), 1e-8);
            assertTrue(layout.transform().scale() > 0 && layout.transform().scale() <= 1);
        }
        assertEquals(1, FilterScreenLayout.fit(640,360).transform().scale());
        assertTrue(FilterScreenLayout.fit(426,240).transform().scale() < 1);
        assertTrue(FilterScreenLayout.fit(280,200).transform().scale() < 1);
    }

    @Test void nativeControlsCoverBakedSlotsAndFooterModesDoNotOverlapActions() {
        for (var layout : java.util.List.of(FilterScreenLayout.fit(640, 360), FilterScreenLayout.fit(280, 200))) {
            assertEquals(new NodePresentation.Bounds(38, 33, 224, 51), layout.graphSelector());
            assertEquals(new NodePresentation.Bounds(38, 63, 173, 81), layout.nodeSelector());
            assertEquals(new NodePresentation.Bounds(179, 63, 197, 81), layout.addButton());
            assertEquals(new NodePresentation.Bounds(197, 63, 215, 81), layout.addInvertedButton());
            assertFalse(layout.nodeSelector().intersects(layout.addButton()));
            assertFalse(layout.addButton().intersects(layout.addInvertedButton()));
            for (int mode = 0; mode < 3; mode++) {
                var button = layout.modeButton(mode);
                assertEquals(18, button.right() - button.left());
                assertEquals(18, button.bottom() - button.top());
                assertFalse(button.intersects(layout.clearButton()));
                assertFalse(button.intersects(layout.confirmButton()));
                assertFalse(button.intersects(layout.rangeLabel()));
                for (int other = mode + 1; other < 3; other++) assertFalse(button.intersects(layout.modeButton(other)));
                var center = layout.toScreen((button.left() + button.right()) / 2, (button.top() + button.bottom()) / 2);
                assertTrue(button.contains(layout.toLocal(center.x(), center.y()).x(), layout.toLocal(center.x(), center.y()).y()));
            }
        }
    }

    @Test void controlsHitTestsAndExclusionsUseTheSameTransformAfterResize() {
        var normal = FilterScreenLayout.fit(640,360);
        var small = FilterScreenLayout.fit(280,200);
        assertNotEquals(normal.screenPixels(normal.panel()), small.screenPixels(small.panel()));
        for (var layout : java.util.List.of(normal, small, FilterScreenLayout.fit(320,360))) {
            assertFalse(layout.nodeSelector().intersects(layout.addButton()));
            assertFalse(layout.clearButton().intersects(layout.confirmButton()));
            assertTrue(layout.status().bottom() <= layout.actionBar().top());
            for (int row = 0; row < FilterScreenLayout.ROWS; row++) {
                var line = layout.row(row); var remove = layout.removeButton(row);
                assertEquals(line.top() + 1, remove.top());
                assertTrue(remove.bottom() <= line.bottom());
                assertTrue(layout.rowLabel(row).right() < remove.left());
                assertTrue(layout.rowIcon(row).right() < layout.rowLabel(row).left());
                assertEquals(row, layout.listRowAt(line.left(), line.top()));
                assertEquals(-1, layout.listRowAt(line.right(), line.top()));
                var point = layout.toScreen(remove.left() + 2, remove.top() + 2);
                var local = layout.toLocal(point.x(), point.y());
                assertEquals(remove.left() + 2, local.x(), 1e-8);
                assertEquals(remove.top() + 2, local.y(), 1e-8);
                assertTrue(remove.contains(local.x(),local.y()));
                assertFalse(remove.contains(remove.right(),remove.top()));
            }
            assertEquals(-1, layout.listRowAt(layout.selectedList().left(),layout.selectedList().bottom()));
            assertEquals(7, layout.localDelta(7 * layout.transform().scale()), 1e-8);
            var exact = layout.toScreen(layout.previewOccupation());
            var pixels = layout.screenPixels(layout.previewOccupation());
            assertTrue(pixels.left() <= exact.left() && pixels.top() <= exact.top());
            assertTrue(pixels.right() >= exact.right() && pixels.bottom() >= exact.bottom());
        }
    }
}
