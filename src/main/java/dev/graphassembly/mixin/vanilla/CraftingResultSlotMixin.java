package dev.graphassembly.mixin.vanilla;

import dev.graphassembly.integration.vanilla.crafting.CraftingTransaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {
    @Inject(method="remove",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$remove(int amount,CallbackInfoReturnable<ItemStack> cir) {
        if(CraftingTransaction.protectedPreview(((net.minecraft.world.inventory.Slot)(Object)this).container))cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="onTake",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$take(Player owner,ItemStack stack,CallbackInfo ci) {
        if(CraftingTransaction.protectedPreview(((net.minecraft.world.inventory.Slot)(Object)this).container))ci.cancel();
    }
}
