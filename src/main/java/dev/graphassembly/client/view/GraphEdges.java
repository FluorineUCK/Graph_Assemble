package dev.graphassembly.client.view;

import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.definition.GraphLayout;
import dev.graphassembly.client.view.GraphProjection.Projected;
import java.util.*;

/** Screen-space directed paths shared by drawing and picking in both graph views. */
public final class GraphEdges {
    private GraphEdges() {}

    public record Path(Edge edge, List<Projected> points, boolean hittable, int arrowSegment, double arrowFraction) {
        public Path { points = List.copyOf(points); }
        public Path(Edge edge, List<Projected> points, boolean hittable) { this(edge, points, hittable, -1, .7); }
        public Path(Edge edge, List<Projected> points, boolean hittable, int arrowSegment) { this(edge, points, hittable, arrowSegment, .5); }
        public boolean authored() { return arrowSegment >= 0; }
    }
    public record Arrow(Projected tip, Projected left, Projected right) {}

    /** The exact canvas origin and zoom used by plane nodes; unrelated to screen GUI scale. */
    public record PlaneTransform(double originX, double originY, double zoom) {
        public static final PlaneTransform IDENTITY = new PlaneTransform(0, 0, 1);
        public PlaneTransform {
            if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(zoom) || zoom <= 0)
                throw new IllegalArgumentException("Plane transform requires finite coordinates and positive zoom");
        }
        public Projected project(GraphLayout.Waypoint point) {
            return new Projected(originX + point.x() * zoom, originY + point.y() * zoom, 1);
        }
    }

    /** Optional, per-pass counters; never retained globally or used by rendering. */
    public static final class Diagnostics {
        private long boundsEvaluations, exactDistanceEvaluations, topologyPreparations, trialReplacements, spatialPreparations;
        public long boundsEvaluations() { return boundsEvaluations; }
        public long exactDistanceEvaluations() { return exactDistanceEvaluations; }
        public long topologyPreparations() { return topologyPreparations; }
        public long trialReplacements() { return trialReplacements; }
        public long spatialPreparations() { return spatialPreparations; }
    }

    private static final class SpatialPath {
        final Path path;
        final double minX, minY, maxX, maxY;
        final boolean boundedFinite;
        SpatialPath(Path path) {
            this.path = path;
            double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
            boolean allBounded = true;
            for (var point : path.points()) {
                allBounded &= Double.isFinite(point.x()) && Double.isFinite(point.y())
                    && Math.abs(point.x()) <= BROAD_PHASE_COORD_LIMIT && Math.abs(point.y()) <= BROAD_PHASE_COORD_LIMIT;
                left = Math.min(left, point.x()); top = Math.min(top, point.y());
                right = Math.max(right, point.x()); bottom = Math.max(bottom, point.y());
            }
            minX = left; minY = top; maxX = right; maxY = bottom;
            boundedFinite = allBounded && !path.points().isEmpty();
        }
        boolean mayHit(double x, double y) {
            // Legacy t may round to 1 for huge finite deltas. For |coordinate| <= 8192,
            // a hittable t is in [0,1], so the ideal residual is outside the endpoint box
            // by the query's box gap. The four rounded residual operations (b-a, x-a,
            // t*dx, subtraction) each err by < 4e-12 at these magnitudes; .25 also dwarfs
            // hypot rounding. Outside that envelope, use the unchanged exact distance.
            return !boundedFinite || !Double.isFinite(x) || !Double.isFinite(y)
                || Math.abs(x) > BROAD_PHASE_COORD_LIMIT || Math.abs(y) > BROAD_PHASE_COORD_LIMIT
                || !(x < Math.nextDown(minX - PICK_RADIUS - BROAD_PHASE_NUMERIC_GUARD)
                    || x > Math.nextUp(maxX + PICK_RADIUS + BROAD_PHASE_NUMERIC_GUARD)
                    || y < Math.nextDown(minY - PICK_RADIUS - BROAD_PHASE_NUMERIC_GUARD)
                    || y > Math.nextUp(maxY + PICK_RADIUS + BROAD_PHASE_NUMERIC_GUARD));
        }
    }

    /** Coordinates belong to the supplied current paths, not to their immutable topology. */
    public static final class Prepared {
        private final List<Path> paths;
        private final SpatialPath[] spatial;
        private final Diagnostics diagnostics;
        private Prepared(List<Path> paths, Diagnostics diagnostics) {
            this.paths = List.copyOf(paths);
            this.spatial = this.paths.stream().map(SpatialPath::new).toArray(SpatialPath[]::new);
            this.diagnostics = diagnostics;
            if (diagnostics != null) diagnostics.spatialPreparations += spatial.length;
        }
        private Prepared(List<Path> paths, SpatialPath[] spatial, Diagnostics diagnostics) {
            this.paths = List.copyOf(paths);
            this.spatial = spatial;
            this.diagnostics = diagnostics;
        }
        public List<Path> paths() { return paths; }
    }

    public static Prepared prepare(List<Path> paths) { return new Prepared(paths, null); }
    public static Prepared prepare(List<Path> paths, Diagnostics diagnostics) { return new Prepared(paths, diagnostics); }

    /** Immutable graph structure; a widget retains one instance while projections change. */
    public static final class Topology {
        private record Connection(String from, String to) {}
        private final List<Edge> edges;
        private final GraphLayout layout;
        private final Map<String, Integer> nodeIndex;
        private final boolean[] reciprocal;
        private final List<Integer> routeOrder;

        private Topology(List<Edge> source, GraphLayout layout, Diagnostics diagnostics) {
            if (diagnostics != null) diagnostics.topologyPreparations++;
            edges = List.copyOf(source);
            this.layout = layout;
            Map<String, Integer> indices = new LinkedHashMap<>();
            Set<Connection> connections = new HashSet<>();
            for (var edge : edges) {
                indices.computeIfAbsent(edge.from(), ignored -> indices.size());
                indices.computeIfAbsent(edge.to(), ignored -> indices.size());
                connections.add(new Connection(edge.from(), edge.to()));
            }
            nodeIndex = Map.copyOf(indices);
            reciprocal = new boolean[edges.size()];
            for (int i = 0; i < edges.size(); i++) {
                var edge = edges.get(i);
                reciprocal[i] = connections.contains(new Connection(edge.to(), edge.from()));
            }
            routeOrder = java.util.stream.IntStream.range(0, edges.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> edges.get(i).from()).thenComparing(i -> edges.get(i).to())).toList();
        }
        public static Topology of(List<Edge> edges) { return of(edges, new GraphLayout(Map.of(), Map.of())); }
        public static Topology of(List<Edge> edges, Diagnostics diagnostics) { return new Topology(edges, new GraphLayout(Map.of(), Map.of()), diagnostics); }
        public static Topology of(List<Edge> edges, GraphLayout layout) { return new Topology(edges, layout, null); }
        public List<Edge> edges() { return edges; }
        public Map<String, Integer> nodeIndex() { return nodeIndex; }
    }

    /** The same arrow geometry is used by rendering and visibility checks. */
    public static Arrow arrow(Path path) {
        int middle = path.authored() ? path.arrowSegment() : (path.points().size() - 1) / 2;
        var a = path.points().get(middle); var b = path.points().get(middle + 1);
        if (path.authored() && a.x() == b.x() && a.y() == b.y()) return new Arrow(a, a, a);
        double angle = Math.atan2(b.y() - a.y(), b.x() - a.x());
        double fraction = path.arrowFraction();
        var tip = new Projected(a.x() + (b.x() - a.x()) * fraction, a.y() + (b.y() - a.y()) * fraction, a.depth());
        return new Arrow(tip, new Projected(tip.x() - Math.cos(angle - .5) * 6, tip.y() - Math.sin(angle - .5) * 6, tip.depth()),
            new Projected(tip.x() - Math.cos(angle + .5) * 6, tip.y() - Math.sin(angle + .5) * 6, tip.depth()));
    }

    public static List<Path> layout(List<Edge> edges, Map<String, Projected> nodes, boolean sphere,
                                    List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport) {
        return layoutWithTopology(Topology.of(edges), nodes, sphere, surfaces, viewport);
    }

    public static List<Path> layoutWithTopology(Topology topology, Map<String, Projected> nodes, boolean sphere,
                                                List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport) {
        return layoutPrepared(topology, nodes, sphere, surfaces, viewport, null).paths();
    }

    public static Prepared layoutPrepared(Topology topology, Map<String, Projected> nodes, boolean sphere,
                                          List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport) {
        return layoutPrepared(topology, nodes, sphere, surfaces, viewport, null);
    }

    public static Prepared layoutPrepared(Topology topology, Map<String, Projected> nodes, boolean sphere,
                                          List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport,
                                          Diagnostics diagnostics) {
        return layoutPrepared(topology, nodes, sphere, surfaces, viewport, diagnostics, PlaneTransform.IDENTITY);
    }

    public static Prepared layoutRouted(Topology topology, Map<String, Projected> nodes, boolean sphere,
                                        List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport,
                                        PlaneTransform plane) {
        return layoutPrepared(topology, nodes, sphere, surfaces, viewport, null, plane);
    }

    private static Prepared layoutPrepared(Topology topology, Map<String, Projected> nodes, boolean sphere,
                                           List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport,
                                           Diagnostics diagnostics, PlaneTransform plane) {
        Map<String, NodePresentation.Bounds> expanded = new HashMap<>();
        surfaces.stream().filter(NodePresentation.Surface::expanded).forEach(s -> expanded.put(s.id(), s.bounds()));
        List<Path> paths = new ArrayList<>(layoutWithTopology(topology, nodes, sphere, plane));
        for (int i = 0; i < paths.size(); i++) {
            var path = paths.get(i); var edge = path.edge(); var bounds = expanded.get(edge.from());
            if (!path.authored() && edge.from().equals(edge.to()) && bounds != null)
                paths.set(i, new Path(edge, expandedLoop(bounds, viewport, nodes.get(edge.from()).depth()), path.hittable()));
        }
        SpatialPath[] spatial = paths.stream().map(SpatialPath::new).toArray(SpatialPath[]::new);
        if (diagnostics != null) diagnostics.spatialPreparations += spatial.length;
        var visible = new VisibleContext(surfaces, viewport);
        // Move only the arrow along authored segments; never replace an artist's route
        // with a machine-preview detour or the old center-to-center chord.
        for (int i = 0; i < paths.size(); i++) {
            var path = paths.get(i);
            if (!path.authored()) continue;
            search:
            for (int segment : longestSegments(path.points())) {
                for (double along : arrowPositions(path, segment, visible)) {
                    var candidate = new Path(path.edge(), path.points(), path.hittable(), segment, along);
                    if (!visibleArrow(candidate, visible)) continue;
                    var candidateSpatial = new SpatialPath(candidate);
                    if (!arrowPickedBy(candidateSpatial, spatial, i, candidateSpatial, diagnostics)) continue;
                    paths.set(i, candidate); spatial[i] = candidateSpatial;
                    break search;
                }
            }
        }
        Set<Path> protectedArrows = new LinkedHashSet<>();
        Map<Path, SpatialPath> protectedSpatial = new HashMap<>();
        for (int i = 0; i < paths.size(); i++) {
            var path = paths.get(i);
            if (path.hittable() && visibleArrow(path, visible)
                && arrowPickedBy(spatial[i], spatial, -1, null, diagnostics)) {
                protectedArrows.add(path);
                protectedSpatial.put(path, spatial[i]);
            }
        }
        // Stable priority independent of JSON order, including when reciprocal lanes share one free side.
        for (int index : topology.routeOrder) {
            var path = paths.get(index); var edge = path.edge();
            if (!path.hittable() || path.authored()) continue;
            if (visibleArrow(path, visible) && arrowPickedBy(spatial[index], spatial, -1, null, diagnostics)) {
                protectedArrows.add(path);
                protectedSpatial.put(path, spatial[index]);
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
                    if (!visibleArrow(candidate, visible)) continue;
                    var candidateSpatial = new SpatialPath(candidate);
                    if (diagnostics != null) {
                        diagnostics.trialReplacements++;
                        diagnostics.spatialPreparations++;
                    }
                    if (!arrowPickedBy(candidateSpatial, spatial, index, candidateSpatial, diagnostics)) continue;
                    // Only the replacement can steal a previously verified arrow; compare those two paths.
                    boolean obscuresExistingArrow = protectedArrows.stream().anyMatch(p ->
                        !arrowPickedBy(protectedSpatial.get(p),
                            new SpatialPath[]{protectedSpatial.get(p), candidateSpatial}, -1, null, diagnostics));
                    if (!obscuresExistingArrow) {
                        paths.set(index, candidate); spatial[index] = candidateSpatial;
                        protectedArrows.add(candidate); protectedSpatial.put(candidate, candidateSpatial);
                        break route;
                    }
                }
            }
        }
        return new Prepared(paths, spatial, diagnostics);
    }

    private record Interval(double start, double end) {}

    /** Subtract model footprints before choosing a tip, so even narrow free gaps are considered. */
    private static List<Double> arrowPositions(Path path, int segment, VisibleContext visible) {
        var a = path.points().get(segment); var b = path.points().get(segment + 1);
        if (a.x() == b.x() && a.y() == b.y()) return List.of();
        var marker = arrow(new Path(path.edge(), path.points(), path.hittable(), segment, 0));
        double left = Math.min(0, Math.min(marker.left().x() - a.x(), marker.right().x() - a.x())) - PICK_RADIUS;
        double right = Math.max(0, Math.max(marker.left().x() - a.x(), marker.right().x() - a.x())) + PICK_RADIUS;
        double top = Math.min(0, Math.min(marker.left().y() - a.y(), marker.right().y() - a.y())) - PICK_RADIUS;
        double bottom = Math.max(0, Math.max(marker.left().y() - a.y(), marker.right().y() - a.y())) + PICK_RADIUS;
        double minX = visible.viewport.left() - left, maxX = visible.viewport.right() - right;
        double minY = visible.viewport.top() - top, maxY = visible.viewport.bottom() - bottom;
        if (minX >= maxX || minY >= maxY) return List.of();
        var clipped = GraphProjection.clipSegment(a.x(), a.y(), b.x(), b.y(), minX, minY, maxX, maxY);
        if (clipped == null) return List.of();
        List<Interval> free = new ArrayList<>();
        free.add(new Interval(along(a, b, clipped[0], clipped[1]), along(a, b, clipped[2], clipped[3])));
        for (var bounds : visible.frontBounds) {
            var covered = GraphProjection.clipSegment(a.x(), a.y(), b.x(), b.y(),
                bounds.left() - right, bounds.top() - bottom, bounds.right() - left, bounds.bottom() - top);
            if (covered == null) continue;
            double start = along(a, b, covered[0], covered[1]), end = along(a, b, covered[2], covered[3]);
            List<Interval> next = new ArrayList<>();
            for (var gap : free) {
                if (end <= gap.start() || start >= gap.end()) next.add(gap);
                else {
                    if (start > gap.start()) next.add(new Interval(gap.start(), start));
                    if (end < gap.end()) next.add(new Interval(end, gap.end()));
                }
            }
            free = next;
            if (free.isEmpty()) break;
        }
        List<Double> positions = new ArrayList<>();
        free.sort(Comparator.comparingDouble(gap -> gap.start() - gap.end()));
        for (var gap : free) if (gap.end() > gap.start()) {
            // Extra interior candidates handle a crossing edge without changing either route.
            for (double fraction : new double[]{.5, .25, .75})
                positions.add(gap.start() + (gap.end() - gap.start()) * fraction);
        }
        return positions;
    }

    private static double along(Projected a, Projected b, double x, double y) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        return Math.abs(dx) >= Math.abs(dy) ? (x - a.x()) / dx : (y - a.y()) / dy;
    }

    private static final double EDGE_CLEARANCE = 12, PICK_RADIUS = 4;
    private static final double BROAD_PHASE_COORD_LIMIT = 8192, BROAD_PHASE_NUMERIC_GUARD = .25;
    private static final int DETOUR_LANES = 4;
    private static boolean arrowPickedBy(SpatialPath path, SpatialPath[] paths, int replacementIndex,
                                         SpatialPath replacement, Diagnostics diagnostics) {
        var arrow = arrow(path.path);
        for (var end : List.of(arrow.left(), arrow.right())) for (double t : new double[]{0, .5, 1}) {
            double x = arrow.tip().x() + (end.x() - arrow.tip().x()) * t, y = arrow.tip().y() + (end.y() - arrow.tip().y()) * t;
            if (!path.path.edge().equals(hit(paths, diagnostics, replacementIndex, replacement, x, y))) return false;
        }
        return true;
    }
    private static final class VisibleContext {
        final List<NodePresentation.Bounds> frontBounds;
        final NodePresentation.Bounds viewport;
        VisibleContext(List<NodePresentation.Surface> surfaces, NodePresentation.Bounds viewport) {
            this.frontBounds = surfaces.stream().filter(NodePresentation.Surface::front).map(NodePresentation.Surface::bounds).toList();
            this.viewport = viewport;
        }
    }
    private static boolean visibleArrow(Path path, VisibleContext visible) {
        var arrow = arrow(path);
        double minX = Math.min(arrow.tip().x(), Math.min(arrow.left().x(), arrow.right().x())) - PICK_RADIUS;
        double minY = Math.min(arrow.tip().y(), Math.min(arrow.left().y(), arrow.right().y())) - PICK_RADIUS;
        double maxX = Math.max(arrow.tip().x(), Math.max(arrow.left().x(), arrow.right().x())) + PICK_RADIUS;
        double maxY = Math.max(arrow.tip().y(), Math.max(arrow.left().y(), arrow.right().y())) + PICK_RADIUS;
        var area = new NodePresentation.Bounds(minX, minY, maxX, maxY);
        if (!visible.viewport.contains(minX, minY) || !visible.viewport.contains(maxX, maxY)) return false;
        for (var bounds : visible.frontBounds) {
            // Reject only finite, definitely disjoint boxes; keep the original rectangle test.
            if (Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(maxX) && Double.isFinite(maxY)
                && Double.isFinite(bounds.left()) && Double.isFinite(bounds.top())
                && Double.isFinite(bounds.right()) && Double.isFinite(bounds.bottom())
                && (maxX <= bounds.left() || minX >= bounds.right()
                    || maxY <= bounds.top() || minY >= bounds.bottom())) continue;
            if (bounds.intersects(area)) return false;
        }
        return true;
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
        return layoutWithTopology(Topology.of(edges), nodes, sphere);
    }

    public static List<Path> layoutWithTopology(Topology topology, Map<String, Projected> nodes, boolean sphere) {
        return layoutWithTopology(topology, nodes, sphere, PlaneTransform.IDENTITY);
    }

    private static List<Path> layoutWithTopology(Topology topology, Map<String, Projected> nodes, boolean sphere,
                                                PlaneTransform plane) {
        List<Path> result = new ArrayList<>(topology.edges.size());
        for (int i = 0; i < topology.edges.size(); i++) {
            var edge = topology.edges.get(i);
            var a = nodes.get(edge.from()); var b = nodes.get(edge.to());
            var waypoints = topology.layout.edge(edge).waypoints();
            if (!sphere && !waypoints.isEmpty()) {
                List<Projected> routed = new ArrayList<>();
                routed.add(a);
                waypoints.forEach(point -> routed.add(plane.project(point)));
                routed.add(b);
                result.add(new Path(edge, routed, true, longestSegments(routed).getFirst()));
                continue;
            }
            var points = edge.from().equals(edge.to())
                ? List.of(new Projected(a.x() - 5, a.y() - 4, a.depth()), new Projected(a.x() - 15, a.y() - 18, a.depth()),
                    new Projected(a.x() + 15, a.y() - 18, a.depth()), new Projected(a.x() + 5, a.y() - 4, a.depth()))
                : List.of(a, b);
            if (!edge.from().equals(edge.to()) && topology.reciprocal[i]) {
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
            result.add(new Path(edge, points, !sphere || a.hittable() && b.hittable()));
        }
        return List.copyOf(result);
    }

    private static List<Integer> longestSegments(List<Projected> points) {
        return java.util.stream.IntStream.range(0, points.size() - 1).boxed()
            .sorted(Comparator.<Integer>comparingDouble(i -> {
                var a = points.get(i); var b = points.get(i + 1);
                return -Math.hypot(b.x() - a.x(), b.y() - a.y());
            }).thenComparingInt(i -> i)).toList();
    }

    public static Edge hit(List<Path> paths, double x, double y) {
        return hitPrepared(prepare(paths), x, y);
    }

    public static Edge hitPrepared(Prepared prepared, double x, double y) {
        return hit(prepared.spatial, prepared.diagnostics, -1, null, x, y);
    }

    private static Edge hit(SpatialPath[] paths, Diagnostics diagnostics, int replacementIndex,
                            SpatialPath replacement, double x, double y) {
        Edge best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < paths.length; i++) {
            var spatial = i == replacementIndex ? replacement : paths[i];
            if (!spatial.path.hittable()) continue;
            if (diagnostics != null) diagnostics.boundsEvaluations++;
            if (!spatial.mayHit(x, y)) continue;
            if (diagnostics != null) diagnostics.exactDistanceEvaluations++;
            double distance = distance(spatial.path, x, y);
            if (!(distance <= PICK_RADIUS)) continue;
            var edge = spatial.path.edge();
            if (best == null || Double.compare(distance, bestDistance) < 0
                || Double.compare(distance, bestDistance) == 0 &&
                    (edge.from().compareTo(best.from()) < 0 || edge.from().equals(best.from()) && edge.to().compareTo(best.to()) < 0)) {
                best = edge;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double distance(Path path, double x, double y) {
        double result = Double.POSITIVE_INFINITY;
        for (int i = 1; i < path.points().size(); i++)
            result = Math.min(result, GraphProjection.segmentDistance(x, y, path.points().get(i - 1), path.points().get(i)));
        return result;
    }
}
