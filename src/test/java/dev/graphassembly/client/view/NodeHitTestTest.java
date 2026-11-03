package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NodeHitTestTest {
    @Test void motionAndDragCallbacksForSamePositionMustNotPanTwice() {
        var gesture = new NodePresentation.Gesture(); gesture.begin(20, 40);
        assertArrayEquals(new double[]{10, 5}, gesture.move(30, 45));
        assertArrayEquals(new double[]{0, 0}, gesture.move(30, 45));
        assertFalse(gesture.release(30, 45));
        assertArrayEquals(new double[]{0, 0}, gesture.move(80, 90));
    }
    @Test void nearestVisibleNodeOwnsItsSlotsAndOccludesOtherNodes() {
        var far = new NodePresentation.Surface("far", .2, true, true, new NodePresentation.Transform(40, 30, 2), 50, 84);
        var near = new NodePresentation.Surface("near", .9, true, true, new NodePresentation.Transform(40, 30, 2), 50, 84);
        var back = new NodePresentation.Surface("back", -1, false, true, new NodePresentation.Transform(40, 30, 2), 50, 84);
        var viewport = new NodePresentation.Bounds(0, 20, 320, 189);
        assertEquals(near, NodePresentation.hit(List.of(near, far, back), viewport, 72, 68));
        assertEquals(16, near.transform().localX(72));
        assertEquals(19, near.transform().localY(68));
        assertNull(NodePresentation.hit(List.of(near), viewport, 72, 191));
        assertNull(NodePresentation.hit(List.of(back), viewport, 72, 68));
    }
    @Test void dragReleaseDoesNotClickAndNextClickStillWorks() {
        var gesture = new NodePresentation.Gesture();
        gesture.begin(20, 40); gesture.drag(10, 0);
        assertFalse(gesture.release(20, 40)); // even if returning to the starting point
        gesture.begin(20, 40);
        assertTrue(gesture.release(21, 40));
        assertFalse(gesture.release(21, 40));
    }
}
