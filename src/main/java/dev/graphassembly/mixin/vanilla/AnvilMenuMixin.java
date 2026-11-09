package dev.graphassembly.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.graphassembly.integration.vanilla.anvil.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Whole native calculation boundary, including upstream cancellable HEAD handlers. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu implements AnvilAccess {
    protected AnvilMenuMixin(MenuType<?> type,int id,Inventory inventory,ContainerLevelAccess access){super(type,id,inventory,access);}
    @Shadow private String itemName;
    @Shadow public int repairItemCountCost;
    @Shadow protected abstract boolean mayPickup(Player player,boolean hasItem);
    @Shadow protected abstract void onTake(Player player,ItemStack result);
    @Unique private AnvilTransaction.Context graphAssembly$anvil;
    public AnvilTransaction.Context graphAssembly$anvil(){return graphAssembly$anvil;}
    public void graphAssembly$anvil(AnvilTransaction.Context context){graphAssembly$anvil=context;}
    public Container graphAssembly$anvilInputs(){return inputSlots;}
    public ResultContainer graphAssembly$anvilResult(){return resultSlots;}
    public String graphAssembly$anvilName(){return itemName;}
    public int graphAssembly$anvilMaterials(){return repairItemCountCost;}
    public void graphAssembly$anvilCost(int cost,int materials){((AnvilMenu)(Object)this).setMaximumCost(cost);repairItemCountCost=materials;}
    public boolean graphAssembly$anvilPickup(Player player){return mayPickup(player,true);}
    public void graphAssembly$anvilTake(Player player,ItemStack result){onTake(player,result);}
    @Inject(method="<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",at=@At("TAIL"))
    private void graphAssembly$init(int id,Inventory inventory,ContainerLevelAccess access,CallbackInfo ci){AnvilTransaction.initialize((AnvilMenu)(Object)this,inventory.player);}
    @WrapMethod(method="createResult")
    private void graphAssembly$result(Operation<Void> original){AnvilTransaction.result((AnvilMenu)(Object)this,()->original.call());}
    @Inject(method="mayPickup",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$pickup(Player player,boolean hasItem,CallbackInfoReturnable<Boolean> cir){if(AnvilTransaction.guardedPickup((AnvilMenu)(Object)this,player,hasItem))cir.setReturnValue(false);}
    @Inject(method="onTake",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$take(Player player,ItemStack stack,CallbackInfo ci){if(AnvilTransaction.guardedTake((AnvilMenu)(Object)this))ci.cancel();}
}
