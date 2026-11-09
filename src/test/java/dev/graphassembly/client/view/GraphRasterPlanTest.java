package dev.graphassembly.client.view;

import dev.graphassembly.client.view.GraphProjection.Projected;
import dev.graphassembly.client.view.NodePresentation.*;
import dev.graphassembly.core.definition.basic.Edge;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class GraphRasterPlanTest {
    private static final Bounds CANVAS = new Bounds(0,20,320,189);
    private static Surface dot(String id, double x) { return new Surface(id,0,true,false,new Transform(x-5,95,1),10,10); }
    private static Surface preview(String id, double x) { return new Surface(id,1,true,true,new Transform(x-5,95,1),10,10); }

    @Test void geometryReuseKeepsPrimitiveMeshAcrossSelectionOnlyChanges() {
        var path = new GraphEdges.Path(new Edge("a","b",1), List.of(new Projected(20,100,1),new Projected(60,100,1)),true);
        var surfaces = List.of(dot("a",20)); var centers = Map.of("a",new Projected(20,100,1));
        var plan = new GraphRasterPlan(CANVAS);
        plan.update(List.of(path), surfaces, centers);
        var mesh = ((GraphRasterPlan.EdgePixels)plan.commands().getFirst()).mesh();
        assertEquals(1,plan.rebuilds());
        // Selection is paint-only state and deliberately is not an update argument.
        plan.update(List.of(path), surfaces, centers);
        assertSame(mesh, ((GraphRasterPlan.EdgePixels)plan.commands().getFirst()).mesh());
        assertEquals(1,plan.rebuilds()); assertEquals(1,plan.hits());
        plan.update(List.of(path), surfaces, Map.of("a",new Projected(21,100,1)));
        assertEquals(2,plan.rebuilds());
        assertNotSame(mesh, ((GraphRasterPlan.EdgePixels)plan.commands().getFirst()).mesh());
        var changedPath = new GraphEdges.Path(path.edge(),List.of(new Projected(20,100,1),new Projected(61,100,1)),true);
        plan.update(List.of(changedPath),surfaces,centers);
        assertEquals(3,plan.rebuilds());
    }

    @Test void interleavedDotsAndPreviewsKeepSurfaceOrderAndBarriers() {
        var surfaces = List.of(dot("d1",20), preview("p1",50), dot("d2",80), preview("p2",110), dot("d3",140));
        var centers = Map.of("d1",new Projected(20,100,1),"d2",new Projected(80,100,1),"d3",new Projected(140,100,1));
        var plan = new GraphRasterPlan(CANVAS);
        plan.update(List.of(), surfaces, centers);
        assertEquals(List.of("d1","p1","d2","p2","d3"), plan.commands().stream().map(c -> c.surface().id()).toList());
        assertEquals(List.of("pixels","preview","pixels","preview","pixels"),
            plan.commands().stream().map(c -> c instanceof GraphRasterPlan.Preview ? "preview" : "pixels").toList());
        assertEquals(3 * GraphPixelMesh.dot(0,0).retainedBytes(), plan.retainedBytes());
        plan.clear(); assertEquals(0,plan.retainedBytes()); assertTrue(plan.commands().isEmpty());
    }

    @Test void dispatchFlushesBeforeAndAfterEachPreviewAndAtCanvasEnd() {
        var surfaces = List.of(dot("d1",20), preview("p1",50), dot("d2",80), preview("p2",110), dot("d3",140));
        var centers = Map.of("d1",new Projected(20,100,1),"d2",new Projected(80,100,1),"d3",new Projected(140,100,1));
        var plan = new GraphRasterPlan(CANVAS);
        var edge = new GraphEdges.Path(new Edge("a","b",1),
            List.of(new Projected(10,100,1),new Projected(15,100,1)),true);
        plan.update(List.of(edge),surfaces,centers);
        var events = new ArrayList<String>();
        var batches = new ArrayList<List<String>>();
        plan.dispatch(new GraphRasterPlan.Sink() {
            final List<String> pending = new ArrayList<>();
            public void edge(GraphRasterPlan.EdgePixels pixels) { pending.add("edge"); events.add("edge"); }
            public void dot(GraphRasterPlan.DotPixels pixels) { pending.add(pixels.surface().id()); events.add(pixels.surface().id()); }
            public void flush() { events.add("flush"); if (!pending.isEmpty()) { batches.add(List.copyOf(pending)); pending.clear(); } }
            public void beginPreview(Surface surface) { events.add("begin:"+surface.id()); }
            public boolean drawPreview(Surface surface) { events.add("draw:"+surface.id()); return true; }
            public void previewFailure(Surface surface, Throwable failure) { fail("Unexpected preview failure", failure); }
            public void endPreview(Surface surface) { events.add("end:"+surface.id()); }
        });
        assertEquals(List.of("edge","d1","flush","begin:p1","draw:p1","flush","end:p1",
            "d2","flush","begin:p2","draw:p2","flush","end:p2","d3","flush"),events);
        assertEquals(List.of(List.of("edge","d1"),List.of("d2"),List.of("d3")),batches);
    }

    @Test void failedPostPreviewFlushContinuesWithFollowingDot() {
        for (Throwable error : List.of(new IllegalStateException("post-preview"), new LinkageError("post-preview"))) {
        var plan = new GraphRasterPlan(CANVAS);
        plan.update(List.of(),List.of(preview("p1",50),dot("d2",80)),Map.of("d2",new Projected(80,100,1)));
        var events = new ArrayList<String>(); var flushes = new int[1];
        plan.dispatch(new GraphRasterPlan.Sink() {
            public void edge(GraphRasterPlan.EdgePixels pixels) {}
            public void dot(GraphRasterPlan.DotPixels pixels) { events.add("dot:"+pixels.surface().id()); }
            public void flush() {
                events.add("flush");
                if (++flushes[0] == 2) {
                    if (error instanceof LinkageError linkage) throw linkage;
                    throw (RuntimeException)error;
                }
            }
            public void beginPreview(Surface surface) { events.add("begin:"+surface.id()); }
            public boolean drawPreview(Surface surface) { events.add("draw:"+surface.id()); return true; }
            public void previewFailure(Surface surface, Throwable failure) {
                assertSame(error, failure);
                events.add("cleanup:"+surface.id()); events.add("diagnose:"+surface.id());
            }
            public void endPreview(Surface surface) { events.add("end:"+surface.id()); }
        });
        assertEquals(List.of("flush","begin:p1","draw:p1","flush","cleanup:p1","diagnose:p1",
            "end:p1","dot:d2","flush"),events);
        }
    }

    @Test void cachedPreviewUsesCurrentHoverAfterEqualValueReprojection() {
        var original = List.of(preview("lower",50), preview("upper",52), preview("side",90), dot("dot",130));
        var projectedAgain = List.of(preview("lower",50), preview("upper",52), preview("side",90), dot("dot",130));
        var centers = Map.of("dot", new Projected(130,100,1));
        var plan = new GraphRasterPlan(CANVAS);
        assertFalse(plan.update(List.of(), original, centers));
        var cachedCommands = plan.commands();
        var mesh = ((GraphRasterPlan.DotPixels) cachedCommands.getLast()).mesh();
        long bytes = plan.retainedBytes();
        assertTrue(bytes > 0);
        assertEquals(projectedAgain, original);
        assertNotSame(projectedAgain.get(1), original.get(1));
        assertTrue(plan.update(List.of(), projectedAgain, centers));
        assertSame(cachedCommands, plan.commands());
        assertSame(mesh, ((GraphRasterPlan.DotPixels) plan.commands().getLast()).mesh());
        assertEquals(1, plan.hits());
        assertEquals(1, plan.rebuilds());
        assertEquals(bytes, plan.retainedBytes());

        double mouseX = 50.6, mouseY = 101.7;
        var hovered = NodePresentation.hit(projectedAgain, CANVAS, mouseX, mouseY);
        assertSame(projectedAgain.get(1), hovered);
        assertEquals(Map.of(
            "lower", new NodePresentation.PreviewMouse(-10000,-10000),
            "upper", new NodePresentation.PreviewMouse(3,6),
            "side", new NodePresentation.PreviewMouse(-10000,-10000)),
            dispatchedPreviewMouse(plan, hovered, mouseX, mouseY));

        assertNull(NodePresentation.hit(projectedAgain, CANVAS, -1, mouseY));
        assertEquals(Map.of(
            "lower", new NodePresentation.PreviewMouse(-10000,-10000),
            "upper", new NodePresentation.PreviewMouse(-10000,-10000),
            "side", new NodePresentation.PreviewMouse(-10000,-10000)),
            dispatchedPreviewMouse(plan, null, -1, mouseY));
        assertEquals(1, plan.rebuilds());
    }

    private static Map<String, NodePresentation.PreviewMouse> dispatchedPreviewMouse(
        GraphRasterPlan plan, Surface hovered, double mouseX, double mouseY) {
        var mouseById = new LinkedHashMap<String, NodePresentation.PreviewMouse>();
        plan.dispatch(new GraphRasterPlan.Sink() {
            public void edge(GraphRasterPlan.EdgePixels pixels) {}
            public void dot(GraphRasterPlan.DotPixels pixels) {}
            public void flush() {}
            public void beginPreview(Surface surface) {}
            public boolean drawPreview(Surface surface) {
                mouseById.put(surface.id(), NodePresentation.previewMouse(surface, hovered, mouseX, mouseY));
                return true;
            }
            public void previewFailure(Surface surface, Throwable failure) { fail("Unexpected preview failure", failure); }
            public void endPreview(Surface surface) {}
        });
        return mouseById;
    }
}
