package dev.graphassembly.core.definition;

import com.google.gson.JsonElement;
public record GraphDefaults(JsonElement ingredient, String transitionalItem) {
    public GraphDefaults { ingredient = ingredient == null ? null : ingredient.deepCopy(); }
    @Override public JsonElement ingredient() { return ingredient == null ? null : ingredient.deepCopy(); }
}
