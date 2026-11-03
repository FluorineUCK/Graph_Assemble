package dev.graphassembly.integration;

import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.crafting.RecipeInput;

/** Input boundary for ordinary native queries, independent of recipe implementation and cached predicates. */
public final class OrdinaryRecipeQueryGuard {
    private OrdinaryRecipeQueryGuard() {}

    public static boolean hasProgress(RecipeInput input) {
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (!stack.isEmpty() && Workpiece.has(stack)) return true;
        }
        return false;
    }
}
