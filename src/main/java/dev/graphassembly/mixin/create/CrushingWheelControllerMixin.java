package dev.graphassembly.mixin.create;

import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Optional;

@Mixin(CrushingWheelControllerBlockEntity.class)
public abstract class CrushingWheelControllerMixin {
    @Unique private boolean graphAssembly$blocked;
    @Unique private GraphIndex graphAssembly$index;
    @Unique private ItemStack graphAssembly$input=ItemStack.EMPTY;
    @Unique private boolean graphAssembly$inFlight;
    @Inject(method="findRecipe",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$find(CallbackInfoReturnable<Optional<RecipeHolder<StandardProcessingRecipe<RecipeWrapper>>>> cir) {
        var self=(CrushingWheelControllerBlockEntity)(Object)this;
        if(self.getLevel()==null)return;
        var lookup=CrusherOperations.lookup(self.getLevel(),self.inventory.getStackInSlot(0));
        if(lookup.handled()) {
            // itemInserted calls findRecipe before assigning the native duration. Snapshot
            // admission now so removal before the first processing tick cannot reopen destruction.
            if(lookup.first().isPresent()) {
                graphAssembly$input=self.inventory.getStackInSlot(0).copy();
                graphAssembly$index=GraphIndex.get(self.getLevel());graphAssembly$inFlight=true;
            }
            cir.setReturnValue(lookup.first());
        }
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$timer(CallbackInfo ci) {
        var self=(CrushingWheelControllerBlockEntity)(Object)this;
        var level=self.getLevel();
        if(level==null||level.isClientSide||self.hasEntity())return;
        if(self.inventory.appliedRecipe||self.inventory.isEmpty()) {
            graphAssembly$inFlight=false;graphAssembly$input=ItemStack.EMPTY;return;
        }
        var input=self.inventory.getStackInSlot(0);
        if(graphAssembly$inFlight&&!ItemStack.isSameItemSameComponents(input,graphAssembly$input))graphAssembly$inFlight=false;
        var lookup=CrusherOperations.lookup(level,input);
        if(!lookup.handled()&&!graphAssembly$inFlight)return;
        if(lookup.first().isEmpty()){ci.cancel();return;}
        var index=GraphIndex.get(level);
        if(!graphAssembly$inFlight||graphAssembly$index!=index||!ItemStack.matches(input,graphAssembly$input)) {
            self.inventory.remainingTime=lookup.first().orElseThrow().value().getProcessingDuration();
            graphAssembly$index=index;graphAssembly$input=input.copy();graphAssembly$inFlight=true;
        }
    }
    @Inject(method="applyRecipe",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$apply(CallbackInfo ci) {
        graphAssembly$blocked=false;
        var self=(CrushingWheelControllerBlockEntity)(Object)this;
        if(self.getLevel()==null)return;
        var input=self.inventory.getStackInSlot(0);
        if(graphAssembly$inFlight&&!ItemStack.isSameItemSameComponents(input,graphAssembly$input))graphAssembly$inFlight=false;
        var lookup=CrusherOperations.lookup(self.getLevel(),input);
        if(!lookup.handled()&&!graphAssembly$inFlight)return;
        ci.cancel();
        graphAssembly$blocked=lookup.first().isEmpty()||!CrusherOperations.apply(self.getLevel(),self.inventory,lookup.first().orElseThrow().value());
        if(graphAssembly$blocked)self.inventory.remainingTime=20;
        else {self.setChanged();self.sendData();}
    }
    // The host writes true unconditionally after applyRecipe; preserve uncommitted inputs on retries.
    @Redirect(method="tick",at=@At(value="FIELD",target="Lcom/simibubi/create/content/processing/recipe/ProcessingInventory;appliedRecipe:Z",opcode=181))
    private void graphAssembly$commitFlag(ProcessingInventory inventory,boolean applied) {
        inventory.appliedRecipe=applied&&!graphAssembly$blocked;
    }
}
