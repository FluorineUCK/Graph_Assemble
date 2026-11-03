package dev.graphassembly.mixin.jei;

import dev.graphassembly.compat.jei.NativeSlotObservation;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.common.gui.JeiTooltip", remap = false)
abstract class NativeTooltipObservationMixin {
    @Shadow private ITypedIngredient<?> typedIngredient;
    @Inject(method = "draw(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$collect(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (NativeSlotObservation.tooltip((ITooltipBuilder)this, typedIngredient)) ci.cancel();
    }
}
