package dev.graphassembly.mixin.create;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import dev.graphassembly.integration.DrainOperations;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GenericItemEmptying.class)
public abstract class GenericItemEmptyingMixin {
    @Inject(method="canItemBeEmptied",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$can(Level level,ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        var lookup=DrainOperations.lookup(level,stack);
        if(lookup.handled())cir.setReturnValue(lookup.first().filter(r->DrainOperations.completion(level,r.value())!=null).isPresent());
    }
    @Inject(method="emptyItem",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$protect(Level level,ItemStack stack,boolean simulate,CallbackInfoReturnable<Pair<FluidStack,ItemStack>> cir) {
        var lookup=DrainOperations.lookup(level,stack);
        if(!lookup.handled())return;
        // Graph execution requires the drain's atomic tank/payment boundary; generic capability
        // or potion fallbacks must never consume a tracked workpiece or strip its progress.
        if(simulate&&lookup.first().isPresent()) {
            var recipe=lookup.first().orElseThrow().value();
            var completion=DrainOperations.completion(level,recipe);
            if(completion!=null){cir.setReturnValue(Pair.of(recipe.getResultingFluid().copy(),completion.preview()));return;}
        }
        cir.setReturnValue(Pair.of(FluidStack.EMPTY,ItemStack.EMPTY));
    }
}
