package dev.graphassembly.compat.jei;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.neoforge.NeoForgeTypes;
import java.util.List;

/** Native processing ingredients used for JEI lookup, independently of the rendered graph. */
public final class GraphRecipeIngredients {
    private GraphRecipeIngredients() {}

    public static void addProcessing(IRecipeLayoutBuilder builder, ProcessingRecipe<?, ?> recipe) {
        for (int i = 1; i < recipe.getIngredients().size(); i++)
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addIngredients(recipe.getIngredients().get(i));
        for (var fluid : recipe.getFluidIngredients())
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addIngredients(NeoForgeTypes.FLUID_STACK, List.of(fluid.getFluids()));
        recipe.getRollableResults().stream().skip(1)
            .forEach(out -> builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(out.getStack()));
    }
}
