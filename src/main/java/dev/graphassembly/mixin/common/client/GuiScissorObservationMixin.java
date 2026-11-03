package dev.graphassembly.mixin.common.client;

import dev.graphassembly.client.view.GuiScissorAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
abstract class GuiScissorObservationMixin implements GuiScissorAccess {
    @Unique private ScreenRectangle graphAssembly$clip;
    @Inject(method = "applyScissor", at = @At("HEAD"))
    private void graphAssembly$observe(ScreenRectangle clip, CallbackInfo ci) { graphAssembly$clip = clip; }
    @Override public ScreenRectangle graphAssembly$effectiveScissor() { return graphAssembly$clip; }
}
