package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettlementFooterLayoutTest {
    @Test void fewCandidatesShrinkTheFrameAndLargerListsUseAllAvailableSpaceBeforePaging() {
        var layout = SettlementFooterLayout.of(320, 228);
        var small = layout.candidatePopup(layout.overview(), 3);
        assertTrue(small.bounds().right() - small.bounds().left() < 160);
        assertEquals(1, small.visibleRows());
        assertEquals(1, small.pages());
        var large = layout.candidatePopup(layout.overview(), 100);
        assertEquals(1, large.pages(), "100 native cells fit when the popup is allowed to grow beyond 240px");
        assertTrue(large.pageCapacity() >= 100);
        assertEquals(0, large.previous().right() - large.previous().left(), "No empty pagination toolbar");
    }
    @Test void quantityTilesFitTheirMeasuredLabelsAndPageOnlyAboveTheMaximumCapacity() {
        var layout = SettlementFooterLayout.of(320, 228);
        var small = layout.candidatePopup(layout.overview(), 3, 28, 96);
        assertEquals(3, small.columns());
        assertEquals(1, small.visibleRows());
        assertEquals(116, small.bounds().right() - small.bounds().left());
        assertEquals(28, small.cell(0).right() - small.cell(0).left());
        assertEquals(2, small.cell(1).left() - small.cell(0).right());
        var full = layout.candidatePopup(layout.overview(), 72, 28, 96);
        assertEquals(1, full.pages(), "9 columns x 8 rows fit without a navigation bar");
        var overflow = layout.candidatePopup(layout.overview(), 73, 28, 96);
        assertTrue(overflow.pages() > 1);
        assertEquals(312, overflow.bounds().right() - overflow.bounds().left());
        assertEquals(9, overflow.columns());
        assertEquals(7, overflow.visibleRows(), "Only actual overflow reserves space for page controls");
        assertTrue(overflow.cell(overflow.pageCapacity() - 1).bottom() <= overflow.content().bottom());
    }
    @Test void wideFooterTilesAndMeasuredHeadersStayWithinTheirColumnsAndViewport() {
        var layout = SettlementFooterLayout.of(320, 228);
        var slots = layout.slots(layout.overview(), 24, 3, 28);
        assertEquals(3, slots.size());
        for (var slot : slots) {
            assertEquals(28, slot.right() - slot.left());
            assertTrue(slot.right() <= layout.overview().right());
        }
        var popup = layout.candidatePopup(layout.overview(), 3, 28, 190);
        assertTrue(popup.title(false).right() - popup.title(false).left() >= 138);
        for (int width : new int[]{100, 140, 250, 320}) for (int height : new int[]{90, 140, 228}) {
            var sized = SettlementFooterLayout.of(width, height);
            var grid = sized.candidatePopup(sized.overview(), 140, 48, 190);
            assertTrue(grid.bounds().left() >= 4 && grid.bounds().right() <= width - 4);
            assertTrue(grid.bounds().top() >= 4 && grid.bounds().bottom() <= height + 22);
            for (int i = 0; i < grid.pageCapacity(); i++) {
                assertTrue(grid.cell(i).right() <= grid.content().right());
                assertTrue(grid.cell(i).bottom() <= grid.content().bottom());
            }
        }
    }
    @Test void pageIndicatorIsCenteredBetweenEqualNativeNavigationButtons() {
        for (int width : new int[]{250, 320}) {
            var layout = SettlementFooterLayout.of(width, 178);
            var popup = layout.candidatePopup(layout.overview(), 136);
            var label = popup.pageLabel(18);
            assertEquals((popup.bounds().left() + popup.bounds().right()) / 2,
                (label.left() + label.right()) / 2, .5);
            assertTrue(label.left() > popup.previous().right());
            assertTrue(label.right() < popup.next().left());
            assertEquals(popup.previous().top(), label.top());
        }
    }
    @Test void headerRowsAndNativeScrollbarHaveDisjointAlignedGeometry() {
        for (int width : new int[]{250, 320}) for (int height : new int[]{90, 178, 228}) {
            var layout = SettlementFooterLayout.of(width, height);
            var shortList = layout.selectorPopup(1);
            assertEquals(0, shortList.scrollbar().right() - shortList.scrollbar().left());
            var popup = layout.selectorPopup(100);
            assertEquals(18, popup.close().right() - popup.close().left());
            assertEquals(popup.content().left(), popup.title(false).left());
            assertEquals(popup.bounds().right() - 14, popup.close().right());
            assertTrue(popup.close().bottom() + 6 <= popup.content().top());
            assertEquals(5, popup.scrollbar().right() - popup.scrollbar().left());
            assertTrue(popup.content().right() + 6 <= popup.scrollbar().left());
            for (int row = 0; row < popup.visibleRows(); row++) {
                assertTrue(popup.rowLabel(row).right() < popup.rowPreview(row).left());
                assertTrue(popup.rowPreview(row).right() <= popup.row(row).right() - 4);
            }
            var top = popup.scrollThumb(100, 0);
            var bottom = popup.scrollThumb(100, 100);
            assertEquals(popup.scrollbar().top(), top.top());
            assertEquals(popup.scrollbar().bottom(), bottom.bottom());
            assertEquals(0, popup.scrollOffset(100, -1000, 0));
            assertEquals(100 - popup.visibleRows(), popup.scrollOffset(100, 1000, 0));
            assertEquals(Math.round((100 - popup.visibleRows()) / 2.0), popup.scrollOffset(100,
                (popup.scrollbar().top() + popup.scrollbar().bottom()) / 2, (top.bottom() - top.top()) / 2));
        }
    }
    @Test void bothPopupsKeepContentsOffTheShadedFrameAndCenterCandidateGrid() {
        for (int width : new int[]{250, 320}) {
            var layout = SettlementFooterLayout.of(width, 178);
            for (var popup : List.of(layout.selectorPopup(4), layout.candidatePopup(layout.success(), 136))) {
                assertTrue(popup.content().left() >= popup.bounds().left() + 12);
                assertTrue(popup.content().right() <= popup.bounds().right() - 12);
                assertTrue(popup.content().bottom() <= popup.bounds().bottom() - 12);
            }
            var grid = layout.candidatePopup(layout.success(), 136);
            double leftGap = grid.cell(0).left() - grid.content().left();
            double rightGap = grid.content().right() - grid.cell(grid.columns() - 1).right();
            assertEquals(leftGap, rightGap, 1);
        }
    }
    @Test void endpointColumnsFollowContractAndIntermediateWidthsStayDisjoint() {
        var standard = SettlementFooterLayout.of(320, 228);
        assertEquals(List.of(4.0, 68.0, 156.0, 240.0),
            List.of(standard.input().left(), standard.success().left(), standard.failure().left(), standard.selector().left()));
        var compact = SettlementFooterLayout.of(250, 228);
        assertEquals(List.of(4.0, 54.0, 118.0, 198.0),
            List.of(compact.input().left(), compact.success().left(), compact.failure().left(), compact.selector().left()));
        assertEquals(new NodePresentation.Bounds(68, 228, 236, 250), standard.overview());
        assertEquals(new NodePresentation.Bounds(54, 228, 194, 250), compact.overview());
        for (int width = 250; width <= 400; width++) {
            var layout = SettlementFooterLayout.of(width, 228);
            var areas = List.of(layout.input(), layout.success(), layout.failure(), layout.selector());
            for (int index = 0; index < areas.size(); index++) {
                var area = areas.get(index);
                assertTrue(area.left() >= 4 && area.right() <= width - 4);
                if (index > 0) assertTrue(areas.get(index - 1).right() + 4 <= area.left());
            }
        }
    }

    @Test void labelBudgetAndOverflowKeepOneExplicitExpandablePosition() {
        assertEquals(3, SettlementFooterLayout.capacity(84, 22));
        assertEquals(1, SettlementFooterLayout.capacity(46, 24));
        assertEquals(0, SettlementFooterLayout.capacity(18, 1));
        assertEquals(1, SettlementFooterLayout.capacity(18, 0));
        assertEquals(new SettlementFooterLayout.Overflow(2, 5), SettlementFooterLayout.overflow(7, 3));
        assertEquals(new SettlementFooterLayout.Overflow(0, 7), SettlementFooterLayout.overflow(7, 1));
        assertEquals(new SettlementFooterLayout.Overflow(3, 0), SettlementFooterLayout.overflow(3, 3));
        var layout = SettlementFooterLayout.of(250, 228);
        var slots = layout.slots(layout.overview(), 24, 8);
        assertEquals(5, slots.size());
        assertEquals(new NodePresentation.Bounds(82, 230, 100, 248), slots.getFirst());
        assertTrue(slots.getLast().right() <= layout.overview().right());
        for (int i = 1; i < slots.size(); i++) assertEquals(2, slots.get(i).left() - slots.get(i - 1).right());
    }

    @Test void sub250WidthsUseAggregatesAndVerySmallWidthsUseOnlyResultNavigation() {
        for (int width = 122; width < 250; width++) {
            var layout = SettlementFooterLayout.of(width, 228);
            assertEquals(SettlementFooterLayout.Mode.AGGREGATE, layout.mode());
            for (var area : List.of(layout.input(), layout.success(), layout.failure()))
                assertEquals(18, area.right() - area.left());
            assertEquals(48, layout.selector().right() - layout.selector().left());
            assertTrue(layout.failure().right() + 4 <= layout.selector().left());
        }
        for (int width = 0; width < 122; width++) {
            var layout = SettlementFooterLayout.of(width, 228);
            assertEquals(SettlementFooterLayout.Mode.RESULTS_ONLY, layout.mode());
            assertTrue(layout.overview().left() >= 0 && layout.overview().right() <= width);
            assertEquals(0, layout.selector().right() - layout.selector().left());
        }
    }

    @Test void popupRowsAndCandidatePagesStayInsideViewportAndUseSameHitRectangles() {
        var layout = SettlementFooterLayout.of(320, 228);
        var selector = layout.selectorPopup(4);
        assertEquals(new NodePresentation.Bounds(76, 98, 316, 228), selector.bounds());
        assertEquals(4, selector.visibleRows());
        assertEquals(6, layout.selectorPopup(100).visibleRows());
        assertEquals(18, selector.row(0).bottom() - selector.row(0).top());
        var candidates = layout.candidatePopup(layout.success(), 500);
        assertTrue(candidates.pages() > 1);
        assertTrue(candidates.pageCapacity() > 0);
        assertTrue(candidates.previous().right() <= candidates.next().left());
        assertTrue(candidates.cell(candidates.pageCapacity() - 1).bottom() <= candidates.content().bottom());
        assertEquals(18, candidates.cell(0).right() - candidates.cell(0).left());
        assertTrue(candidates.previous().top() >= candidates.content().bottom());
        for (int width : new int[]{250, 320}) for (int y : new int[]{90, 140, 228}) {
            var sized = SettlementFooterLayout.of(width, y);
            for (var popup : List.of(sized.selectorPopup(100), sized.candidatePopup(sized.input(), 500))) {
                assertTrue(popup.bounds().left() >= 4 && popup.bounds().right() <= width - 4);
                assertTrue(popup.bounds().top() >= 4 && popup.bounds().bottom() <= y + 22);
                assertTrue(popup.visibleRows() >= 1);
                assertTrue(popup.content().left() >= popup.bounds().left() + 4);
                assertTrue(popup.content().right() <= popup.bounds().right() - 4);
            }
        }
    }

    @Test void tinyPopupCannotExposeOverlappingPageControls() {
        for (int width = 0; width < 250; width++) {
            var layout = SettlementFooterLayout.of(width, 90);
            var popup = layout.candidatePopup(layout.overview(), 500);
            assertTrue(popup.bounds().left() >= 0 && popup.bounds().right() <= width);
            assertFalse(popup.previous().intersects(popup.next()), "Navigation must not overlap at width " + width);
            for (int index = 0; index < popup.pageCapacity(); index++) {
                var cell = popup.cell(index);
                assertTrue(cell.left() >= popup.bounds().left() && cell.right() <= popup.bounds().right());
            }
        }
    }
}
