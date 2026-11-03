package dev.graphassembly.mixin.create;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.*;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeployerBlockEntity.class)
public abstract class DeployerMixin {
    @Inject(method = "getRecipe", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$recipe(ItemStack input, CallbackInfoReturnable<RecipeHolder<? extends Recipe<? extends RecipeInput>>> cir) {
        var self = (DeployerBlockEntity)(Object)this;
        var level = self.getLevel();
        if (level == null || self.getPlayer() == null) return;
        var held = self.getPlayer().getMainHandItem();
        var inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(0, input); inventory.setStackInSlot(1, held);
        var wrapper = new RecipeWrapper(inventory);
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class,
            r -> r.value().matches(wrapper, level), false, java.util.List.of(held));
        if (lookup.handled() || Workpiece.has(held)) cir.setReturnValue(lookup.first().orElse(null));
    }
}
