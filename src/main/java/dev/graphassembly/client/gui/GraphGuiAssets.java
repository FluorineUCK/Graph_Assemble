package dev.graphassembly.client.gui;

import java.util.Set;

/** Stable asset contract and atomically published snapshot; no client classes needed to test it. */
public final class GraphGuiAssets {
    public static final String ATLAS_PNG = "textures/gui/graph_gui.png";
    public static final String CANVAS_BACKGROUND_PNG = "textures/gui/canvas_background.png";
    public static final int CANVAS_TILE_SIZE = 16;
    public static final int ATLAS_WIDTH = 1024, ATLAS_HEIGHT = 512;
    private GraphGuiAssets() {}
    public enum Fallback { SOLID, FILTER_PANEL, FILTER_CHOICE, JEI_SLOT, ADD, TRASH, CONFIRM, PLANE, SPHERE }
    public enum Sprite {
        // Source rectangles follow the artist's rearranged PNG; screen bounds remain in the callers.
        // The shaded frame reaches 11px at the bottom; keep all of it outside the stretched center.
        GRAPH_PANEL("backgrounds/graph_panel", 0, 0, 320, 224, 11, 0xFF132029),
        FILTER_PANEL("backgrounds/filter_panel/header", 336, 0, 232, 95, 0, Fallback.FILTER_PANEL),
        FILTER_PANEL_UPPER("backgrounds/filter_panel/upper", 336, 95, 232, 20, 0, Fallback.FILTER_PANEL),
        FILTER_PANEL_MIDDLE("backgrounds/filter_panel/middle", 336, 116, 232, 20, 0, Fallback.FILTER_PANEL),
        FILTER_PANEL_LOWER("backgrounds/filter_panel/lower", 336, 137, 232, 19, 0, Fallback.FILTER_PANEL),
        FILTER_PANEL_FOOTER("backgrounds/filter_panel/footer", 336, 156, 241, 30, 0, Fallback.FILTER_PANEL),
        // Retain full 16px cells, including transparent padding for Create's +(1,1) inset.
        ADD("icons/add", 592, 16, 16, 16, 0, Fallback.ADD),
        REMOVE("icons/remove", 608, 16, 16, 16, 0, Fallback.TRASH),
        CLEAR("icons/clear", 608, 16, 16, 16, 0, Fallback.TRASH),
        CONFIRM("icons/confirm", 624, 16, 16, 16, 0, Fallback.CONFIRM),
        SPHERE("icons/sphere", 640, 16, 16, 16, 0, Fallback.SPHERE),
        PLANE("icons/plane", 656, 16, 16, 16, 0, Fallback.PLANE),
        FILTER_CHOICE("widgets/filter_choice", 592, 47, 115, 18, 8, Fallback.FILTER_CHOICE),
        FILTER_ICON_SLOT("widgets/filter_icon_slot", 351, 33, 18, 18, 0, Fallback.JEI_SLOT);

        private final String path;
        private final int x, y, width, height, border, color;
        private final Fallback fallback;
        Sprite(String path, int x, int y, int width, int height, int border, int color) {
            this(path, x, y, width, height, border, color, Fallback.SOLID);
        }
        Sprite(String path, int x, int y, int width, int height, int border, Fallback fallback) {
            this(path, x, y, width, height, border, 0, fallback);
        }
        Sprite(String path, int x, int y, int width, int height, int border, int color, Fallback fallback) {
            this.path = path; this.x = x; this.y = y; this.width = width; this.height = height;
            this.border = border; this.color = color; this.fallback = fallback;
        }
        public String path() { return path; }
        public String png() { return ATLAS_PNG; }
        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }
        public int border() { return border; }
        public int color() { return color; }
        public Fallback fallback() { return fallback; }
    }

    public record Snapshot(Set<Sprite> available, boolean canvasBackground) {
        public Snapshot(Set<Sprite> available) { this(available, false); }
        public Snapshot { available = Set.copyOf(available); }
    }
    private static volatile Snapshot current = new Snapshot(Set.of());
    public static Snapshot current() { return current; }
    static void publish(Snapshot snapshot) { current = snapshot; }

    public static boolean matchesAtlasSize(int width, int height) {
        return width >= ATLAS_WIDTH && width % ATLAS_WIDTH == 0
            && (long)height * ATLAS_WIDTH == (long)width * ATLAS_HEIGHT;
    }

}
