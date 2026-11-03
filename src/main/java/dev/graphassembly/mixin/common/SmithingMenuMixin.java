package dev.graphassembly.mixin.common;

import dev.graphassembly.integration.smithing.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu implements SmithingAccess {
    protected SmithingMenuMixin(MenuType<?> type,int id,Inventory inventory,ContainerLevelAccess access){super(type,id,inventory,access);}
    @Shadow private RecipeHolder<SmithingRecipe> selectedRecipe;
    @Unique private SmithingTransaction.Context graphAssembly$smithing;
    public SmithingTransaction.Context graphAssembly$smithing(){return graphAssembly$smithing;}
    public void graphAssembly$smithing(SmithingTransaction.Context context){graphAssembly$smithing=context;}
    public Container graphAssembly$inputs(){return inputSlots;}
    public ResultContainer graphAssembly$result(){return resultSlots;}
    public void graphAssembly$clearRecipe(){selectedRecipe=null;}
    public void graphAssembly$sound(){access.execute((level,pos)->level.levelEvent(1044,pos,0));}
    @Inject(method="<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",at=@At("TAIL"))
    private void graphAssembly$init(int id,Inventory inventory,ContainerLevelAccess access,CallbackInfo ci){SmithingTransaction.initialize((SmithingMenu)(Object)this,inventory.player);}
    @Inject(method="createInputSlotDefinitions",at=@At("RETURN"),cancellable=true)
    private void graphAssembly$slots(CallbackInfoReturnable<ItemCombinerMenuSlotDefinition> cir) {
        var original=cir.getReturnValue();var builder=ItemCombinerMenuSlotDefinition.create();
        for(var slot:original.getSlots())builder.withSlot(slot.slotIndex(),slot.x(),slot.y(),stack->SmithingTransaction.admits((SmithingMenu)(Object)this,slot.slotIndex(),stack,slot.mayPlace()));
        var result=original.getResultSlot();cir.setReturnValue(builder.withResultSlot(result.slotIndex(),result.x(),result.y()).build());
    }
    @Inject(method="createResult",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$result(CallbackInfo ci){if(SmithingTransaction.update((SmithingMenu)(Object)this))ci.cancel();}
    @Inject(method="mayPickup",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$pickup(Player player,boolean hasItem,CallbackInfoReturnable<Boolean> cir){if(SmithingTransaction.guarded((SmithingMenu)(Object)this))cir.setReturnValue(false);}
    @Inject(method="onTake",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$take(Player player,ItemStack stack,CallbackInfo ci){if(SmithingTransaction.guarded((SmithingMenu)(Object)this))ci.cancel();}
    @Inject(method="canMoveIntoInputSlots",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$canRoute(ItemStack stack,CallbackInfoReturnable<Boolean> cir){var route=SmithingTransaction.route((SmithingMenu)(Object)this,stack);if(route!=null)cir.setReturnValue(route>=0);}
    @Inject(method="getSlotToQuickMoveTo",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$route(ItemStack stack,CallbackInfoReturnable<Integer> cir){var route=SmithingTransaction.route((SmithingMenu)(Object)this,stack);if(route!=null)cir.setReturnValue(Math.max(0,route));}
}
