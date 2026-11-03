package dev.graphassembly.compat.jei;

import dev.graphassembly.client.GraphFilterScreen;
import dev.graphassembly.client.view.FilterScreenLayout;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import java.util.List;

/** JEI stays optional: the Screen and its pure layout do not refer back to this adapter. */
public final class GraphFilterGuiHandler implements IScreenHandler<GraphFilterScreen>, IGlobalGuiHandler {
    // JEI IngredientListOverlayLayout reserves 6 px margins and a 20 px config button.
    private static final int MIN_SIDEBAR_WIDTH = 6 * 2 + 20;
    @Override public IGuiProperties apply(GraphFilterScreen screen) { return properties(screen.screenLayout()); }
    @Override public List<Rect2i> getGuiExtraAreas() {
        return Minecraft.getInstance().screen instanceof GraphFilterScreen screen ? extraAreas(screen.screenLayout()) : List.of();
    }
    static IGuiProperties properties(FilterScreenLayout layout) {
        if (layout == null) return null;
        // Extra areas protect ingredient slots, not JEI's bottom search field. Reserve preview too.
        var area = layout.screenAreaForSidebar(MIN_SIDEBAR_WIDTH);
        if (area == null) return null;
        var bounds = rect(area);
        return new Properties(GraphFilterScreen.class, bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(),
            layout.screenWidth(), layout.screenHeight());
    }
    static List<Rect2i> extraAreas(FilterScreenLayout layout) {
        return FilterScreenLayout.screenExtraAreas(layout).stream().map(GraphFilterGuiHandler::rect).toList();
    }
    private static Rect2i rect(Bounds b) { return new Rect2i((int)b.left(),(int)b.top(),(int)(b.right()-b.left()),(int)(b.bottom()-b.top())); }
    private record Properties(Class<? extends Screen> screenClass, int guiLeft, int guiTop, int guiXSize, int guiYSize,
                              int screenWidth, int screenHeight) implements IGuiProperties {}
}
