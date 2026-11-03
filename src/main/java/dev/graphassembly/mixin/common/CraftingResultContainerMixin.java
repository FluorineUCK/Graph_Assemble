package dev.graphassembly.mixin.common;

import dev.graphassembly.integration.crafting.CraftingPreview;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultContainer.class)
public abstract class CraftingResultContainerMixin implements CraftingPreview {
    @Unique private boolean graphAssembly$preview;
    @Override public boolean graphAssembly$preview(){return graphAssembly$preview;}
    @Override public void graphAssembly$preview(boolean preview){graphAssembly$preview=preview;}
    @Inject(method="removeItem",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$remove(int slot,int count,CallbackInfoReturnable<ItemStack> cir) {
        if(graphAssembly$preview)cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="removeItemNoUpdate",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$removeWithoutUpdate(int slot,CallbackInfoReturnable<ItemStack> cir) {
        if(graphAssembly$preview)cir.setReturnValue(ItemStack.EMPTY);
    }
}
