package dev.graphassembly.core;

import com.google.gson.JsonObject;

public record WeightedOutput(String item, int count, double weight, JsonObject components, double chance) implements OutputNode {
    public WeightedOutput(String item, int count, double weight, JsonObject components) { this(item, count, weight, components, 1); }
    public WeightedOutput { components = components.deepCopy(); }
    @Override public JsonObject components() { return components.deepCopy(); }
}
