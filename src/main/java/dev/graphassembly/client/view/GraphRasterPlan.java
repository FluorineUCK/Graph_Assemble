package dev.graphassembly.client.view;

import dev.graphassembly.client.view.GraphProjection.Projected;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import dev.graphassembly.client.view.NodePresentation.Surface;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Widget-owned, paint-color-independent raster plan in exact legacy layer order. */
public final class GraphRasterPlan {
    public sealed interface Command permits EdgePixels, DotPixels, Preview {
        default Surface surface() { return null; }
    }
    public record EdgePixels(GraphEdges.Path path, GraphPixelMesh mesh) implements Command {}
    public record DotPixels(Surface surface, GraphPixelMesh mesh) implements Command {}
    public record Preview(Surface surface) implements Command {}
    /** The production draw sequence; sink methods keep platform-specific preview state local. */
    public interface Sink {
        void edge(EdgePixels pixels);
        void dot(DotPixels pixels);
        void flush();
        void beginPreview(Surface surface);
        /** Return false if preview drawing failed and the original post-draw flush must be skipped. */
        boolean drawPreview(Surface surface);
        void previewFailure(Surface surface, Throwable failure);
        void endPreview(Surface surface);
    }

    private final Bounds viewport;
    private List<GraphEdges.Path> oldPaths;
    private List<Surface> oldSurfaces;
    private Map<String, Projected> oldCenters;
    private List<Command> commands = List.of();
    private long retainedBytes, hits, rebuilds;

    public GraphRasterPlan(Bounds viewport) { this.viewport = viewport; }
    /** Returns true on reuse; changed projected paths, surface layers, or dot centers rebuild. */
    public boolean update(List<GraphEdges.Path> paths, List<Surface> surfaces, Map<String, Projected> centers) {
        if (oldPaths != null && oldPaths.equals(paths) && oldSurfaces.equals(surfaces) && oldCenters.equals(centers)) {
            hits++; return true;
        }
        var next = new ArrayList<Command>(); long bytes = 0;
        for (var path : paths) {
            var mesh = GraphPixelMesh.path(path); next.add(new EdgePixels(path, mesh)); bytes += mesh.retainedBytes();
        }
        for (var surface : surfaces) {
            if (!viewport.intersects(surface.bounds())) continue;
            if (surface.expanded()) next.add(new Preview(surface));
            else {
                var p = centers.get(surface.id());
                var mesh = GraphPixelMesh.dot(p.x(), p.y());
                next.add(new DotPixels(surface, mesh)); bytes += mesh.retainedBytes();
            }
        }
        oldPaths = List.copyOf(paths); oldSurfaces = List.copyOf(surfaces); oldCenters = Map.copyOf(centers);
        commands = List.copyOf(next); retainedBytes = bytes; rebuilds++;
        return false;
    }
    public List<Command> commands() { return commands; }
    public void dispatch(Sink sink) {
        try {
            for (var command : commands) {
                if (command instanceof EdgePixels edge) sink.edge(edge);
                else if (command instanceof DotPixels dot) sink.dot(dot);
                else {
                    var surface = command.surface();
                    sink.flush();
                    sink.beginPreview(surface);
                    try { if (sink.drawPreview(surface)) sink.flush(); }
                    catch (RuntimeException | LinkageError failure) { sink.previewFailure(surface, failure); }
                    finally { sink.endPreview(surface); }
                }
            }
        } finally { sink.flush(); }
    }
    public long retainedBytes() { return retainedBytes; }
    public long hits() { return hits; }
    public long rebuilds() { return rebuilds; }
    public void clear() {
        oldPaths = null; oldSurfaces = null; oldCenters = null; commands = List.of(); retainedBytes = 0;
    }
}
