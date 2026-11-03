package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePreviewGeometryTest {
    @Test void normalizedMillstoneHasTheSamePaddedStepWidthAndKeepsItsModelAnchorCentered() {
        int width = NativePreviewGeometry.MILLING_WIDTH;
        assertEquals(25 + 12 * 2, width, "Do not keep the oversized standalone hit/edge rectangle after shrinking the model");
        var mill = NativePreviewGeometry.milling(width);
        assertEquals(width / 2.0, mill.x() + 12 * mill.scale(), 1e-8,
            "Apply the old model anchor offset in model space, not unscaled layout space");
    }

    @Test void millstoneUsesTheLowerMachineRowRatherThanTheUpperToolRow() {
        var mill = NativePreviewGeometry.milling(NativePreviewGeometry.MILLING_WIDTH);
        // AnimatedMillstone adds its own 18px Y offset inside the bridge scale.
        double modelAnchor = mill.y() + 18 * mill.scale();
        assertTrue(modelAnchor >= 76 && modelAnchor <= 80,
            "The body and shadow belong beside the saw/depot row, not the upper processing heads");
        assertTrue(modelAnchor < 84, "Keep the visible body inside the native layout's hit rectangle");
    }

    @Test void millstoneAndSawUseTheSameBlockScaleAtEveryCanvasAndWindowZoom() {
        // Actual Create 6.0.10 draw chain: AnimatedMillstone=22;
        // AssemblyCutting scales AnimatedSaw's 25 by .6 before graph/window zoom.
        var mill = NativePreviewGeometry.milling(NativePreviewGeometry.MILLING_WIDTH);
        for (double window : new double[]{1, 168.0 / 228, 108.0 / 228})
            for (double zoom : new double[]{.25, 1.25, Math.pow(1.12, 4), 3, 4.5}) {
                double millBlock = 22 * mill.scale() * zoom * window;
                double sawBlock = 25 * .6 * zoom * window;
                assertEquals(sawBlock, millBlock, 1e-8,
                    "The standalone millstone must not stay 46.7% larger than the sequenced saw");
            }
    }
}
