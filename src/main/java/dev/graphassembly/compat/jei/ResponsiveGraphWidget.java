package dev.graphassembly.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import dev.graphassembly.client.view.RecipeViewport;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.*;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import java.util.*;

/** Footer is unscaled; only graph coordinates use the canvas affine transform. */
final class ResponsiveGraphWidget implements ISlottedRecipeWidget, IJeiInputHandler {
    private final GraphWidget graph;
    private final SettlementFooterWidget footer;
    private final RecipeViewport viewport;
    ResponsiveGraphWidget(GraphWidget graph, RecipeViewport viewport) {
        this.graph = graph; this.viewport = viewport;
        footer = new SettlementFooterWidget(graph.definition(), graph.viewState(), viewport.width(), viewport.canvasHeight());
    }
    List<IRecipeSlotDrawable> allSlots() { return footer.allSlots(); }
    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(0, 0, viewport.width(), viewport.height()); }
    private double graphX(double x) { return viewport.local(x - viewport.canvasX()); }
    @Override public void tick() { graph.tick(); }
    @Override public void drawWidget(GuiGraphics graphics, double x, double y) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(viewport.canvasX(), 0, 0);
            graphics.pose().scale((float)viewport.scale(), (float)viewport.scale(), 1);
            graph.drawWidget(graphics, footer.popupOpen() ? -1 : graphX(x), footer.popupOpen() ? -1 : viewport.local(y));
        } finally { graphics.pose().popPose(); }
        footer.draw(graphics, x, y);
    }
    @Override public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double x, double y) { return footer.slotUnderMouse(x, y); }
    @Override public void getTooltip(ITooltipBuilder tooltip, double x, double y) {
        if (footer.popupOpen() || y >= viewport.canvasHeight()) footer.tooltip(tooltip, x, y);
        else graph.getTooltip(tooltip, graphX(x), viewport.local(y));
    }
    @Override public boolean handleInput(double x, double y, IJeiUserInput input) {
        boolean popup = footer.popupOpen();
        if (popup || y >= viewport.canvasHeight() || input.getKey().getType() != InputConstants.Type.MOUSE) {
            if (footer.input(x, y, input)) return true;
            if (popup || y >= viewport.canvasHeight()) return false;
        }
        return graph.handleInput(graphX(x), viewport.local(y), input);
    }
    @Override public void handleMouseMoved(double x, double y) {
        footer.moved(x, y);
        if (!footer.popupOpen() && y < viewport.canvasHeight()) graph.handleMouseMoved(graphX(x), viewport.local(y));
    }
    @Override public boolean handleMouseDragged(double x, double y, InputConstants.Key key, double dx, double dy) {
        footer.moved(x, y);
        if (footer.popupOpen() || y >= viewport.canvasHeight()) return true;
        return graph.handleMouseDragged(graphX(x), viewport.local(y), key, viewport.local(dx), viewport.local(dy));
    }
    @Override public boolean handleMouseScrolled(double x, double y, double dx, double dy) {
        if (footer.scroll(dy)) return true;
        return y < viewport.canvasHeight() && graph.handleMouseScrolled(graphX(x), viewport.local(y), dx, dy);
    }
}
