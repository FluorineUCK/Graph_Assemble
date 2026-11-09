package dev.graphassembly.compat.kubejs;

import com.google.gson.*;

/** Authoring-only writer: the native outer Create recipe owns loop and result selection. */
public final class SequenceBuilder {
    private final JsonObject json = new JsonObject();
    private final JsonArray sequence = new JsonArray(), results = new JsonArray();
    public SequenceBuilder(JsonElement transitionalItem) {
        json.addProperty("type", "create:sequenced_assembly");
        json.add("transitional_item", transitionalItem.deepCopy());
        json.addProperty("loops", 1); json.add("sequence", sequence); json.add("results", results);
    }
    public SequenceBuilder ingredient(JsonElement ingredient) { json.add("ingredient", ingredient.deepCopy()); return this; }
    public SequenceBuilder step(JsonObject recipe, JsonObject options) {
        for (String key : options.keySet()) if (!java.util.Set.of("recipe_mode", "campfire").contains(key))
            throw new IllegalArgumentException("Sequence step options do not support " + key);
        if (!recipe.has("type")) throw new IllegalArgumentException("Sequence step recipe needs a serializer type");
        String type = recipe.get("type").getAsString();
        if (type.equals("create:sequenced_assembly") || type.equals("graph_assembly:sequence_step"))
            throw new IllegalArgumentException("Recursive sequence wrappers are unsupported");
        var step = options.deepCopy(); step.add("recipe", recipe.deepCopy());
        var wrapper = new JsonObject(); wrapper.addProperty("type", "graph_assembly:sequence_step"); wrapper.add("step", step);
        sequence.add(wrapper); return this;
    }
    public SequenceBuilder loops(int loops) {
        if (loops < 1) throw new IllegalArgumentException("Sequence loops must be positive");
        json.addProperty("loops", loops); return this;
    }
    public SequenceBuilder output(JsonElement result) {
        if (result.isJsonArray()) for (var entry : result.getAsJsonArray()) results.add(entry.deepCopy());
        else results.add(result.deepCopy());
        return this;
    }
    public JsonObject build() {
        if (!json.has("ingredient")) throw new IllegalArgumentException("Sequence ingredient is required");
        if (sequence.isEmpty()) throw new IllegalArgumentException("Sequence needs at least one step");
        if (results.isEmpty()) throw new IllegalArgumentException("Sequence results must not be empty");
        return json.deepCopy();
    }
}
