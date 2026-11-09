package dev.graphassembly.mixin.vanilla;

import dev.graphassembly.integration.vanilla.crafting.CraftingTransaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$quickMove(Player player,int slot,CallbackInfoReturnable<ItemStack> cir) {
        if(CraftingTransaction.click((AbstractContainerMenu)(Object)this,slot,0,ClickType.QUICK_MOVE,player))cir.setReturnValue(ItemStack.EMPTY);
    }
}
