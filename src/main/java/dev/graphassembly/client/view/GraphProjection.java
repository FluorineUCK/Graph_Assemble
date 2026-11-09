package dev.graphassembly.client.view;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import java.util.*;

/** Layout and camera math without client or JEI linkage, independently testable. */
public final class GraphProjection {
    private GraphProjection() {}
    public record Point(double x, double y, double z) {}
    public record Projected(double x, double y, double depth) { public boolean hittable() { return depth > 0; } }
    public static Projected project(Point point, double yaw, double pitch, double radius, double cx, double cy) {
        double x = point.x() * Math.cos(yaw) + point.z() * Math.sin(yaw);
        double z = -point.x() * Math.sin(yaw) + point.z() * Math.cos(yaw);
        double y = point.y() * Math.cos(pitch) - z * Math.sin(pitch);
        double depth = point.y() * Math.sin(pitch) + z * Math.cos(pitch);
        double perspective = 3 / (3 - depth);
        return new Projected(cx + x * radius * perspective, cy + y * radius * perspective, depth);
    }
    public static Map<String, Point> layout(GraphDefinition<?> graph, boolean sphere) {
        Map<String, Point> result = new LinkedHashMap<>();
        int i = 0, count = graph.nodes().size();
        for (var node : graph.nodes().values()) {
            var layout = graph.layout().node(node.id());
            var override = sphere ? layout.spherePosition() : layout.position();
            Point point;
            if (!override.isEmpty()) {
                double x = override.get(0), y = override.get(1), z = sphere ? override.get(2) : 0;
                if (sphere) {
                    double scale = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
                    x /= scale; y /= scale; z /= scale;
                }
                double length = sphere ? Math.sqrt(x * x + y * y + z * z) : 1;
                point = new Point(x / length, y / length, z / length);
            } else if (sphere) {
                double y = 1 - 2 * (i + .5) / count;
                double r = Math.sqrt(1 - y * y), theta = i * Math.PI * (3 - Math.sqrt(5));
                point = new Point(Math.cos(theta) * r, y, Math.sin(theta) * r);
            } else {
                double angle = i * Math.PI * 2 / count - Math.PI / 2;
                point = new Point(Math.cos(angle) * 100, Math.sin(angle) * 65, 0);
            }
            result.put(node.id(), point); i++;
        }
        return result;
    }
    public static double segmentDistance(double x, double y, Projected a, Projected b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.x()) * dx + (y - a.y()) * dy) / length));
        return Math.hypot(x - a.x() - t * dx, y - a.y() - t * dy);
    }

    /** Bounded Cohen-Sutherland clipping, before any pixel-count conversion or iteration. */
    public static double[] clipSegment(double x1, double y1, double x2, double y2,
                                       double left, double top, double right, double bottom) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)) return null;
        for (int iteration = 0; iteration < 8; iteration++) {
            int a = region(x1, y1, left, top, right, bottom), b = region(x2, y2, left, top, right, bottom);
            if ((a | b) == 0) return new double[]{x1, y1, x2, y2};
            if ((a & b) != 0) return null;
            int outside = a != 0 ? a : b;
            double x, y;
            if ((outside & 12) != 0) {
                y = (outside & 4) != 0 ? top : bottom;
                double scale = Math.max(1, Math.max(Math.abs(y1), Math.abs(y2)));
                double t = (y / scale - y1 / scale) / (y2 / scale - y1 / scale);
                x = x1 * (1 - t) + x2 * t;
            } else {
                x = (outside & 1) != 0 ? left : right;
                double scale = Math.max(1, Math.max(Math.abs(x1), Math.abs(x2)));
                double t = (x / scale - x1 / scale) / (x2 / scale - x1 / scale);
                y = y1 * (1 - t) + y2 * t;
            }
            if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
            if (outside == a) { x1 = x; y1 = y; } else { x2 = x; y2 = y; }
        }
        return null;
    }
    private static int region(double x, double y, double left, double top, double right, double bottom) {
        return (x < left ? 1 : x > right ? 2 : 0) | (y < top ? 4 : y > bottom ? 8 : 0);
    }
}
