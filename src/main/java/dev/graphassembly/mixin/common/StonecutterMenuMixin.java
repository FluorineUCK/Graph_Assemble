package dev.graphassembly.mixin.common;

import dev.graphassembly.integration.stonecutting.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;

@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin implements StonecuttingAccess {
    @Shadow @Final private DataSlot selectedRecipeIndex;
    @Shadow private List<RecipeHolder<StonecutterRecipe>> recipes;
    @Shadow @Final private ResultContainer resultContainer;
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow long lastSoundTime;
    @Shadow private ItemStack input;
    @Shadow Runnable slotUpdateListener;
    @Unique private StonecuttingTransaction.Context graphAssembly$stonecutting;
    public StonecuttingTransaction.Context graphAssembly$stonecutting(){return graphAssembly$stonecutting;}
    public void graphAssembly$stonecutting(StonecuttingTransaction.Context context){graphAssembly$stonecutting=context;}
    public void graphAssembly$list(List<RecipeHolder<StonecutterRecipe>> list){recipes=new java.util.ArrayList<>(list);}
    public int graphAssembly$selected(){return selectedRecipeIndex.get();}
    public void graphAssembly$selected(int index){selectedRecipeIndex.set(index);}
    public ResultContainer graphAssembly$result(){return resultContainer;}
    public void graphAssembly$resetNativeInput(){input=ItemStack.EMPTY;}
    public void graphAssembly$notifyChoices(){slotUpdateListener.run();}
    public void graphAssembly$sound(){access.execute((level,pos)->{
        long now=level.getGameTime();if(lastSoundTime!=now){level.playSound(null,pos,net.minecraft.sounds.SoundEvents.UI_STONECUTTER_TAKE_RESULT,net.minecraft.sounds.SoundSource.BLOCKS,1,1);lastSoundTime=now;}
    });}
    @Inject(method="<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",at=@At("TAIL"))
    private void graphAssembly$init(int id,Inventory inventory,ContainerLevelAccess access,CallbackInfo ci){StonecuttingTransaction.initialize((StonecutterMenu)(Object)this,inventory.player);}
    @Inject(method="slotsChanged",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$changed(Container container,CallbackInfo ci){if(StonecuttingTransaction.update((StonecutterMenu)(Object)this))ci.cancel();}
    @Inject(method="setupResultSlot",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$result(CallbackInfo ci){if(StonecuttingTransaction.update((StonecutterMenu)(Object)this))ci.cancel();}
    @Inject(method="clickMenuButton",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$select(Player player,int index,CallbackInfoReturnable<Boolean> cir){var result=StonecuttingTransaction.select((StonecutterMenu)(Object)this,index);if(result!=null)cir.setReturnValue(result);}
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$quick(Player player,int slot,CallbackInfoReturnable<ItemStack> cir){
        var menu=(StonecutterMenu)(Object)this;
        if(StonecuttingTransaction.click(menu,slot,0,ClickType.QUICK_MOVE,player) || StonecuttingTransaction.quickInventory(menu,slot,player))cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method={"getRecipes","getNumRecipes","hasInputItem"},at=@At("HEAD"))
    private void graphAssembly$refresh(CallbackInfoReturnable<?> cir){StonecuttingTransaction.update((StonecutterMenu)(Object)this);}
    @Inject(method="removed",at=@At("HEAD"))
    private void graphAssembly$close(Player player,CallbackInfo ci){StonecuttingTransaction.close((StonecutterMenu)(Object)this);}
}
