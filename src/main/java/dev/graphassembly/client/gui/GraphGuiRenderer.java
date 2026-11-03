package dev.graphassembly.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.world.item.ItemStack;
import dev.graphassembly.client.view.FilterScreenLayout;
import static dev.graphassembly.client.gui.GraphGuiAssets.*;

/** The sole owner of Create fallback atlas details. Layout and events stay in callers. */
public final class GraphGuiRenderer {
    private static final ResourceLocation ATLAS = GraphGuiReloadListener.id(ATLAS_PNG);
    private GraphGuiRenderer() {}
    public static int color(String key) { return GuiColors.color(key); }
    /** Create StockKeeperRequestScreen's native 5px thumb; never reuse a horizontal choice strip. */
    public static void scrollbar(GuiGraphics g, Bounds thumb) {
        int x = (int)thumb.left(), y = (int)thumb.top(), height = (int)(thumb.bottom() - thumb.top());
        if (thumb.right() <= thumb.left() || height < 9) return;
        var pad = AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
        g.blit(pad.location, x, y, pad.getWidth(), height, pad.getStartX(), pad.getStartY(),
            pad.getWidth(), pad.getHeight(), 256, 256);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(g, x, y);
        if (height > 16) AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(g, x, y + height / 2 - 4);
        AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(g, x, y + height - 5);
    }
    public static int filterColor(String role) {
        boolean fallback = !current().available().contains(Sprite.FILTER_PANEL);
        return color("filter." + (fallback && !role.equals("choice") && !role.equals("error") ? "fallback." : "") + role);
    }
    public static void filterPreview(GuiGraphics g, ItemStack editingStack, FilterScreenLayout.Preview preview) {
        g.flush();
        GuiGameElement.of(editingStack).<GuiGameElement.GuiRenderBuilder>at((float)preview.x(), (float)preview.y(), (float)preview.z())
            .scale(preview.scale()).render(g);
    }
    /** The artist's separated filter pieces are assembled without stretching the 2px striped body. */
    public static void filterPanel(GuiGraphics g, FilterScreenLayout layout) {
        if (!current().available().contains(Sprite.FILTER_PANEL)) {
            sprite(g, Sprite.FILTER_PANEL, layout.panel());
            return;
        }
        sprite(g, Sprite.FILTER_PANEL, layout.panelHeader());
        sprite(g, Sprite.FILTER_PANEL_UPPER, layout.panelUpper());
        for (var tile : layout.panelMiddleTiles()) {
            int height = (int)(tile.bottom() - tile.top());
            if (height == Sprite.FILTER_PANEL_MIDDLE.height()) sprite(g, Sprite.FILTER_PANEL_MIDDLE, tile);
            else {
                g.flush();
                boolean blending = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                try {
                    g.blit(ATLAS, (int)tile.left(), (int)tile.top(), (int)(tile.right() - tile.left()), height,
                        (float)Sprite.FILTER_PANEL_MIDDLE.x(), (float)Sprite.FILTER_PANEL_MIDDLE.y(),
                        Sprite.FILTER_PANEL_MIDDLE.width(), height, ATLAS_WIDTH, ATLAS_HEIGHT);
                } finally { if (!blending) RenderSystem.disableBlend(); }
            }
        }
        sprite(g, Sprite.FILTER_PANEL_LOWER, layout.panelLower());
        sprite(g, Sprite.FILTER_PANEL_FOOTER, layout.panelFooter());
    }
    public static void sprite(GuiGraphics g, Sprite sprite, Bounds rect) {
        sprite(g, sprite, (int)rect.left(), (int)rect.top(), (int)(rect.right() - rect.left()), (int)(rect.bottom() - rect.top()));
    }
    public static void sprite(GuiGraphics g, Sprite sprite, int x, int y, int width, int height) {
        if (current().available().contains(sprite)) {
            // Direct PNG drawing, like Create's AllGuiTextures. Transparent icons need blending.
            g.flush();
            boolean blending = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                for (var part : GuiNineSlice.slices(sprite, width, height))
                    g.blit(ATLAS, x + part.x(), y + part.y(), part.width(), part.height(),
                        (float)part.u(), (float)part.v(), part.sourceWidth(), part.sourceHeight(), ATLAS_WIDTH, ATLAS_HEIGHT);
            }
            finally { if (!blending) RenderSystem.disableBlend(); }
            return;
        }
        switch (sprite.fallback()) {
            case SOLID -> g.fill(x, y, x + width, y + height, sprite.color());
            case FILTER_PANEL -> {
                // Native Attribute Filter chrome, excluding its fixed ghost inventory slots.
                var texture = AllGuiTextures.ATTRIBUTE_FILTER;
                nativeStrip(g, texture.location, x, y, width, 20, 0, 99, 241, 20, 4);
                nativeStrip(g, texture.location, x, y + 20, width, height - 26, 0, 119, 241, 1, 4);
                nativeStrip(g, texture.location, x, y + height - 6, width, 6, 0, 178, 241, 6, 4);
            }
            case FILTER_CHOICE -> nativeStrip(g, AllGuiTextures.ATTRIBUTE_FILTER.location,
                x, y, width, height, 39, 125, 137, 18, 8);
            case JEI_SLOT -> AllGuiTextures.JEI_SLOT.render(g, x, y);
            case ADD -> AllIcons.I_ADD.render(g, x, y);
            case TRASH -> AllIcons.I_TRASH.render(g, x, y);
            case CONFIRM -> AllIcons.I_CONFIRM.render(g, x, y);
            case PLANE, SPHERE -> g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                sprite.fallback() == Fallback.PLANE ? "2D" : "3D", x + 2, y + 4, color("text.primary"), false);
        }
    }

    private static void nativeStrip(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height,
                                    int u, int v, int sourceWidth, int sourceHeight, int border) {
        if (width <= 0 || height <= 0) return;
        int left = Math.min(border, (width + 1) / 2), right = Math.min(border, width / 2);
        g.blit(texture, x, y, left, height, u, (float)v, border, sourceHeight, 256, 256);
        if (width > left + right) g.blit(texture, x + left, y, width - left - right, height,
            (float)(u + border), (float)v, sourceWidth - border * 2, sourceHeight, 256, 256);
        if (right > 0) g.blit(texture, x + width - right, y, right, height,
            (float)(u + sourceWidth - border), (float)v, border, sourceHeight, 256, 256);
    }

    /** Custom PNG glyph at Create's native +(1,1) inset; button chrome and states remain native. */
    public static IconButton textButton(Bounds rect, net.minecraft.network.chat.Component text) {
        int width = (int)(rect.right() - rect.left()), height = (int)(rect.bottom() - rect.top());
        return new IconButton((int)rect.left(), (int)rect.top(), width, height, (g, x, y) -> {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String label = ButtonLabel.fit(text.getString(), Math.max(0, width - 6), font::width);
            g.drawString(font, label, x - 1 + (width - font.width(label)) / 2,
                y - 1 + (height - font.lineHeight) / 2, color("text.primary"), false);
        }) {
            @Override protected void drawBg(GuiGraphics g, AllGuiTextures texture) {
                nativeStrip(g, texture.location, getX(), getY(), getWidth(), getHeight(),
                    texture.getStartX(), texture.getStartY(), texture.getWidth(), texture.getHeight(), 3);
            }
            @Override protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY, float partial) {}
        };
    }

    public static IconButton iconButton(int x, int y, Sprite icon) {
        return iconButton(x, y, (g, ix, iy) -> sprite(g, icon, ix, iy, 16, 16));
    }

    /** Inherit Create's disabled > pressed > hovered > green > normal priority, including its PNGs. */
    public static IconButton iconButton(int x, int y, ScreenElement icon) {
        return new IconButton(x, y, icon) {
            // The owning Screen or JEI draws tooltips once, after returning to screen coordinates.
            @Override protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY, float partial) {}
        };
    }
}
