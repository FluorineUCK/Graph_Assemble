package dev.graphassembly.client.view;

/** Pure display policy; never modifies graph execution state. */
public final class NodePresentation {
    private NodePresentation() {}
    private static final int MACHINE_BADGE_SIZE = 16;
    /** Native dynamic results have no statically truthful carrier/default-item tooltip. */
    public static boolean showStaticCarrier(dev.graphassembly.core.Node node) {
        return !dev.graphassembly.integration.PreviewRecipeData.dynamicCarrier(node);
    }
    public record Bounds(double left, double top, double right, double bottom) {
        public boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
        public boolean intersects(Bounds b) { return right > b.left && left < b.right && bottom > b.top && top < b.bottom; }
    }
    public record Surface(String id, double depth, boolean front, boolean expanded, Transform transform, double width, double height) {
        public Bounds bounds() {
            return new Bounds(transform.x(), transform.y(), transform.x() + width * transform.scale(), transform.y() + height * transform.scale());
        }
        /** Native local x of a reserved machine badge on an expanded preview. */
        public int machineBadgeX() { return (int)width - MACHINE_BADGE_SIZE; }
    }
    public static Surface expandedPreview(String id, double depth, boolean front, Transform nativeOrigin,
                                          double nativeWidth, double nativeHeight, boolean machineBadge) {
        return new Surface(id, depth, front, true, nativeOrigin,
            machineBadge ? nativeWidth + MACHINE_BADGE_SIZE : nativeWidth,
            machineBadge ? Math.max(nativeHeight, MACHINE_BADGE_SIZE) : nativeHeight);
    }
    public record PreviewMouse(int x, int y) {}
    /** Coordinates forwarded to the native layout for one rasterized preview. */
    public static PreviewMouse previewMouse(Surface surface, Surface hovered, double mouseX, double mouseY) {
        if (surface == null || !surface.equals(hovered)) return new PreviewMouse(-10000, -10000);
        var transform = surface.transform();
        return new PreviewMouse((int) transform.localX(mouseX), (int) transform.localY(mouseY));
    }
    public static Surface hit(java.util.List<Surface> nodes, Bounds viewport, double x, double y) {
        if (!viewport.contains(x, y)) return null;
        return nodes.stream().filter(n -> n.front() && n.bounds().contains(x, y))
            .max(java.util.Comparator.comparingDouble(Surface::depth).thenComparing(Surface::id)).orElse(null);
    }
    public record Hit(Surface node, dev.graphassembly.core.Edge edge) {}
    /** Shared canvas priority: the foremost node (including its slots) occludes edges. */
    public static Hit pick(java.util.List<Surface> nodes, java.util.List<GraphEdges.Path> edges,
                           Bounds viewport, double x, double y) {
        if (!viewport.contains(x, y)) return new Hit(null, null);
        Surface node = hit(nodes, viewport, x, y);
        return new Hit(node, node == null ? GraphEdges.hit(edges, x, y) : null);
    }
    public static Hit pickPrepared(java.util.List<Surface> nodes, GraphEdges.Prepared edges,
                                   Bounds viewport, double x, double y) {
        if (!viewport.contains(x, y)) return new Hit(null, null);
        Surface node = hit(nodes, viewport, x, y);
        return new Hit(node, node == null ? GraphEdges.hitPrepared(edges, x, y) : null);
    }
    public static final class Gesture {
        public double[] move(double x, double y) {
            if (!pressed) return new double[]{0, 0};
            double dx = x - lastX, dy = y - lastY;
            lastX = x; lastY = y; drag(dx, dy); return new double[]{dx, dy};
        }
        private double x, y, lastX, lastY, distance;
        private boolean pressed;
        public void begin(double x, double y) { this.x = lastX = x; this.y = lastY = y; distance = 0; pressed = true; }
        public void cancel() { pressed = false; }
        public void drag(double dx, double dy) { distance += Math.hypot(dx, dy); }
        public boolean release(double x, double y) {
            boolean click = pressed && distance <= 3 && Math.hypot(x - this.x, y - this.y) <= 3;
            pressed = false; return click;
        }
    }
    public static final double EXPAND_SCALE = 1.25, COLLAPSE_SCALE = 1.10;
    public static double effectiveScale(boolean sphere, double zoom, double depth) {
        if (!Double.isFinite(zoom) || zoom <= 0 || !Double.isFinite(depth) || sphere && Math.abs(depth) > 1.000001)
            return Double.NaN;
        return sphere ? zoom * 3 / (3 - depth) : zoom;
    }
    public static boolean expanded(boolean previous, double scale, boolean front, boolean visible) {
        return front && visible && Double.isFinite(scale) && scale >= (previous ? COLLAPSE_SCALE : EXPAND_SCALE);
    }
    public record Transform(double x, double y, double scale) {
        public Transform {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(scale) || scale <= 0
                || scale > Float.MAX_VALUE || Math.abs(x) > Float.MAX_VALUE || Math.abs(y) > Float.MAX_VALUE)
                throw new IllegalArgumentException("Invalid display transform");
        }
        public double localX(double mouseX) { return (mouseX - x) / scale; }
        public double localY(double mouseY) { return (mouseY - y) / scale; }
    }
}
