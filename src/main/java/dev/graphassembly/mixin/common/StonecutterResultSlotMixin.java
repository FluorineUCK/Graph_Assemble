package dev.graphassembly.mixin.common;

import dev.graphassembly.integration.crafting.CraftingTransaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native anonymous result slot is not a ResultSlot. Direct onTake cannot consume a preview. */
@Mixin(targets="net.minecraft.world.inventory.StonecutterMenu$2")
public abstract class StonecutterResultSlotMixin {
    @Inject(method="onTake",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$take(Player player,ItemStack stack,CallbackInfo ci){
        if(CraftingTransaction.protectedPreview(((Slot)(Object)this).container))ci.cancel();
    }
}
