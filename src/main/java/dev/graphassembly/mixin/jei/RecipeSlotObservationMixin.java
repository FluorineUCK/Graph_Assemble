package dev.graphassembly.mixin.jei;

import dev.graphassembly.compat.jei.NativeSlotObservation;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
abstract class RecipeSlotObservationMixin {
    @Inject(method = "draw(Lnet/minecraft/client/gui/GuiGraphics;Z)V", at = @At("HEAD"))
    private void graphAssembly$observe(GuiGraphics graphics, boolean hovered, CallbackInfo ci) {
        NativeSlotObservation.drawn((IRecipeSlotDrawable)this, graphics);
    }
}
