package dev.graphassembly.mixin.vanilla;

import dev.graphassembly.integration.vanilla.crafting.CraftingTransaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method="slotChangedCraftingGrid",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$grid(AbstractContainerMenu menu,Level level,Player player,CraftingContainer grid,
                                           ResultContainer result,RecipeHolder<CraftingRecipe> previous,CallbackInfo ci) {
        if(CraftingTransaction.update(menu,player,grid,result))ci.cancel();
    }
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$quickMove(Player player,int slot,CallbackInfoReturnable<ItemStack> cir) {
        if(CraftingTransaction.click((AbstractContainerMenu)(Object)this,slot,0,ClickType.QUICK_MOVE,player))cir.setReturnValue(ItemStack.EMPTY);
    }
}
