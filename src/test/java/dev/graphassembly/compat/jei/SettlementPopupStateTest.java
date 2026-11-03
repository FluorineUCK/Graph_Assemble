package dev.graphassembly.compat.jei;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettlementPopupStateTest {
    @Test void nativeThumbDragAndTrackClickScrollWithoutSelectingOrClosingAnExit() {
        var layout = dev.graphassembly.client.view.SettlementFooterLayout.of(320, 228).selectorPopup(20);
        var state = new SettlementPopupState();
        state.open(SettlementPopupState.Kind.SELECTOR, 20, layout.visibleRows(), 0);
        var track = layout.scrollbar();
        assertFalse(state.beginScrollbar(layout, layout.row(0).left(), layout.row(0).top()));
        assertTrue(state.beginScrollbar(layout, track.left() + 2, track.top() + 3));
        assertEquals(0, state.offset, "The simulated press only begins a gesture");
        state.dragScrollbar(layout, track.bottom() + 20);
        assertEquals(20 - layout.visibleRows(), state.offset);
        state.endScrollbar(layout, track.bottom() + 20);
        assertFalse(state.scrolling());
        assertTrue(state.open());
        assertTrue(state.beginScrollbar(layout, track.left() + 2, track.top()));
        state.endScrollbar(layout, track.top());
        assertEquals(0, state.offset);
        state.close();
        assertFalse(state.scrolling());
    }
    @Test void scrollbarPositionClampsAndKeepsKeyboardCursorInsideVisibleRows() {
        var state = new SettlementPopupState();
        state.open(SettlementPopupState.Kind.SELECTOR, 20, 6, 0);
        state.scrollTo(10);
        assertEquals(10, state.offset);
        assertEquals(10, state.cursor);
        state.scrollTo(100);
        assertEquals(14, state.offset);
        state.move(1);
        assertEquals(15, state.cursor);
        state.scrollTo(-100);
        assertEquals(0, state.offset);
        assertEquals(5, state.cursor);
    }
    @Test void selectorKeyboardWrapsAndScrollsWithoutEscapingTheBoundedViewport() {
        var state = new SettlementPopupState();
        state.open(SettlementPopupState.Kind.SELECTOR, 9, 6, 8);
        assertEquals(3, state.offset);
        state.move(1);
        assertEquals(0, state.cursor); assertEquals(0, state.offset);
        state.move(-1);
        assertEquals(8, state.cursor); assertEquals(3, state.offset);
        state.scroll(100); assertEquals(3, state.offset);
        state.scroll(-100); assertEquals(0, state.offset);
        state.close(); assertFalse(state.open());
    }
    @Test void candidatePagesRetainEveryEntryAndClampAtTheLastPage() {
        var state = new SettlementPopupState();
        state.open(SettlementPopupState.Kind.CANDIDATES, 136, 77, 0);
        state.move(80);
        assertEquals(80, state.cursor); assertEquals(77, state.offset);
        state.scroll(1); assertEquals(77, state.offset);
        state.scroll(-1); assertEquals(0, state.offset); assertEquals(0, state.cursor);
        state.move(-1); assertEquals(135, state.cursor); assertEquals(77, state.offset);
    }

    @Test void draggingAwayAndBackDoesNotActivateARow() {
        var state = new SettlementPopupState();
        state.open(SettlementPopupState.Kind.SELECTOR, 9, 4, 2);
        state.press(3, 5, 5);
        state.moved(50, 5);
        state.moved(5, 5);
        assertEquals(-1, state.release(3, 5, 5));
        assertTrue(state.open());
    }

    @Test void outsidePressClosesOnlyOnCommitAndNeverActivatesUnderlyingControl() {
        var state = new SettlementPopupState();
        state.open(SettlementPopupState.Kind.SELECTOR, 9, 4, 2);
        state.press(-1, 5, 5);
        assertTrue(state.open());
        assertEquals(-1, state.release(-1, 5, 5));
        assertFalse(state.open());
        state.open(SettlementPopupState.Kind.SELECTOR, 9, 4, 2);
        state.press(3, 5, 5);
        assertEquals(-1, state.release(3, 14, 5));
        assertTrue(state.open(), "Dragging a row is not a selection");
    }
}
