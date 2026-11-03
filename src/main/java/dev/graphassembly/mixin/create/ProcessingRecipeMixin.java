package dev.graphassembly.mixin.create;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.integration.GraphOperation;
import dev.graphassembly.integration.GraphOutputs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Graph settlement replaces the complete primary result bundle, not just its first ItemStack. */
@Mixin(ProcessingRecipe.class)
public abstract class ProcessingRecipeMixin {
    @Inject(method = "rollResults(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$roll(List<ProcessingOutput> outputs, RandomSource random, CallbackInfoReturnable<List<ItemStack>> cir) {
        var recipe = (ProcessingRecipe<?, ?>)(Object)this;
        var operation = GraphOperation.get(recipe);
        if (operation != null) cir.setReturnValue(GraphOutputs.roll(operation.level(), operation, recipe, random));
    }
}
