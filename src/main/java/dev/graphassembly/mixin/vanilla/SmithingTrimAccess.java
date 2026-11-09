package dev.graphassembly.mixin.vanilla;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native material hints only; dynamic assembly still uses the original recipe. */
@Mixin(SmithingTrimRecipe.class)
public interface SmithingTrimAccess {
    @Accessor("template") Ingredient graphAssembly$template();
    @Accessor("addition") Ingredient graphAssembly$addition();
}
