package dev.graphassembly.mixin.create;

import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ItemDrainBlockEntity.class)
public abstract class ItemDrainMixin {
    @Shadow SmartFluidTankBehaviour internalTank;
    @Shadow TransportedItemStack heldItem;
    @Shadow protected int processingTicks;
    @Unique private ItemStack graphAssembly$snapshot=ItemStack.EMPTY;
    @Unique private GraphIndex graphAssembly$index;
    @Unique private GraphOperation graphAssembly$operation;
    @Inject(method="setHeldItem",at=@At("HEAD"))
    private void graphAssembly$newHeld(TransportedItemStack item,net.minecraft.core.Direction side,CallbackInfo ci) {
        graphAssembly$snapshot=ItemStack.EMPTY;graphAssembly$operation=null;graphAssembly$index=null;processingTicks=0;
    }
    @Inject(method="tick",at=@At("RETURN"))
    private void graphAssembly$admitted(CallbackInfo ci) {
        var self=(ItemDrainBlockEntity)(Object)this;
        var level=self.getLevel();
        if(level==null||level.isClientSide||heldItem==null||processingTicks!=ItemDrainBlockEntity.FILLING_TIME||graphAssembly$operation!=null)return;
        // Native tick has just admitted the item at its midpoint. Keep this definition/input
        // before the first continueProcessing call; a reload must not reopen capability fallback.
        var lookup=DrainOperations.lookup(level,heldItem.stack);
        if(lookup.first().isPresent()) {
            graphAssembly$operation=GraphOperation.get(lookup.first().orElseThrow().value());
            graphAssembly$snapshot=heldItem.stack.copy();graphAssembly$index=GraphIndex.get(level);
        }
    }
    @Inject(method="continueProcessing",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$process(CallbackInfoReturnable<Boolean> cir) {
        var self=(ItemDrainBlockEntity)(Object)this;
        var level=self.getLevel();
        if(level==null||level.isClientSide||heldItem==null)return;
        var input=heldItem.stack;
        var lookup=DrainOperations.lookup(level,input);
        if(!lookup.handled()&&graphAssembly$operation==null)return;
        cir.setReturnValue(true);
        if(processingTicks<5)return;
        var selected=lookup.first();
        if(selected.isEmpty()||input.getCount()!=1){processingTicks=ItemDrainBlockEntity.FILLING_TIME;return;}
        var recipe=selected.orElseThrow().value();
        var operation=GraphOperation.get(recipe);
        var completion=DrainOperations.completion(level,recipe);
        if(completion==null||!operation.matchesPrimary(level,input)){processingTicks=ItemDrainBlockEntity.FILLING_TIME;return;}
        var index=GraphIndex.get(level);
        boolean changed=graphAssembly$operation==null&&processingTicks<=5||graphAssembly$operation!=null&&(graphAssembly$index!=index||!ItemStack.matches(graphAssembly$snapshot,input)
            ||!graphAssembly$operation.current(level));
        graphAssembly$snapshot=input.copy();graphAssembly$index=index;graphAssembly$operation=operation;
        if(changed){processingTicks=ItemDrainBlockEntity.FILLING_TIME;return;}
        var fluid=recipe.getResultingFluid().copy();
        internalTank.allowInsertion();
        try {
            // In particular run this at tick 5: the native host only rechecks at ticks > 5.
            if(internalTank.getPrimaryHandler().fill(fluid,FluidAction.SIMULATE)!=fluid.getAmount()) {
                processingTicks=ItemDrainBlockEntity.FILLING_TIME;return;
            }
            if(processingTicks!=5)return;
            if(!operation.matchesPrimary(level,heldItem.stack)||!ItemStack.matches(graphAssembly$snapshot,heldItem.stack)) {
                processingTicks=ItemDrainBlockEntity.FILLING_TIME;return;
            }
            var output=dev.graphassembly.integration.vanilla.furnace.CookingLookup.merge(ItemStack.EMPTY,completion.roll(level.random),Integer.MAX_VALUE);
            internalTank.getPrimaryHandler().fill(fluid,FluidAction.EXECUTE);
            if(output.isEmpty())heldItem=null;
            else heldItem.stack=output;
            graphAssembly$operation=null;graphAssembly$snapshot=ItemStack.EMPTY;
            self.notifyUpdate();
        } finally {internalTank.forbidInsertion();}
    }
}
