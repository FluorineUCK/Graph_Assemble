package dev.graphassembly.mixin.create;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.graphassembly.integration.BasinGraphRuntime;
import dev.graphassembly.integration.GraphOperation;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BasinRecipe.class)
public abstract class BasinRecipeMixin {
    @Inject(method="match",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$match(BasinBlockEntity basin,Recipe<?> recipe,CallbackInfoReturnable<Boolean> cir) {
        var operation=GraphOperation.get(recipe);
        if(operation!=null && recipe instanceof BasinRecipe nativeRecipe) cir.setReturnValue(BasinGraphRuntime.match(basin,nativeRecipe,operation));
    }
    @Inject(method="apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$apply(BasinBlockEntity basin,Recipe<?> recipe,boolean simulate,CallbackInfoReturnable<Boolean> cir) {
        var operation=GraphOperation.get(recipe);
        if(operation!=null && recipe instanceof BasinRecipe nativeRecipe) cir.setReturnValue(BasinGraphRuntime.apply(basin,nativeRecipe,operation,simulate));
    }
    @Redirect(method="apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/item/crafting/Ingredient;test(Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean graphAssembly$ordinaryInput(Ingredient ingredient,ItemStack stack) {
        return !dev.graphassembly.integration.sequence.NativeSequences.tracked(stack) && ingredient.test(stack);
    }
}
