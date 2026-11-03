package dev.graphassembly.client.view;

import net.minecraft.client.gui.navigation.ScreenRectangle;

/** Read-only effective clip, after Minecraft has intersected all nested scissors. */
public interface GuiScissorAccess {
    ScreenRectangle graphAssembly$effectiveScissor();
}
