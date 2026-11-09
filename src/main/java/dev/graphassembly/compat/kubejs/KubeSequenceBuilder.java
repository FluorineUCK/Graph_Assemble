package dev.graphassembly.compat.kubejs;

import com.google.gson.*;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.rhino.Context;

public final class KubeSequenceBuilder {
    private final SequenceBuilder writer;
    public KubeSequenceBuilder(Context cx, Object transitionalItem) {
        writer = new SequenceBuilder(KubeGraphValues.nativeOutput(cx, transitionalItem));
    }
    public KubeSequenceBuilder ingredient(Context cx, Object ingredient) {
        writer.ingredient(KubeGraphValues.ingredient(cx, ingredient)); return this;
    }
    public KubeSequenceBuilder step(Context cx, Object value) { return step(cx, value, new JsonObject()); }
    public KubeSequenceBuilder step(Context cx, Object value, Object options) {
        var settings = KubeGraphValues.json(cx, options).getAsJsonObject();
        Object unwrapped = KubeGraphValues.unwrap(value);
        if (unwrapped instanceof KubeRecipe recipe) {
            var snapshot = KubeNativeRecipe.snapshot(recipe);
            if (settings.has("recipe_mode") && !settings.get("recipe_mode").getAsString().equals("native"))
                throw new IllegalArgumentException("An upstream recipe object is complete: recipe_mode must be native");
            settings.addProperty("recipe_mode", "native");
            writer.step(snapshot, settings);
            KubeNativeRecipe.consume(recipe);
        } else {
            var recipe = KubeGraphValues.json(cx, unwrapped).getAsJsonObject();
            if (!recipe.has("recipe_id")) KubeNativeRecipe.validate(recipe);
            writer.step(recipe, settings);
        }
        return this;
    }
    public KubeSequenceBuilder loops(double loops) {
        if (!Double.isFinite(loops) || loops < 1 || loops > Integer.MAX_VALUE || loops != Math.rint(loops))
            throw new IllegalArgumentException("Sequence loops must be a positive integer");
        writer.loops((int) loops); return this;
    }
    public KubeSequenceBuilder output(Context cx, Object results) {
        Object value = KubeGraphValues.unwrap(results);
        if (cx.isListLike(value)) for (var result : cx.optionalListOf(value)) writer.output(KubeGraphValues.nativeOutput(cx, result));
        else if (value instanceof JsonArray array) for (var result : array) writer.output(KubeGraphValues.nativeOutput(cx, result));
        else writer.output(KubeGraphValues.nativeOutput(cx, value));
        return this;
    }
    public JsonObject build() { return writer.build(); }
    public KubeRecipe register(Context cx, RecipesKubeEvent event, String id) {
        return KubeRecipeRegistration.register(cx, event, id, build());
    }
}
