package dev.graphassembly.mixin.jei;

import dev.graphassembly.compat.jei.GraphCategory;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** JEI 19 caches immutable layout rectangles across Screen.init/resize. Only invalidate our category. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic", remap = false)
abstract class JeiRecipeLayoutResizeMixin {
    @Shadow private IRecipeCategory<?> cachedRecipeCategory;
    @Unique private int graphAssembly$width = -1;
    @Unique private int graphAssembly$height = -1;

    @Inject(method = "getVisibleRecipeLayoutsWithButtons", at = @At("HEAD"))
    private void graphAssembly$resize(CallbackInfoReturnable<?> callback) {
        if (!(cachedRecipeCategory instanceof GraphCategory category)) return;
        int width = category.getWidth(), height = category.getHeight();
        if (graphAssembly$width != width || graphAssembly$height != height) {
            // Use JEI's normal invalidation branch: rebuild slots, widgets and hit areas together,
            // preserving the lookup/focus/history rather than opening a new recipe query.
            cachedRecipeCategory = null;
            graphAssembly$width = width;
            graphAssembly$height = height;
        }
    }
}
