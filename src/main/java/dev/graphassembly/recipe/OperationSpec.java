package dev.graphassembly.recipe;

import com.google.gson.JsonObject;
import dev.graphassembly.integration.vanilla.campfire.CampfireDefinition;
import dev.graphassembly.integration.vanilla.campfire.CampfireKind;
import java.util.Objects;

/** Recipe-layer operation: device configuration is present exactly for campfire recipes. */
public record OperationSpec(JsonObject recipe, RecipeMode recipeMode, CampfireKind campfire) {
    public OperationSpec {
        recipe = recipe.deepCopy();
        Objects.requireNonNull(recipeMode);
        if (CampfireDefinition.isCampfire(recipe) != (campfire != null))
            throw new IllegalArgumentException("campfire configuration must be present exactly for campfire recipes");
    }

    @Override public JsonObject recipe() { return recipe.deepCopy(); }

    public boolean completeRecipe() { return recipeMode == RecipeMode.NATIVE || recipe.has("recipe_id"); }

    public boolean isCampfire() { return CampfireDefinition.isCampfire(recipe); }
}
