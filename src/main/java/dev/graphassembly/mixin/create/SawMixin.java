package dev.graphassembly.mixin.create;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.saw.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.graphassembly.integration.GraphRuntime;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(SawBlockEntity.class)
public abstract class SawMixin {
    @Shadow private FilteringBehaviour filtering;

    @Inject(method = "applyRecipe", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$reserveBulkOutputs(CallbackInfo ci) {
        var self = (SawBlockEntity)(Object)this;
        var level = self.getLevel();
        if (level == null) return;
        var input = self.inventory.getStackInSlot(0);
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class, r -> true);
        if (!lookup.handled()) return;
        var selected = lookup.recipes().stream()
            .filter(r -> filtering.test(r.value().getResultItem(level.registryAccess())))
            .findFirst();
        if (selected.isEmpty()) {
            ci.cancel();
            return;
        }
        // Native applyRecipe clears the input and silently truncates results to output slots.
        // Reserve for every possible stack before either consumption or a random draw. Graph
        // workpieces cannot stack; even byproducts may carry distinct components on each roll.
        var recipe = selected.get().value();
        int stacksPerInput = dev.graphassembly.integration.GraphOutputs.maxStacks(level,
            dev.graphassembly.integration.GraphOperation.get(recipe), recipe)
            + (input.hasCraftingRemainingItem() ? 1 : 0);
        long requiredSlots = (long) input.getCount() * stacksPerInput;
        if (requiredSlots > self.inventory.getSlots() - 1) {
            ci.cancel();
            return;
        }
        // Do not let native inventory.clear erase outputs still awaiting export.
        for (int slot = 1; slot < self.inventory.getSlots(); slot++) {
            if (!self.inventory.getStackInSlot(slot).isEmpty()) {
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "getRecipes", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$recipes(CallbackInfoReturnable<List<RecipeHolder<? extends Recipe<?>>>> cir) {
        var self = (SawBlockEntity)(Object)this;
        var level = self.getLevel();
        if (level == null) return;
        var lookup = GraphRuntime.lookup(level, self.inventory.getStackInSlot(0), AllRecipeTypes.CUTTING.getType(), CuttingRecipe.class, r -> true);
        if (lookup.handled()) cir.setReturnValue(lookup.recipes().stream()
            .filter(r -> filtering.test(r.value().getResultItem(level.registryAccess())))
            .<RecipeHolder<? extends Recipe<?>>>map(r -> r).toList());
    }
}
