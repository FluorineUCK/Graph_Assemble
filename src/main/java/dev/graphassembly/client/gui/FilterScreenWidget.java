package dev.graphassembly.client.gui;

import dev.graphassembly.client.view.FilterScreenLayout;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A screen-space event/focus facade for a native Create widget in logical panel coordinates. */
public final class FilterScreenWidget extends AbstractWidget {
    private final AbstractSimiWidget widget;
    private final FilterScreenLayout layout;
    public FilterScreenWidget(AbstractSimiWidget widget, FilterScreenLayout layout) {
        this(widget, layout, layout.screenPixels(new Bounds(widget.getX(), widget.getY(),
            widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight())));
    }
    private FilterScreenWidget(AbstractSimiWidget widget, FilterScreenLayout layout, Bounds b) {
        super((int)b.left(), (int)b.top(), (int)(b.right()-b.left()), (int)(b.bottom()-b.top()), Component.empty());
        this.widget = widget; this.layout = layout;
    }
    public AbstractSimiWidget widget() { return widget; }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        var point = layout.toLocal(mx,my); var transform = layout.transform();
        g.pose().pushPose();
        g.pose().translate(transform.x(), transform.y(), 0);
        g.pose().scale((float)transform.scale(), (float)transform.scale(), 1);
        try { widget.render(g, (int)Math.floor(point.x()), (int)Math.floor(point.y()), partial); }
        finally { g.flush(); g.pose().popPose(); }
    }
    @Override public boolean isMouseOver(double x, double y) {
        var p = layout.toLocal(x,y); return widget.isMouseOver(p.x(),p.y());
    }
    @Override public void mouseMoved(double x, double y) { var p=layout.toLocal(x,y); widget.mouseMoved(p.x(),p.y()); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        var p=layout.toLocal(x,y); return widget.mouseClicked(p.x(),p.y(),button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        var p=layout.toLocal(x,y); return widget.mouseReleased(p.x(),p.y(),button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        var p=layout.toLocal(x,y); return widget.mouseDragged(p.x(),p.y(),button,layout.localDelta(dx),layout.localDelta(dy));
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (!widget.active || !widget.visible || !isMouseOver(x,y)) return false;
        var p=layout.toLocal(x,y); return widget.mouseScrolled(p.x(),p.y(),sx,sy);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) { return widget.keyPressed(key,scan,mods); }
    @Override public boolean keyReleased(int key, int scan, int mods) { return widget.keyReleased(key,scan,mods); }
    @Override public boolean charTyped(char ch, int mods) { return widget.charTyped(ch,mods); }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); widget.setFocused(focused); }
    @Override public boolean isFocused() { return widget.isFocused(); }
    @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return widget.nextFocusPath(event) == null ? null : ComponentPath.leaf(this);
    }
    @Override public int getTabOrderGroup() { return widget.getTabOrderGroup(); }
    @Override public NarrationPriority narrationPriority() { return widget.narrationPriority(); }
    @Override public boolean isActive() { return widget.visible && widget.active; }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { widget.updateNarration(output); }
}
