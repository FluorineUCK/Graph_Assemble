package dev.graphassembly.client.view;

/** World-locked UVs for a single tiled background quad (logical tile pixels, not PNG resolution). */
public final class CanvasBackground {
    private CanvasBackground() {}
    public record Uv(double left, double top, double right, double bottom) {}
    public static Uv uv(NodePresentation.Bounds area, double originX, double originY, double zoom, int tileSize) {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(zoom) || zoom <= 0 || tileSize <= 0)
            throw new IllegalArgumentException("Invalid background camera");
        double period = zoom * tileSize;
        // Modulo before converting to float prevents precision loss after long/negative pans.
        double left = fract((area.left() - originX) / period);
        double top = fract((area.top() - originY) / period);
        return new Uv(left, top, left + (area.right() - area.left()) / period, top + (area.bottom() - area.top()) / period);
    }
    private static double fract(double value) { return value - Math.floor(value); }
}
