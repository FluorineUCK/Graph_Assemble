package dev.graphassembly.compat.jei;

import dev.graphassembly.client.view.SettlementFooterLayout.Popup;

/** Pure popup gesture/navigation state; simulated presses never change visible selection. */
final class SettlementPopupState {
    enum Kind { SELECTOR, CANDIDATES }
    Kind kind;
    int count, capacity, cursor, offset;
    private int pressed = -2;
    private double pressX, pressY;
    private boolean dragged;
    private double scrollGrab = -1;
    boolean open() { return kind != null; }
    void open(Kind kind, int count, int capacity, int selected) {
        scrollGrab = -1;
        this.kind = kind; this.count = count; this.capacity = Math.max(1, capacity);
        cursor = Math.clamp(selected, 0, Math.max(0, count - 1));
        offset = kind == Kind.SELECTOR ? Math.max(0, cursor - this.capacity + 1) : cursor / this.capacity * this.capacity;
    }
    void close() { kind = null; pressed = -2; scrollGrab = -1; }
    boolean scrolling() { return scrollGrab >= 0; }
    boolean beginScrollbar(Popup layout, double x, double y) {
        if (kind != Kind.SELECTOR || !layout.scrollbar().contains(x, y)) return false;
        var thumb = layout.scrollThumb(count, offset);
        scrollGrab = thumb.contains(x, y) ? y - thumb.top() : (thumb.bottom() - thumb.top()) / 2;
        pressed = -2;
        return true;
    }
    void dragScrollbar(Popup layout, double y) {
        if (scrolling()) scrollTo(layout.scrollOffset(count, y, scrollGrab));
    }
    void endScrollbar(Popup layout, double y) { dragScrollbar(layout, y); scrollGrab = -1; }
    void scrollTo(int position) {
        if (kind != Kind.SELECTOR) return;
        offset = Math.clamp(position, 0, Math.max(0, count - capacity));
        cursor = Math.clamp(cursor, offset, Math.min(Math.max(0, count - 1), offset + capacity - 1));
    }
    void press(int target, double x, double y) { pressed = target; pressX = x; pressY = y; dragged = false; }
    void moved(double x, double y) { if (Math.hypot(x - pressX, y - pressY) > 4) dragged = true; }
    int release(int target, double x, double y) {
        boolean click = !dragged && pressed == target && Math.hypot(x - pressX, y - pressY) <= 4;
        pressed = -2;
        if (!click) return -1;
        if (target == -1) { close(); return -1; }
        return target;
    }
    void move(int amount) {
        cursor = Math.floorMod(cursor + amount, Math.max(1, count));
        if (kind == Kind.SELECTOR) offset = Math.clamp(offset, Math.max(0, cursor - capacity + 1), cursor);
        else offset = cursor / capacity * capacity;
    }
    void scroll(int amount) {
        if (kind == Kind.SELECTOR) scrollTo(offset + amount);
        else { offset = Math.clamp(offset + amount * capacity, 0, Math.max(0, (count - 1) / capacity * capacity)); cursor = offset; }
    }
}
