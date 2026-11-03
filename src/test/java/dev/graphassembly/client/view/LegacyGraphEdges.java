package dev.graphassembly.client.view;

import dev.graphassembly.core.Edge;
import dev.graphassembly.client.view.GraphProjection.Projected;
import java.util.*;

/** Screen-space directed paths shared by drawing and picking in both graph views. */
final class LegacyGraphEdges {
    private LegacyGraphEdges() {}

    public record Path(Edge edge, List<Projected> points, boolean hittable) {
        public Path { points = List.copyOf(points); }
    }
    public record Arrow(Projected tip, Projected left, Projected right) {}

    /** The same arrow geometry is used by rendering and visibility checks. */
    public static Arrow arrow(Path path) {
        int middle = (path.points().size() - 1) / 2;
        var a = path.points().get(middle); var b = path.points().get(middle + 1);
        double angle = Math.atan2(b.y() - a.y(), b.x() - a.x());
        var tip = new Projected(a.x() + (b.x() - a.x()) * .7, a.y() + (b.y() - a.y()) * .7, a.depth());
        return new Arrow(tip, new Projected(tip.x() - Math.cos(angle - .5) * 6, tip.y() - Math.sin(angle - .5) * 6, tip.depth()),
            new Projected(tip.x() - Math.cos(angle + .5) * 6, tip.y() - Math.sin(angle + .5) * 6, tip.depth()));
    }

    public static List<Path> layout(List<Edge> edges, Map<String, Projected> nodes, boolean sphere,
                                    List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport) {
        Map<String, NodePresentation.Bounds> expanded = new HashMap<>();
        surfaces.stream().filter(NodePresentation.Surface::expanded).forEach(s -> expanded.put(s.id(), s.bounds()));
        List<Path> paths = new ArrayList<>(layout(edges, nodes, sphere));
        for (int i = 0; i < paths.size(); i++) {
            var path = paths.get(i); var edge = path.edge(); var bounds = expanded.get(edge.from());
            if (edge.from().equals(edge.to()) && bounds != null)
                paths.set(i, new Path(edge, expandedLoop(bounds, viewport, nodes.get(edge.from()).depth()), path.hittable()));
        }
        Set<Path> protectedArrows = new LinkedHashSet<>();
        paths.stream().filter(Path::hittable).filter(p -> visibleArrow(p, surfaces, viewport))
            .filter(p -> arrowPickedBy(p, paths)).forEach(protectedArrows::add);
        // Stable priority independent of JSON order, including when reciprocal lanes share one free side.
        var order = java.util.stream.IntStream.range(0, paths.size()).boxed()
            .sorted(Comparator.comparing((Integer i) -> paths.get(i).edge().from()).thenComparing(i -> paths.get(i).edge().to())).toList();
        for (int index : order) {
            var path = paths.get(index); var edge = path.edge();
            if (!path.hittable()) continue;
            if (visibleArrow(path, surfaces, viewport) && arrowPickedBy(path, paths)) {
                protectedArrows.add(path);
                continue;
            }
            // Self-loops also need obstacle-aware fallback: their first free side may contain a neighbor.
            if (!expanded.containsKey(edge.from()) && !expanded.containsKey(edge.to())) continue;
            var a = nodes.get(edge.from()); var b = nodes.get(edge.to());
            var ab = expanded.getOrDefault(edge.from(), new NodePresentation.Bounds(a.x() - 5, a.y() - 5, a.x() + 5, a.y() + 5));
            var bb = expanded.getOrDefault(edge.to(), new NodePresentation.Bounds(b.x() - 5, b.y() - 5, b.x() + 5, b.y() + 5));
            boolean vertical = Math.abs(b.y() - a.y()) >= Math.abs(b.x() - a.x());
            route:
            for (boolean axis : new boolean[]{vertical, !vertical}) {
                double delta = axis ? b.y() - a.y() : b.x() - a.x();
                boolean positive = delta == 0 ? edge.from().compareTo(edge.to()) > 0 : axis ? delta < 0 : delta > 0;
                for (int lane = 1; lane <= DETOUR_LANES; lane++) for (boolean side : new boolean[]{positive, !positive}) {
                    var candidate = detour(path, a, b, ab, bb, viewport, axis, side, EDGE_CLEARANCE * lane);
                    if (!visibleArrow(candidate, surfaces, viewport)) continue;
                    var trial = new ArrayList<>(paths); trial.set(index, candidate);
                    if (!arrowPickedBy(candidate, trial)) continue;
                    // Only the replacement can steal a previously verified arrow; compare those two paths.
                    boolean obscuresExistingArrow = protectedArrows.stream().anyMatch(p -> !arrowPickedBy(p, List.of(p, candidate)));
                    if (!obscuresExistingArrow) { paths.set(index, candidate); protectedArrows.add(candidate); break route; }
                }
            }
        }
        return List.copyOf(paths);
    }

