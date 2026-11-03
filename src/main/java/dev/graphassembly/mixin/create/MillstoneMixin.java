package dev.graphassembly.mixin.create;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.millstone.*;
import dev.graphassembly.integration.GraphRuntime;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Optional;

@Mixin(MillstoneBlockEntity.class)
public abstract class MillstoneMixin {
    @Shadow private MillingRecipe lastRecipe;
    @Unique private boolean graphAssembly$lastWasGraph;
    @Unique private GraphIndex graphAssembly$recipeIndex;

    @Inject(method = "tick", at = @At("HEAD"))
    private void graphAssembly$invalidateCached(CallbackInfo ci) {
        var self = (MillstoneBlockEntity)(Object)this;
        var level = self.getLevel();
        if (level == null) return;
        var index = GraphIndex.get(level);
        if (graphAssembly$recipeIndex != index) {
            // A newly matching raw Start has no progress component. Invalidate the native
            // ingredient-only cache and its in-flight duration when graph recipes reload.
            if (graphAssembly$recipeIndex != null) self.timer = 0;
            graphAssembly$recipeIndex = index;
            lastRecipe = null;
        }
        if (graphAssembly$lastWasGraph || Workpiece.has(self.inputInv.getStackInSlot(0))) lastRecipe = null;
    }
    @Redirect(method = {"tick", "process", "canProcess"}, at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/AllRecipeTypes;find(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<MillingRecipe>> graphAssembly$find(AllRecipeTypes type, RecipeInput inventory, Level level) {
        var lookup = GraphRuntime.lookup(level, inventory.getItem(0), AllRecipeTypes.MILLING.getType(), MillingRecipe.class, r -> true);
        graphAssembly$lastWasGraph = lookup.handled();
        return lookup.handled() ? lookup.first() : type.find(inventory, level);
    }
    @Inject(method = "canProcess", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$canProcess(ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        var level = ((MillstoneBlockEntity)(Object)this).getLevel();
        if (level == null) return;
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.MILLING.getType(), MillingRecipe.class, r -> true);
        if (lookup.handled()) cir.setReturnValue(!lookup.recipes().isEmpty());
    }
    @Inject(method = "process", at = @At("HEAD"), cancellable = true)
    private void graphAssembly$process(CallbackInfo ci) {
        var self = (MillstoneBlockEntity)(Object)this;
        var level = self.getLevel();
        if (level == null) return;
        var input = self.inputInv.getStackInSlot(0);
        var lookup = GraphRuntime.lookup(level, input, AllRecipeTypes.MILLING.getType(), MillingRecipe.class, r -> true);
        if (!lookup.handled()) return;
        ci.cancel();
        lastRecipe = null;
        var selected = lookup.first();
        if (selected.isEmpty()) return;
        // Reserve one empty slot for each possible output and remainder BEFORE any random draw.
        // This prevents native insertion from discarding an unstackable workpiece when storage is full.
        var recipe = selected.get().value();
        int needed = dev.graphassembly.integration.GraphOutputs.maxStacks(level,
            dev.graphassembly.integration.GraphOperation.get(recipe), recipe) + (input.hasCraftingRemainingItem() ? 1 : 0);
        int free = 0;
        for (int i = 0; i < self.outputInv.getSlots(); i++) if (self.outputInv.getStackInSlot(i).isEmpty()) free++;
        if (free < needed) return;
        var remainder = input.getCraftingRemainingItem();
        var outputs = selected.get().value().rollResults(level.random);
        input.shrink(1);
        for (var output : outputs) ItemHandlerHelper.insertItemStacked(self.outputInv, output, false);
        if (!remainder.isEmpty()) ItemHandlerHelper.insertItemStacked(self.outputInv, remainder, false);
        self.setChanged(); self.sendData();
    }
}
