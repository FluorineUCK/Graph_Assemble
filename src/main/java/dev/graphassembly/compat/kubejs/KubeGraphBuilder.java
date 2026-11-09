package dev.graphassembly.compat.kubejs;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.rhino.Context;

/** KubeJS-only facade; the shared writer and the runtime never load KubeJS types. */
public final class KubeGraphBuilder {
    private final GraphBuilder writer;
    public KubeGraphBuilder(String transitionalItem) { writer = new GraphBuilder(transitionalItem); }

    public KubeGraphBuilder ingredient(Context cx, Object value) {
        writer.ingredient(KubeGraphValues.ingredient(cx, value));
        return this;
    }
    public KubeGraphBuilder node(Context cx, String id, Object value) { return node(cx, id, value, new JsonObject()); }
    public KubeGraphBuilder node(Context cx, String id, Object value, Object options) {
        var settings = KubeGraphValues.options(cx, options);
        Object unwrapped = KubeGraphValues.unwrap(value);
        if (unwrapped instanceof KubeRecipe recipe) {
            var snapshot = KubeNativeRecipe.snapshot(recipe);
            nativeSettings(settings);
            writer.node(id, snapshot, settings);
            KubeNativeRecipe.consume(recipe);
        } else {
            var json = KubeGraphValues.json(cx, unwrapped).getAsJsonObject();
            if (settings.has("recipe_mode") && settings.get("recipe_mode").getAsString().equals("native") && !json.has("recipe_id"))
                KubeNativeRecipe.validate(json);
            writer.node(id, json, settings);
        }
        return this;
    }
    public KubeGraphBuilder nativeNode(Context cx, String id, Object value) { return nativeNode(cx, id, value, new JsonObject()); }
    public KubeGraphBuilder nativeNode(Context cx, String id, Object value, Object options) {
        if (KubeGraphValues.unwrap(value) instanceof KubeRecipe) return node(cx, id, value, options);
        var recipe = KubeGraphValues.json(cx, value).getAsJsonObject();
        KubeNativeRecipe.validate(recipe);
        var settings = KubeGraphValues.options(cx, options);
        nativeSettings(settings);
        writer.node(id, recipe, settings);
        return this;
    }
    private static void nativeSettings(JsonObject options) {
        if (options.has("recipe_mode") && !options.get("recipe_mode").getAsString().equals("native"))
            throw new IllegalArgumentException("An upstream recipe object is complete: recipe_mode must be native");
        options.addProperty("recipe_mode", "native");
    }
    public KubeGraphBuilder edge(String from, String to) { writer.edge(from, to); return this; }
    public KubeGraphBuilder edge(String from, String to, JsonObject options) { writer.edge(from, to, options); return this; }
    public KubeGraphBuilder weightedEdge(String from, String to, double weight) { writer.weightedEdge(from, to, weight); return this; }
    public KubeGraphBuilder bidirectionalEdge(String from, String to) { writer.bidirectionalEdge(from, to); return this; }
    public KubeGraphBuilder output(Context cx, Object value) { writer.output(KubeGraphValues.rule(cx, value)); return this; }
    public KubeGraphBuilder finishOutput(Context cx, String nodeId, Object results) {
        return finishOutput(cx, nodeId, results, new JsonObject());
    }
    public KubeGraphBuilder finishOutput(Context cx, String nodeId, Object results, Object requirements) {
        writer.finishOutput(nodeId, KubeGraphValues.entries(cx, KubeGraphValues.json(cx, results)),
            KubeGraphValues.json(cx, requirements).getAsJsonObject()); return this;
    }
    public KubeGraphBuilder scrap(Context cx, String nodeId, Object results) {
        writer.scrap(nodeId, KubeGraphValues.entries(cx, KubeGraphValues.json(cx, results))); return this;
    }
    public KubeGraphBuilder requirements(Context cx, String nodeId, Object requirements) {
        writer.requirements(nodeId, KubeGraphValues.json(cx, requirements).getAsJsonObject()); return this;
    }
    public KubeGraphBuilder conditionalEdge(Context cx, String from, String to, Object requirements) {
        return conditionalEdge(cx, from, to, requirements, 1);
    }
    public KubeGraphBuilder conditionalEdge(Context cx, String from, String to, Object requirements, double weight) {
        writer.conditionalEdge(from, to, KubeGraphValues.json(cx, requirements).getAsJsonObject(), weight); return this;
    }
    public KubeGraphBuilder branchMode(String nodeId, String mode) { writer.branchMode(nodeId, mode); return this; }
    public KubeGraphBuilder progressAction(Context cx, String nodeId, Object action) {
        writer.progressAction(nodeId, KubeGraphValues.json(cx, action).getAsJsonObject()); return this;
    }
    public KubeRecipe register(Context cx, dev.latvian.mods.kubejs.recipe.RecipesKubeEvent event, String id) {
        return KubeRecipeRegistration.register(cx, event, id, build());
    }
    public JsonObject build() { return writer.build(); }
}
