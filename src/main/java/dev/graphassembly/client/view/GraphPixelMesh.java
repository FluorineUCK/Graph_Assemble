package dev.graphassembly.client.view;

import java.util.Arrays;

/** Ordered integer pixel origins for the legacy 1x1 GUI quads. */
public final class GraphPixelMesh {
    @FunctionalInterface public interface Pixel { void accept(int x, int y); }
    private final int[] coordinates;
    private final int count;
    private GraphPixelMesh(int[] coordinates, int count) { this.coordinates = coordinates; this.count = count; }
    public int pixelCount() { return count; }
    public long retainedBytes() { return (long)coordinates.length * Integer.BYTES; }
    public void forEach(Pixel pixel) {
        for (int i = 0; i < count * 2; i += 2) pixel.accept(coordinates[i], coordinates[i + 1]);
    }
    public int x(int pixel) { return coordinates[pixel * 2]; }
    public int y(int pixel) { return coordinates[pixel * 2 + 1]; }
    public static GraphPixelMesh line(double x1, double y1, double x2, double y2) {
        var builder = new Builder(); builder.line(x1, y1, x2, y2); return builder.build();
    }
    public static GraphPixelMesh dot(double centerX, double centerY) {
        var builder = new Builder(); builder.dot(centerX, centerY); return builder.build();
    }
    public static GraphPixelMesh path(GraphEdges.Path path) {
        var builder = new Builder(); var points = path.points();
        for (int i = 1; i < points.size(); i++) builder.line(points.get(i-1).x(), points.get(i-1).y(), points.get(i).x(), points.get(i).y());
        var arrow = GraphEdges.arrow(path);
        builder.line(arrow.tip().x(), arrow.tip().y(), arrow.left().x(), arrow.left().y());
        builder.line(arrow.tip().x(), arrow.tip().y(), arrow.right().x(), arrow.right().y());
        return builder.build();
    }
    public static final class Builder {
        private int[] coordinates = new int[0];
        private int count;
        private void add(int x, int y) {
            if (count * 2 == coordinates.length) coordinates = Arrays.copyOf(coordinates, Math.max(32, coordinates.length * 2));
            coordinates[count * 2] = x; coordinates[count * 2 + 1] = y; count++;
        }
        public Builder line(double x1, double y1, double x2, double y2) {
            var clipped = GraphProjection.clipSegment(x1, y1, x2, y2, 0, 20, 320, 189);
            if (clipped == null) return this;
            x1 = clipped[0]; y1 = clipped[1]; x2 = clipped[2]; y2 = clipped[3];
            int steps = (int)Math.ceil(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
            for (int i = 0; i <= steps; i++) {
                double t = steps == 0 ? 0 : (double)i / steps;
                add((int)Math.round(x1 + (x2 - x1) * t), (int)Math.round(y1 + (y2 - y1) * t));
            }
            return this;
        }
        public Builder dot(double centerX, double centerY) {
            int cx = (int)centerX, cy = (int)centerY;
            for (int y = -2; y <= 2; y++) for (int x = -2; x <= 2; x++)
                if (x*x + y*y <= 6) add(cx + x, cy + y);
            return this;
        }
        public GraphPixelMesh build() { return new GraphPixelMesh(Arrays.copyOf(coordinates, count * 2), count); }
    }
}
