package dev.graphassembly.mixin.vanilla;

import dev.graphassembly.integration.vanilla.crafting.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class CraftingMenuStateMixin implements CraftingMenuState {
    @Unique private CraftingTransaction.Context graphAssembly$crafting;
    @Override public CraftingTransaction.Context graphAssembly$crafting(){return graphAssembly$crafting;}
    @Override public void graphAssembly$crafting(CraftingTransaction.Context context){graphAssembly$crafting=context;}
    @Inject(method="clicked",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$click(int slot,int button,ClickType type,Player player,CallbackInfo ci) {
        if(dev.graphassembly.integration.vanilla.anvil.AnvilTransaction.click((AbstractContainerMenu)(Object)this,slot,button,type,player)
            || dev.graphassembly.integration.vanilla.stonecutting.StonecuttingTransaction.click((AbstractContainerMenu)(Object)this,slot,button,type,player)
            || dev.graphassembly.integration.vanilla.smithing.SmithingTransaction.click((AbstractContainerMenu)(Object)this,slot,button,type,player)
            || CraftingTransaction.click((AbstractContainerMenu)(Object)this,slot,button,type,player))ci.cancel();
    }
    @Inject(method="setData",at=@At("TAIL"))
    private void graphAssembly$stonecuttingData(int index,int value,CallbackInfo ci) {
        dev.graphassembly.integration.vanilla.stonecutting.StonecuttingTransaction.synchronizedSelection((AbstractContainerMenu)(Object)this,index,value);
    }
}
