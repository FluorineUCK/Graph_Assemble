package dev.graphassembly.mixin.common;

import dev.graphassembly.integration.smithing.SmithingTransaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** The two methods are inherited, not declared by SmithingMenu. Exact-menu guard is in transaction. */
@Mixin(ItemCombinerMenu.class)
public abstract class ItemCombinerMenuMixin {
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$quick(Player player,int slot,CallbackInfoReturnable<ItemStack> cir) {
        if(dev.graphassembly.integration.anvil.AnvilTransaction.click((AbstractContainerMenu)(Object)this,slot,0,ClickType.QUICK_MOVE,player)
            || SmithingTransaction.click((AbstractContainerMenu)(Object)this,slot,0,ClickType.QUICK_MOVE,player))cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="removed",at=@At("HEAD"))
    private void graphAssembly$close(Player player,CallbackInfo ci){dev.graphassembly.integration.anvil.AnvilTransaction.close((AbstractContainerMenu)(Object)this);SmithingTransaction.close((AbstractContainerMenu)(Object)this);}
}
