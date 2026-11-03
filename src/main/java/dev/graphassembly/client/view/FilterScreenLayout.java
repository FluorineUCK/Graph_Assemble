package dev.graphassembly.client.view;

import dev.graphassembly.client.view.NodePresentation.Bounds;
import dev.graphassembly.client.view.NodePresentation.Transform;
import java.util.List;
import java.util.ArrayList;

/** Logical panel geometry plus one parent transform shared by drawing, input and exclusions. */
public record FilterScreenLayout(int screenWidth, int screenHeight, int panelWidth, Transform transform) {
    public static final int HEIGHT = 226, ROWS = 3, ROW_HEIGHT = 20;
    public static final int MARGIN = 8, PREVIEW_WIDTH = 80, PREVIEW_BOTTOM = 12;
    public record Preview(double x, double y, double z, double scale) {}
    public record Point(double x, double y) {}

    public static FilterScreenLayout fit(int width, int height) {
        if (width <= 2 * MARGIN || height <= 2 * MARGIN)
            throw new IllegalArgumentException("Screen must have space inside its safety margin");
        // The filter atlas bakes controls into a 241px panel. Shrink the whole
        // interface, never the panel independently of its input coordinates.
        int panel = 241;
        double logicalWidth = panel + PREVIEW_WIDTH, logicalHeight = HEIGHT + PREVIEW_BOTTOM;
        double scale = Math.min(1, Math.min((width - 2.0 * MARGIN) / logicalWidth, (height - 2.0 * MARGIN) / logicalHeight));
        return new FilterScreenLayout(width, height, panel,
            new Transform((width - logicalWidth * scale) / 2, (height - logicalHeight * scale) / 2, scale));
    }
    public Bounds panel() { return new Bounds(0, 0, panelWidth, HEIGHT); }
    public Bounds panelHeader() { return new Bounds(0, 0, 232, 95); }
    public Bounds panelUpper() { return new Bounds(0, 95, 232, 115); }
    public List<Bounds> panelMiddleTiles() {
        var tiles = new ArrayList<Bounds>();
        for (int y = 115; y < 177; y += 20) tiles.add(new Bounds(0, y, 232, Math.min(y + 20, 177)));
        return List.copyOf(tiles);
    }
    public Bounds panelLower() { return new Bounds(0, 177, 232, 196); }
    public Bounds panelFooter() { return new Bounds(0, 196, panelWidth, HEIGHT); }
    public Preview preview() { return new Preview(panelWidth + 8, HEIGHT - 52, -200, 4); }
    public Bounds nativeExtraArea() { return new Bounds(panelWidth, HEIGHT - 40, panelWidth + 80, HEIGHT + 8); }
    public Bounds previewOccupation() { return new Bounds(panelWidth, HEIGHT - 52, panelWidth + PREVIEW_WIDTH, HEIGHT + PREVIEW_BOTTOM); }
    public Bounds occupation() { return new Bounds(0, 0, panelWidth + PREVIEW_WIDTH, HEIGHT + PREVIEW_BOTTOM); }
    /** Reserve the entire interface for sidebars whose search/navigation ignores extra exclusions. */
    public Bounds screenAreaForSidebar(int minimumSidebarWidth) {
        var area = screenPixels(occupation());
        return screenWidth - area.right() >= minimumSidebarWidth ? area : null;
    }
    /** No cached coordinates survive closing or replacing the active filter layout. */
    public static List<Bounds> screenExtraAreas(FilterScreenLayout activeFilter) {
        return activeFilter == null ? List.of() : List.of(activeFilter.screenPixels(activeFilter.nativeExtraArea()),
            activeFilter.screenPixels(activeFilter.previewOccupation()));
    }
    public Bounds title() { return new Bounds(0, 4, panelWidth - 8, 18); }
    public Bounds graphTitle() { return new Bounds(16, 23, panelWidth - 16, 33); }
    public Bounds graphIcon() { return new Bounds(16, 35, 32, 51); }
    public Bounds graphSelector() { return new Bounds(38, 33, 224, 51); }
    public Bounds nodeTitle() { return new Bounds(16, 53, panelWidth - 16, 63); }
    public Bounds nodeIcon() { return new Bounds(16, 64, 32, 80); }
    public Bounds nodeSelector() { return new Bounds(38, 63, 173, 81); }
    public Bounds choiceLabel(Bounds selector) { return new Bounds(selector.left() + 6, selector.top() + 5, selector.right() - 10, selector.bottom()); }
    public Bounds addButton() { return new Bounds(179, 63, 197, 81); }
    public Bounds addInvertedButton() { return new Bounds(197, 63, 215, 81); }
    public Bounds selectedTitle() { return new Bounds(16, 98, panelWidth - 16, 108); }
    public Bounds selectedList() { return new Bounds(14, 111, panelWidth - 14, 171); }
    public Bounds row(int row) {
        if (row < 0 || row >= ROWS) throw new IndexOutOfBoundsException(row);
        return new Bounds(selectedList().left(), 111 + row * ROW_HEIGHT, selectedList().right(), 111 + (row + 1) * ROW_HEIGHT);
    }
    public Bounds removeButton(int row) { return new Bounds(208, row(row).top() + 1, 226, row(row).top() + 19); }
    public Bounds rowIconFrame(int row) { return new Bounds(15, row(row).top() + 1, 33, row(row).top() + 19); }
    public Bounds rowTextFrame(int row) { return new Bounds(36, row(row).top() + 1, removeButton(row).left() - 4, row(row).top() + 19); }
    public Bounds rowIcon(int row) { return new Bounds(16, row(row).top() + 2, 32, row(row).top() + 18); }
    public Bounds rowLabel(int row) { return new Bounds(rowTextFrame(row).left() + 8, row(row).top() + 5, rowTextFrame(row).right() - 8, row(row).top() + 15); }
    public Bounds rowDescription(int row) { return new Bounds(row(row).left(), row(row).top(), rowTextFrame(row).right(), row(row).bottom()); }
    public Bounds status() { return new Bounds(14, 179, panelWidth - 14, 191); }
    public Bounds actionBar() { return new Bounds(0, 196, panelWidth, HEIGHT); }
    public Bounds modeButton(int mode) {
        if (mode < 0 || mode >= 3) throw new IndexOutOfBoundsException(mode);
        int left = 15 + mode * 18;
        return new Bounds(left, 202, left + 18, 220);
    }
    public Bounds clearButton() { return new Bounds(179, 202, 197, 220); }
    public Bounds confirmButton() { return new Bounds(208, 202, 226, 220); }
    public Bounds rangeLabel() { return new Bounds(80, 205, panelWidth - 70, 215); }
    public int listRowAt(double x, double y) { return selectedList().contains(x,y) ? (int)((y - selectedList().top()) / ROW_HEIGHT) : -1; }
    public Point toScreen(double x, double y) { return new Point(transform.x() + x * transform.scale(), transform.y() + y * transform.scale()); }
    public Point toLocal(double x, double y) { return new Point(transform.localX(x), transform.localY(y)); }
    public double localDelta(double delta) { return delta / transform.scale(); }
    public Bounds toScreen(Bounds b) {
        return new Bounds(transform.x() + b.left() * transform.scale(), transform.y() + b.top() * transform.scale(),
            transform.x() + b.right() * transform.scale(), transform.y() + b.bottom() * transform.scale());
    }
    /** Conservative integer screen coverage for focus navigation and JEI; hit testing stays exact. */
    public Bounds screenPixels(Bounds b) {
        var screen = toScreen(b);
        return new Bounds(Math.floor(screen.left()), Math.floor(screen.top()), Math.ceil(screen.right()), Math.ceil(screen.bottom()));
    }
}