    private static final double EDGE_CLEARANCE = 12, PICK_RADIUS = 4;
    private static final int DETOUR_LANES = 4;
    private static boolean arrowPickedBy(Path path, List<Path> paths) {
        var arrow = arrow(path);
        for (var end : List.of(arrow.left(), arrow.right())) for (double t : new double[]{0, .5, 1}) {
            double x = arrow.tip().x() + (end.x() - arrow.tip().x()) * t, y = arrow.tip().y() + (end.y() - arrow.tip().y()) * t;
            if (!path.edge().equals(hit(paths, x, y))) return false;
        }
        return true;
    }
    private static boolean visibleArrow(Path path, List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport) {
        var arrow = arrow(path);
        double minX = Math.min(arrow.tip().x(), Math.min(arrow.left().x(), arrow.right().x())) - PICK_RADIUS;
        double minY = Math.min(arrow.tip().y(), Math.min(arrow.left().y(), arrow.right().y())) - PICK_RADIUS;
        double maxX = Math.max(arrow.tip().x(), Math.max(arrow.left().x(), arrow.right().x())) + PICK_RADIUS;
        double maxY = Math.max(arrow.tip().y(), Math.max(arrow.left().y(), arrow.right().y())) + PICK_RADIUS;
        var area = new NodePresentation.Bounds(minX, minY, maxX, maxY);
        return viewport.contains(minX, minY) && viewport.contains(maxX, maxY)
            && surfaces.stream().noneMatch(s -> s.front() && s.bounds().intersects(area));
    }
    private static Path detour(Path path, Projected a, Projected b, NodePresentation.Bounds ab, NodePresentation.Bounds bb,
                               NodePresentation.Bounds viewport, boolean vertical, boolean positive, double clearance) {
        double aBorder = vertical ? (positive ? ab.right() : ab.left()) : (positive ? ab.bottom() : ab.top());
        double bBorder = vertical ? (positive ? bb.right() : bb.left()) : (positive ? bb.bottom() : bb.top());
        double lane = (positive ? Math.max(aBorder, bBorder) + clearance : Math.min(aBorder, bBorder) - clearance);
        double alongA = vertical ? a.y() : a.x(), alongB = vertical ? b.y() : b.x();
        double low = (vertical ? viewport.top() : viewport.left()) + EDGE_CLEARANCE + PICK_RADIUS;
        double high = (vertical ? viewport.bottom() : viewport.right()) - EDGE_CLEARANCE - PICK_RADIUS;
        double controlA = Math.max(low, Math.min(high, alongA)), controlB = Math.max(low, Math.min(high, alongB));
        if (Math.abs(controlB - controlA) < EDGE_CLEARANCE * 2) {
            // Coincident or clipped centers still require a nonzero, correctly directed arrow shaft.
            double sign = alongB == alongA ? (path.edge().from().compareTo(path.edge().to()) < 0 ? 1 : -1) : Math.signum(alongB - alongA);
            double center = Math.max(low + EDGE_CLEARANCE, Math.min(high - EDGE_CLEARANCE, (controlA + controlB) / 2));
            controlA = center - sign * EDGE_CLEARANCE; controlB = center + sign * EDGE_CLEARANCE;
        }
        return new Path(path.edge(), List.of(loopPoint(!vertical, alongA, aBorder, a.depth()),
            loopPoint(!vertical, controlA, lane, a.depth()), loopPoint(!vertical, controlB, lane, b.depth()),
            loopPoint(!vertical, alongB, bBorder, b.depth())), path.hittable());
    }

