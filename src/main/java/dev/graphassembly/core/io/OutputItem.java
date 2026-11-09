package dev.graphassembly.core.io;

import com.google.gson.JsonObject;

/** A determined output quantity, with no remaining selection weights or chance gates. */
public record OutputItem(String item, int count, JsonObject components) {
    public OutputItem { components = components.deepCopy(); }
    @Override public JsonObject components() { return components.deepCopy(); }
}
