package dev.graphassembly.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.graphassembly.client.view.CanvasBackground;
import dev.graphassembly.client.view.GraphGuiLayout;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;

/** Factory Controller-style world-locked PNG tiling, wrapped in the shader with one quad. */
public final class GraphCanvasBackground {
    private static final Bounds AREA = GraphGuiLayout.CANVAS;
    private static ShaderInstance shader;
    private GraphCanvasBackground() {}

    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), GraphGuiReloadListener.id("canvas_background"),
            DefaultVertexFormat.POSITION_TEX_COLOR), value -> shader = value);
    }

    /** The caller clips the canvas. Frame, controls and settlement text are outside this layer. */
    public static void draw(GuiGraphics graphics, double originX, double originY, double zoom) {
        if (shader == null || !GraphGuiAssets.current().canvasBackground()) return;
        var uv = CanvasBackground.uv(AREA, originX, originY, zoom, GraphGuiAssets.CANVAS_TILE_SIZE);
        graphics.flush();
        var previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableDepthTest();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, GraphGuiReloadListener.id(GraphGuiAssets.CANVAS_BACKGROUND_PNG));
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            var matrix = graphics.pose().last().pose();
            // A subdued layer keeps thin graph edges readable while the PNG remains directly editable.
            int tint = GuiColors.color("canvas.background");
            buffer.addVertex(matrix, (float)AREA.left(), (float)AREA.top(), 0).setUv((float)uv.left(), (float)uv.top()).setColor(tint);
            buffer.addVertex(matrix, (float)AREA.left(), (float)AREA.bottom(), 0).setUv((float)uv.left(), (float)uv.bottom()).setColor(tint);
            buffer.addVertex(matrix, (float)AREA.right(), (float)AREA.bottom(), 0).setUv((float)uv.right(), (float)uv.bottom()).setColor(tint);
            buffer.addVertex(matrix, (float)AREA.right(), (float)AREA.top(), 0).setUv((float)uv.right(), (float)uv.top()).setColor(tint);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.setShader(() -> previousShader); RenderSystem.setShaderTexture(0, previousTexture);
            if (!blend) RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest();
        }
    }
}
