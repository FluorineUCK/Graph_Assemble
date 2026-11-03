package dev.graphassembly.mixin.create;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import dev.graphassembly.integration.GraphRuntime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FillingBySpout.class)
public abstract class SpoutMixin {
    @Inject(method = "canItemBeFilled", at = @At("HEAD"), cancellable = true)
    private static void graphAssembly$canFill(Level level, ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.FILLING.getType(), FillingRecipe.class, r -> true, true);
        if (lookup.handled()) cir.setReturnValue(!lookup.recipes().isEmpty());
    }
    @Inject(method = "getRequiredAmountForItem", at = @At("HEAD"), cancellable = true)
    private static void graphAssembly$amount(Level level, ItemStack input, FluidStack available, CallbackInfoReturnable<Integer> cir) {
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
            r -> r.value().getRequiredFluid().ingredient().test(available));
        if (lookup.handled()) cir.setReturnValue(lookup.first().map(r -> r.value().getRequiredFluid().amount()).orElse(-1));
    }
    @Inject(method = "fillItem", at = @At("HEAD"), cancellable = true)
    private static void graphAssembly$fill(Level level, int amount, ItemStack input, FluidStack available, CallbackInfoReturnable<ItemStack> cir) {
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
            r -> r.value().getRequiredFluid().test(available));
        if (!lookup.handled()) return;
        var recipe = lookup.first();
        if (recipe.isEmpty() || recipe.get().value().getRequiredFluid().amount() != amount) { cir.setReturnValue(ItemStack.EMPTY); return; }
        var results = recipe.get().value().rollResults(level.random);
        input.shrink(1); available.shrink(amount);
        cir.setReturnValue(results.isEmpty() ? ItemStack.EMPTY : results.getFirst());
    }
}
