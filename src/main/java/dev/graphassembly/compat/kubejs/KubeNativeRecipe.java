package dev.graphassembly.compat.kubejs;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.KubeRecipeEventOps;

/** Takes a standalone JSON snapshot before handing ownership to KubeJS's nested-recipe lifecycle. */
final class KubeNativeRecipe {
    private static final java.lang.reflect.Field ACTIONS = actionsField();
    private KubeNativeRecipe() {}

    private static java.lang.reflect.Field actionsField() {
        try {
            var field = KubeRecipe.class.getDeclaredField("recipeIngredientActions");
            return field.trySetAccessible() ? field : null;
        } catch (ReflectiveOperationException | SecurityException unavailable) { return null; }
    }

    private static void rejectActions(KubeRecipe recipe) {
        // 379 exposes no getter and catches action-codec failures inside serializeChanges.
        // Read only this optional API boundary before serialization; never infer no actions
        // from an absent JSON field after upstream swallowed an encoding failure.
        if (ACTIONS == null) throw new IllegalArgumentException("Unsupported KubeJS API: cannot inspect recipeIngredientActions");
        try {
            Object value = ACTIONS.get(recipe);
            if (value != null && (!(value instanceof java.util.List<?> actions) || !actions.isEmpty()))
                throw new IllegalArgumentException("Graph nodes do not support kubejs:ingredient_actions");
        } catch (IllegalAccessException unavailable) {
            throw new IllegalArgumentException("Unsupported KubeJS API: cannot inspect recipeIngredientActions", unavailable);
        }
        if (recipe.modifyResult != null && !recipe.modifyResult.isEmpty())
            throw new IllegalArgumentException("Graph nodes do not support kubejs:modify_result");
    }

    static JsonObject snapshot(KubeRecipe recipe) {
        if (recipe.removed || recipe.creationError)
            throw new IllegalArgumentException("Cannot embed a removed or invalid KubeJS recipe");
        if (recipe.type == null || !recipe.type.event.addedRecipes.contains(recipe))
            throw new IllegalArgumentException("Only recipes created in this recipe event can be embedded; use recipe_id for existing recipes");
        rejectActions(recipe);
        // Unlike the upstream nested codec, serializeChanges includes ingredient actions and
        // getSerializationTypeFunction selects vanilla crafting when no KJS behavior is needed.
        recipe.serializeChanges();
        recipe.serialize();
        var json = recipe.json.deepCopy();
        json.addProperty("type", recipe.getSerializationTypeFunction().schemaType.serializerType);
        json.remove(KubeRecipe.CHANGED_MARKER);
        validate(json);
        simpleCrafting(recipe, json);
        return json;
    }

    private static void simpleCrafting(KubeRecipe recipe, JsonObject json) {
        var event = recipe.type.event;
        // Some upstream schema versions instantiate plain KubeRecipe even for the shaped
        // factory. Only lower the ordinary subset; never erase KJS crafting behavior.
        var vanilla = recipe.type == event.shaped ? event.vanillaShaped
            : recipe.type == event.shapeless ? event.vanillaShapeless : null;
        if (vanilla == null || vanilla == recipe.type) return;
        var keys = java.util.Set.of("type", "group", "category", "show_notification", "result", "pattern", "key", "ingredients");
        for (String key : json.keySet()) if (!keys.contains(key))
            throw new IllegalArgumentException("Graph nodes cannot lower custom crafting field " + key + " to vanilla crafting");
        json.addProperty("type", vanilla.schemaType.serializerType);
    }

    static void consume(KubeRecipe recipe) { KubeRecipeEventOps.MARK_SYNTHETIC.apply(recipe); }

    static void validate(JsonObject json) {
        for (String key : java.util.List.of("kubejs:ingredient_actions", "kubejs:modify_result", "kubejs:mirror", "kubejs:shrink", "neoforge:conditions"))
            if (json.has(key)) throw new IllegalArgumentException("Graph nodes do not support " + key + "; its behavior cannot be discarded");
        if (!json.has("type") || !json.get("type").isJsonPrimitive())
            throw new IllegalArgumentException("Native node recipe needs a serializer type");
        if (json.has("recipe_id")) throw new IllegalArgumentException("Use node() for recipe_id references, not nativeNode()");
    }
}
