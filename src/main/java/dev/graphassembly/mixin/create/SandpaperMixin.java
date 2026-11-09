package dev.graphassembly.mixin.create;

import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import dev.graphassembly.integration.SandpaperOperations;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(SandPaperPolishingRecipe.class)
public abstract class SandpaperMixin {
    @SuppressWarnings({"unchecked","rawtypes"})
    @Inject(method="getMatchingRecipes",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$recipes(Level level,ItemStack input,CallbackInfoReturnable<List<RecipeHolder<Recipe<SingleRecipeInput>>>> cir) {
        var found=SandpaperOperations.lookup(level,input);
        if(found.handled())cir.setReturnValue((List)found.recipes());
    }
    @Inject(method="applyPolish",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$apply(Level level,Vec3 position,ItemStack input,ItemStack paper,CallbackInfoReturnable<ItemStack> cir) {
        var found=SandpaperOperations.lookup(level,input);
        if(found.handled())cir.setReturnValue(found.first().map(holder->SandpaperOperations.complete(level,input,holder)).orElseGet(input::copy));
    }
}
