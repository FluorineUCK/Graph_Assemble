package dev.graphassembly.client.view;

import dev.graphassembly.client.view.NodePresentation.Bounds;
import java.util.ArrayList;
import java.util.List;

/** One source of footer drawing and input rectangles, in unscaled recipe GUI pixels. */
public record SettlementFooterLayout(int width, int footerY, Bounds input, Bounds success,
                                     Bounds failure, Bounds overview, Bounds selector, Mode mode) {
    public enum Mode { NORMAL, AGGREGATE, RESULTS_ONLY }
    // 11px shaded PNG frame plus 3px breathing room. Shared by both popup types.
    public static final int POPUP_INSET = 14;

    public record Overflow(int visibleCount, int hiddenCount) {}

    /** A zero-width label has no label-to-slot gap. Slots are never scaled to fit. */
    public static int capacity(int availableWidth, int labelWidth) {
        return capacity(availableWidth, labelWidth, 18);
    }
    public static int capacity(int availableWidth, int labelWidth, int cellWidth) {
        int remaining = availableWidth - Math.max(0, labelWidth) - (labelWidth > 0 ? 4 : 0);
        return Math.max(0, (remaining + 2) / (Math.max(18, cellWidth) + 2));
    }

    public static Overflow overflow(int candidateCount, int capacity) {
        int count = Math.max(0, candidateCount), positions = Math.max(0, capacity);
        int visible = count <= positions ? count : Math.max(0, positions - 1);
        return new Overflow(visible, count - visible);
    }

    /** Includes the final overflow/navigation rectangle when candidates exceed capacity. */
    public List<Bounds> slots(Bounds column, int labelWidth, int candidateCount) {
        return slots(column, labelWidth, candidateCount, 18);
    }
    public List<Bounds> slots(Bounds column, int labelWidth, int candidateCount, int cellWidth) {
        cellWidth = Math.max(18, cellWidth);
        int count = Math.min(Math.max(0, candidateCount), capacity((int) (column.right() - column.left()), labelWidth, cellWidth));
        int x = (int) column.left() + Math.max(0, labelWidth) + (labelWidth > 0 ? 4 : 0);
        List<Bounds> slots = new ArrayList<>();
        for (int index = 0; index < count; index++) slots.add(bounds(x + index * (cellWidth + 2), footerY + 2, cellWidth, 18));
        return List.copyOf(slots);
    }

    /** Local row/cell indices are viewport-relative, not absolute list indices. */
    public record Popup(Bounds bounds, Bounds content, int columns, int visibleRows,
                        int pageCapacity, int pages, Bounds previous, Bounds next, Bounds scrollbar, int cellWidth) {
        public Bounds close() {
            double right = bounds.right() - Math.min(POPUP_INSET, (bounds.right() - bounds.left()) / 2);
            double width = Math.min(18, Math.max(0, right - content.left()));
            double top = Math.min(bounds.bottom(), bounds.top() + POPUP_INSET);
            return new Bounds(right - width, top, right, Math.min(bounds.bottom(), top + 18));
        }
        public Bounds title(boolean withBack) {
            var close = close();
            double left = Math.min(close.left(), content.left() + (withBack ? 46 : 0));
            return new Bounds(left, close.top(), Math.max(left, close.left() - 6), close.bottom());
        }
        public Bounds back() { return new Bounds(content.left(), close().top(), Math.min(content.left() + 42, close().left()), close().bottom()); }
        public Bounds pageLabel(int textWidth) {
            double left = previous.right() + 4, right = Math.max(left, next.left() - 4);
            double width = Math.min(Math.max(0, textWidth), right - left);
            double x = Math.floor(left + (right - left - width) / 2);
            return new Bounds(x, previous.top(), x + width, previous.bottom());
        }
        public Bounds row(int index) {
            if (index < 0 || index >= visibleRows) throw new IndexOutOfBoundsException(index);
            return new Bounds(content.left(), content.top() + index * 20,
                content.right(), content.top() + index * 20 + 18);
        }
        /** Right-aligned native 16px preview with its own slot-sized column. */
        public Bounds rowPreview(int index) {
            var row = row(index);
            return new Bounds(Math.max(row.left(), row.right() - 22), row.top(), Math.max(row.left(), row.right() - 4), row.bottom());
        }
        public Bounds rowLabel(int index) {
            var row = row(index);
            double left = Math.min(row.right(), row.left() + 16);
            return new Bounds(left, row.top(), Math.max(left, rowPreview(index).left() - 6), row.bottom());
        }

        public Bounds cell(int index) {
            if (index < 0 || index >= pageCapacity) throw new IndexOutOfBoundsException(index);
            int padding = Math.max(0, ((int)(content.right() - content.left()) - (columns * (cellWidth + 2) - 2)) / 2);
            int x = (int) content.left() + padding + index % columns * (cellWidth + 2);
            int y = (int) content.top() + index / columns * 20;
            return SettlementFooterLayout.bounds(x, y, cellWidth, 18);
        }
        public Bounds scrollThumb(int count, int offset) {
            double track = scrollbar.bottom() - scrollbar.top();
            if (scrollbar.right() <= scrollbar.left() || track <= 0) return scrollbar;
            double size = Math.min(track, Math.max(9, Math.floor(track * visibleRows / Math.max(1, count))));
            int max = Math.max(0, count - visibleRows);
            double y = scrollbar.top() + Math.round((track - size) * Math.clamp(offset, 0, max) / Math.max(1, max));
            return new Bounds(scrollbar.left(), y, scrollbar.right(), y + size);
        }
        public int scrollOffset(int count, double mouseY, double grabOffset) {
            var thumb = scrollThumb(count, 0);
            double travel = scrollbar.bottom() - scrollbar.top() - (thumb.bottom() - thumb.top());
            int max = Math.max(0, count - visibleRows);
            return travel <= 0 ? 0 : (int)Math.round(Math.clamp((mouseY - scrollbar.top() - grabOffset) / travel, 0, 1) * max);
        }
    }

    /** Includes the "all exits" item in optionCount. Extra options scroll within these rows. */
    public Popup selectorPopup(int optionCount) {
        return popup(selector, Math.max(0, optionCount), true, 18, 0);
    }

    public Popup candidatePopup(Bounds anchor, int candidateCount) {
        return candidatePopup(anchor, candidateCount, 18, 96);
    }
    /** Measured tile/header widths; native item icons remain 16px regardless of tile width. */
    public Popup candidatePopup(Bounds anchor, int candidateCount, int cellWidth, int minimumWidth) {
        return popup(anchor, Math.max(0, candidateCount), false, Math.max(18, cellWidth), minimumWidth);
    }

    private Popup popup(Bounds anchor, int count, boolean selectorList, int cellWidth, int minimumWidth) {
        int maximumWidth = Math.max(0, width - 8);
        int popupWidth = selectorList ? Math.min(240, maximumWidth) : maximumWidth;
        int maxHeight = Math.max(0, footerY + 22 - 8);
        int aboveHeight = Math.max(0, (int) anchor.top() - 6);
        int minimum = POPUP_INSET * 2 + 24 + 18 + (selectorList ? 0 : 24);
        if (aboveHeight >= minimum) maxHeight = Math.min(maxHeight, aboveHeight);
        int insetX = Math.min(POPUP_INSET, popupWidth / 2);
        int innerWidth = Math.max(0, popupWidth - insetX * 2);
        int overhead = POPUP_INSET * 2 + 24;
        int columns = selectorList ? 1 : capacity(innerWidth, 0, cellWidth);
        int rows = Math.min(selectorList ? 6 : Integer.MAX_VALUE,
            Math.max(0, (maxHeight - overhead + 2) / 20));
        // Decide overflow using the maximum available width AND height, before reserving navigation.
        boolean paged = !selectorList && (long) columns * rows < count;
        if (paged) rows = Math.max(0, (maxHeight - overhead - 24 + 2) / 20);
        if (!selectorList && !paged) {
            int neededColumns = rows == 0 ? 0 : (int)(((long)count + rows - 1) / rows);
            int balancedColumns = (int)Math.ceil(Math.sqrt(count * 1.5));
            columns = Math.min(columns, Math.min(count, Math.max(neededColumns, Math.max(3, balancedColumns))));
            int gridWidth = Math.max(0, columns * (cellWidth + 2) - 2);
            popupWidth = Math.min(maximumWidth, Math.max(Math.max(0, minimumWidth), POPUP_INSET * 2 + gridWidth));
            insetX = Math.min(POPUP_INSET, popupWidth / 2);
            innerWidth = Math.max(0, popupWidth - insetX * 2);
        }
        if (columns == 0) rows = 0;
        if (selectorList) rows = Math.min(rows, count);
        else if (columns > 0) rows = Math.min(rows, (int) Math.min(Integer.MAX_VALUE, ((long) count + columns - 1) / columns));
        int contentHeight = Math.max(0, rows * 20 - 2);
        int height = Math.min(maxHeight, overhead + contentHeight + (paged ? 24 : 0));
        int x = Math.clamp((int) anchor.right() - popupWidth, Math.min(4, width), Math.max(Math.min(4, width), width - 4 - popupWidth));
        int y = Math.clamp((int) anchor.top() - 2 - height, Math.min(4, footerY + 22), Math.max(4, footerY + 18 - height));
        Bounds outer = bounds(x, y, popupWidth, height);
        boolean scrolling = selectorList && count > rows && rows > 0 && innerWidth >= 30;
        int insetY = Math.min(POPUP_INSET + 24, height);
        int contentWidth = Math.max(0, innerWidth - (scrolling ? 11 : 0));
        Bounds content = bounds(x + insetX, y + insetY, contentWidth, contentHeight);
        Bounds empty = bounds(x, y, 0, 0);
        boolean pageButtons = paged && innerWidth >= 42 && columns > 0 && rows > 0;
        Bounds previous = pageButtons
            ? bounds(x + insetX, (int) content.bottom() + 6, 18, 18) : empty;
        Bounds next = pageButtons
            ? bounds(x + popupWidth - insetX - 18, (int) content.bottom() + 6, 18, 18) : empty;
        int pageCapacity = columns * rows;
        int pages = pageCapacity == 0 ? 0 : (int) Math.max(1, ((long) count + pageCapacity - 1) / pageCapacity);
        Bounds scrollbar = scrolling ? bounds(x + popupWidth - insetX - 5, (int)content.top(), 5, contentHeight) : empty;
        return new Popup(outer, content, columns, rows, pageCapacity, pages, previous, next, scrollbar, cellWidth);
    }

    public static SettlementFooterLayout of(int width, int footerY) {
        if (width < 0 || footerY < 0) throw new IllegalArgumentException("Negative recipe dimensions");
        if (width < 122) {
            int x = Math.min(4, width), size = Math.min(18, Math.max(0, width - x - Math.min(4, width - x)));
            var empty = bounds(x, footerY, 0, 0);
            return new SettlementFooterLayout(width, footerY, empty, empty, empty,
                bounds(x, footerY + 2, size, 18), empty, Mode.RESULTS_ONLY);
        }
        if (width < 250) return new SettlementFooterLayout(width, footerY,
            bounds(4, footerY, 18, 22), bounds(26, footerY, 18, 22), bounds(48, footerY, 18, 22),
            bounds(26, footerY, 18, 22), bounds(width - 52, footerY + 2, 48, 18), Mode.AGGREGATE);
        double t = Math.clamp((width - 250) / 70.0, 0, 1);
        int inputWidth = (int) Math.round(46 + 14 * t);
        int failureWidth = (int) Math.round(76 + 4 * t);
        int selectorWidth = (int) Math.round(48 + 28 * t);
        int selectorX = width - 4 - selectorWidth;
        int failureX = selectorX - 4 - failureWidth;
        int successX = 4 + inputWidth + 4;
        return new SettlementFooterLayout(width, footerY,
            bounds(4, footerY, inputWidth, 22), bounds(successX, footerY, failureX - 4 - successX, 22),
            bounds(failureX, footerY, failureWidth, 22), bounds(successX, footerY, selectorX - 4 - successX, 22),
            bounds(selectorX, footerY + 2, selectorWidth, 18), Mode.NORMAL);
    }

    private static Bounds bounds(int x, int y, int width, int height) {
        return new Bounds(x, y, x + width, y + height);
    }
}
