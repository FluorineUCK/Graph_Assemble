package dev.graphassembly.client.view;

import dev.graphassembly.client.view.NodePresentation.Transform;

/** Geometry for bridging a standalone Create animation into a sequenced-step preview. */
public final class NativePreviewGeometry {
    private NativePreviewGeometry() {}
    public static final int STEP_PADDING = 12;
    public static final int MILLING_WIDTH = 25 + STEP_PADDING * 2;
    // Create 6.0.10: AnimatedMillstone uses 22 pixels per block. The native
    // sequenced saw uses 25 * .6, deployer/spout use 20 * .75 (all 15).
    private static final double MILLING_BLOCK_SCALE = 22, ASSEMBLY_BLOCK_SCALE = 15;
    // Use the lower machine row in the 84px native layout (beside saw/depot bodies),
    // rather than the upper row reserved for processing heads and ingredient slots.
    private static final int MILLING_Y = 66;

    public static Transform milling(int layoutWidth) {
        double scale = ASSEMBLY_BLOCK_SCALE / MILLING_BLOCK_SCALE;
        return new Transform(layoutWidth / 2.0 - 12 * scale, MILLING_Y, scale);
    }
}
