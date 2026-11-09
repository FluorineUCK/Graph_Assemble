package dev.graphassembly.mixin.vanilla;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only native fields: SmithingRecipe inherits an empty getIngredients default. */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformAccess {
    @Accessor("template") Ingredient graphAssembly$template();
    @Accessor("addition") Ingredient graphAssembly$addition();
}
