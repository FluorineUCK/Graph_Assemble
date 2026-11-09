package dev.graphassembly.compat.kubejs;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.util.KubeResourceLocation;
import dev.latvian.mods.rhino.Context;
import net.minecraft.resources.ResourceLocation;

final class KubeRecipeRegistration {
    private KubeRecipeRegistration() {}
    static KubeRecipe register(Context cx, RecipesKubeEvent event, String id, JsonObject json) {
        if (id == null || id.isBlank() || !id.contains(":"))
            throw new IllegalArgumentException("Recipe id must be a namespaced resource location");
        var location = ResourceLocation.parse(id);
        if (event == null) throw new IllegalArgumentException("A ServerEvents.recipes event is required");
        return event.custom(cx, json).id(new KubeResourceLocation(location));
    }
}
