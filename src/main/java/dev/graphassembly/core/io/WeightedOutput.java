package dev.graphassembly.core.io;

import com.google.gson.JsonObject;

public record WeightedOutput(String item, int count, double weight, JsonObject components, double chance) implements OutputNode {
    public WeightedOutput { components = components.deepCopy(); }
    @Override public JsonObject components() { return components.deepCopy(); }
}
