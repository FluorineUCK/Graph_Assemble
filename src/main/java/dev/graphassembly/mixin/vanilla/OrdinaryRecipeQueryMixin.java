package dev.graphassembly.mixin.vanilla;

import dev.graphassembly.integration.OrdinaryRecipeQueryGuard;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Optional;

/** Ordinary lookup only: inventory enumeration, synchronization, and direct recipe calls remain native. */
@Mixin(RecipeManager.class)
public abstract class OrdinaryRecipeQueryMixin {
    // Both the three-argument and cached-id overloads delegate here before matching any recipe.
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
        at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void graphAssembly$guardOrdinaryQuery(
            RecipeType<T> type, I input, Level level, RecipeHolder<T> cached,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        if (OrdinaryRecipeQueryGuard.hasProgress(input)) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "getRecipesFor", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void graphAssembly$guardOrdinaryMatches(
            RecipeType<T> type, I input, Level level, CallbackInfoReturnable<List<RecipeHolder<T>>> cir) {
        if (OrdinaryRecipeQueryGuard.hasProgress(input)) cir.setReturnValue(List.of());
    }
}
