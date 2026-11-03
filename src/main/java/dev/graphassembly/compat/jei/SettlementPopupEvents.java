package dev.graphassembly.compat.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Vector3f;
import java.lang.ref.WeakReference;

/** Optional-JEI-safe hook: installed only by a live footer; bound to its last rendered screen/frame. */
final class SettlementPopupEvents {
    private static boolean installed, swallowRelease;
    private static WeakReference<SettlementFooterWidget> active = new WeakReference<>(null);
    private static WeakReference<Screen> screen = new WeakReference<>(null);
    private static long renderedAt;
    private static double left, top, right, bottom;
    static void install() {
        if (installed) return;
        installed = true;
        NeoForge.EVENT_BUS.addListener(SettlementPopupEvents::frame);
        SettlementPopupInputListeners.register(NeoForge.EVENT_BUS, ScreenEvent.KeyPressed.Pre.class, SettlementPopupEvents::key);
        SettlementPopupInputListeners.register(NeoForge.EVENT_BUS, ScreenEvent.MouseButtonPressed.Pre.class, SettlementPopupEvents::mouse);
        SettlementPopupInputListeners.register(NeoForge.EVENT_BUS, ScreenEvent.MouseButtonReleased.Pre.class, SettlementPopupEvents::release);
        SettlementPopupInputListeners.register(NeoForge.EVENT_BUS, ScreenEvent.MouseScrolled.Pre.class, SettlementPopupEvents::scroll);
    }
    static void rendered(SettlementFooterWidget widget, GuiGraphics graphics, int width, int height) {
        if (!widget.popupOpen()) return;
        active = new WeakReference<>(widget); screen = new WeakReference<>(Minecraft.getInstance().screen);
        renderedAt = System.nanoTime();
        var a = graphics.pose().last().pose().transformPosition(new Vector3f(0, 0, 0));
        var b = graphics.pose().last().pose().transformPosition(new Vector3f(width, height, 0));
        left = a.x(); top = a.y(); right = b.x(); bottom = b.y();
    }
    private static SettlementFooterWidget current(Screen current) {
        var widget = active.get();
        return screen.get() == current && System.nanoTime() - renderedAt < 500_000_000L
            && widget != null && widget.popupOpen() ? widget : null;
    }
    private static void frame(ScreenEvent.Render.Pre event) {
        // Same-Screen JEI category/page changes must revoke the previous widget before the next frame.
        active = new WeakReference<>(null);
    }
    private static void key(ScreenEvent.KeyPressed.Pre event) {
        var widget = current(event.getScreen());
        if (widget == null) return;
        var key = com.mojang.blaze3d.platform.InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        if (widget.queryKey(key) || widget.key(event.getKeyCode(), event.getModifiers())) {
            event.setCanceled(true); return;
        }
        var mc = Minecraft.getInstance();
        double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth() - left;
        double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight() - top;
        // Only actual hovered popup ingredients may pass native query keys to JEI.
        if (!widget.nativeQueryKey(key) || widget.slotUnderMouse(x, y).isEmpty()) event.setCanceled(true);
    }
    private static void mouse(ScreenEvent.MouseButtonPressed.Pre event) {
        var widget = current(event.getScreen());
        if (widget == null) return;
        if (event.getMouseX() < left || event.getMouseX() >= right || event.getMouseY() < top || event.getMouseY() >= bottom) {
            widget.closePopup(); swallowRelease = true; event.setCanceled(true);
        }
    }
    private static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (swallowRelease && screen.get() == event.getScreen()) { swallowRelease = false; event.setCanceled(true); }
    }
    private static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        var widget = current(event.getScreen());
        if (widget != null) { widget.scroll(event.getScrollDeltaY()); event.setCanceled(true); }
    }
}
