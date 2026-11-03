package dev.graphassembly.client.view;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.graphassembly.client.view.NodePresentation.Bounds;

class SlotGeometryTest {
    @Test void outerScaleIsRemovedButNativeWidgetMovementAndClipRemain() {
        var root = new Matrix4f().translate(120, 70, 0).scale(.75f);
        var draw = new Matrix4f(root).translate(40, 20, 0).scale(1.25f);
        var local = new Matrix4f(root).invert().mul(draw);
        assertEquals(new Bounds(42.5, 25, 62.5, 45), SlotGeometry.transform(local, new Bounds(2, 4, 18, 20)));
        assertNull(SlotGeometry.transform(new Matrix4f().rotateZ(.4f), new Bounds(0, 0, 16, 16)));
        assertNull(SlotGeometry.transform(new Matrix4f().scale(0), new Bounds(0, 0, 16, 16)));
    }
}
