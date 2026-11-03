package dev.graphassembly.mixin.create;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import dev.graphassembly.integration.GraphRuntime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(MechanicalPressBlockEntity.class)
public abstract class PressMixin {
    @Inject(method = "getRecipe", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$recipe(ItemStack input, CallbackInfoReturnable<Optional<RecipeHolder<PressingRecipe>>> cir) {
        var level = ((MechanicalPressBlockEntity)(Object)this).getLevel();
        if (level == null) return;
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class, r -> true);
        if (lookup.handled()) cir.setReturnValue(lookup.first());
    }
}
