package dev.graphassembly.client.gui;

import dev.graphassembly.client.view.GraphPixelMesh;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

/** Submits adjacent legacy pixel quads through the existing GUI RenderType. */
public final class GraphGuiPixelBatch {
    private final GuiGraphics graphics;
    private final Vector3f scratch = new Vector3f();
    private boolean pending;
    private long submissions, vertices;

    public GraphGuiPixelBatch(GuiGraphics graphics) { this.graphics = graphics; }
    public void submit(GraphPixelMesh mesh, int color) {
        if (mesh.pixelCount() == 0) return;
        var matrix = graphics.pose().last().pose();
        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < mesh.pixelCount(); i++) {
            int x = mesh.x(i), y = mesh.y(i);
            vertex(matrix, consumer, x + 1, y + 1, color);
            vertex(matrix, consumer, x + 1, y, color);
            vertex(matrix, consumer, x, y, color);
            vertex(matrix, consumer, x, y + 1, color);
        }
        pending = true;
        vertices += (long)mesh.pixelCount() * 4;
    }
    private void vertex(org.joml.Matrix4f matrix, com.mojang.blaze3d.vertex.VertexConsumer consumer, int x, int y, int color) {
        scratch.set(x, y, 0);
        matrix.transformPosition(scratch);
        consumer.addVertex(scratch.x, scratch.y, scratch.z).setColor(color);
    }
    /** Unconditionally preserves GuiGraphics.flush at each native-preview and canvas boundary. */
    public void flush() {
        graphics.flush();
        if (pending) { submissions++; pending = false; }
    }
    public long submissions() { return submissions; }
    public long vertices() { return vertices; }
}