    private static final double LOOP_CLEARANCE = 14, LOOP_HALF_WIDTH = 15;
    private static List<Projected> expandedLoop(NodePresentation.Bounds bounds, NodePresentation.Bounds viewport, double depth) {
        // Screen-space clearance stays pickable at every zoom; the attachment follows the scaled panel.
        // Prefer above the node, but use the roomiest visible side when panning/zooming clips it.
        double[] borders = {bounds.top(), bounds.right(), bounds.bottom(), bounds.left()};
        double[] room = {bounds.top() - viewport.top(), viewport.right() - bounds.right(),
            viewport.bottom() - bounds.bottom(), bounds.left() - viewport.left()};
        int side = 0;
        if (room[0] < LOOP_CLEARANCE) for (int i = 1; i < room.length; i++) if (room[i] > room[side]) side = i;
        boolean horizontal = side % 2 == 0;
        double low = horizontal ? Math.max(bounds.left(), viewport.left()) : Math.max(bounds.top(), viewport.top());
        double high = horizontal ? Math.min(bounds.right(), viewport.right()) : Math.min(bounds.bottom(), viewport.bottom());
        double center = (low + high) / 2, halfWidth = Math.min(LOOP_HALF_WIDTH, Math.max(0, (high - low) / 2 - 1));
        double border = borders[side];
        double outside = border + (side == 0 || side == 3 ? -1 : 1) * Math.min(LOOP_CLEARANCE, Math.max(1, room[side] - 1));
        return List.of(loopPoint(horizontal, center - halfWidth / 3, border, depth),
            loopPoint(horizontal, center - halfWidth, outside, depth),
            loopPoint(horizontal, center + halfWidth, outside, depth),
            loopPoint(horizontal, center + halfWidth / 3, border, depth));
    }
    private static Projected loopPoint(boolean horizontal, double along, double across, double depth) {
        return horizontal ? new Projected(along, across, depth) : new Projected(across, along, depth);
    }

    public static List<Path> layout(List<Edge> edges, Map<String, Projected> nodes, boolean sphere) {
        record Connection(String from, String to) {}
        Set<Connection> connections = new HashSet<>();
        edges.forEach(edge -> connections.add(new Connection(edge.from(), edge.to())));
        return edges.stream().map(edge -> {
            var a = nodes.get(edge.from()); var b = nodes.get(edge.to());
            var points = edge.from().equals(edge.to())
                ? List.of(new Projected(a.x() - 5, a.y() - 4, a.depth()), new Projected(a.x() - 15, a.y() - 18, a.depth()),
                    new Projected(a.x() + 15, a.y() - 18, a.depth()), new Projected(a.x() + 5, a.y() - 4, a.depth()))
                : List.of(a, b);
            if (!edge.from().equals(edge.to()) && connections.contains(new Connection(edge.to(), edge.from()))) {
                double dx = b.x() - a.x(), dy = b.y() - a.y(), length = Math.hypot(dx, dy);
                if (length == 0) {
                    // Coincident projected nodes still need two distinct directed paths.
                    double side = edge.from().compareTo(edge.to()) < 0 ? 1 : -1;
                    points = List.of(a, new Projected(a.x() - 15, a.y() + side * 18, a.depth()),
                        new Projected(a.x() + 15, a.y() + side * 18, b.depth()), b);
                } else if (Double.isFinite(length)) {
                    // The left normal reverses with direction: reciprocal lanes cannot coincide.
                    double ox = -dy / length * 8, oy = dx / length * 8;
                    points = List.of(a, new Projected(a.x() * .8 + b.x() * .2 + ox, a.y() * .8 + b.y() * .2 + oy, a.depth()),
                        new Projected(a.x() * .2 + b.x() * .8 + ox, a.y() * .2 + b.y() * .8 + oy, b.depth()), b);
                }
            }
            return new Path(edge, points, !sphere || a.hittable() && b.hittable());
        }).toList();
    }

    public static Edge hit(List<Path> paths, double x, double y) {
        return paths.stream().filter(Path::hittable).filter(path -> distance(path, x, y) <= PICK_RADIUS)
            .min(Comparator.comparingDouble((Path path) -> distance(path, x, y))
                .thenComparing(path -> path.edge().from()).thenComparing(path -> path.edge().to()))
            .map(Path::edge).orElse(null);
    }

    private static double distance(Path path, double x, double y) {
        double result = Double.POSITIVE_INFINITY;
        for (int i = 1; i < path.points().size(); i++)
            result = Math.min(result, GraphProjection.segmentDistance(x, y, path.points().get(i - 1), path.points().get(i)));
        return result;
    }
}
