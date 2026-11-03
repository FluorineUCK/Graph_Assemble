package dev.graphassembly.compat.kubejs;

import com.google.gson.*;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.IngredientWrapper;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.ItemWrapper;
import dev.latvian.mods.kubejs.util.JsonUtils;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Optional script boundary. Native codecs own item components and ingredient predicates. */
final class KubeGraphValues {
    private KubeGraphValues() {}

    static Object unwrap(Object value) {
        while (value instanceof Wrapper wrapper) value = wrapper.unwrap();
        return value;
    }

    static JsonElement ingredient(Context cx, Object value) {
        var ingredient = IngredientWrapper.wrap(cx, unwrap(value));
        return Ingredient.CODEC.encodeStart(RegistryAccessContainer.of(cx).json(), ingredient).getOrThrow();
    }

    static JsonElement json(Context cx, Object input) {
        Object value = unwrap(input);
        if (value instanceof JsonElement json) return json.deepCopy();
        if (value instanceof ItemStack stack)
            return ItemStack.CODEC.encodeStart(RegistryAccessContainer.of(cx).json(), stack).getOrThrow();
        if (value instanceof Ingredient ingredient)
            return Ingredient.CODEC.encodeStart(RegistryAccessContainer.of(cx).json(), ingredient).getOrThrow();
        if (cx.isMapLike(value)) {
            var result = new JsonObject();
            cx.optionalMapOf(value).forEach((key, entry) -> result.add(key, json(cx, entry)));
            return result;
        }
        if (cx.isListLike(value)) {
            var result = new JsonArray();
            cx.optionalListOf(value).forEach(entry -> result.add(json(cx, entry)));
            return result;
        }
        return JsonUtils.of(cx, value).deepCopy();
    }

    static JsonObject options(Context cx, Object value) {
        var result = json(cx, value).getAsJsonObject();
        if (result.has("ingredient")) result.add("ingredient", ingredient(cx, result.get("ingredient")));
        if (result.has("outputs")) {
            var rules = new JsonArray();
            for (var rule : result.getAsJsonArray("outputs")) rules.add(rule(cx, rule));
            result.add("outputs", rules);
        }
        if (result.has("scrap")) result.add("scrap", entries(cx, result.get("scrap")));
        return result;
    }

    static JsonObject rule(Context cx, Object value) {
        var data = json(cx, value);
        if (data.isJsonObject() && (data.getAsJsonObject().has("results") || data.getAsJsonObject().has("requirements"))) {
            var result = data.getAsJsonObject();
            if (result.has("results")) result.add("results", entries(cx, result.get("results")));
            return result;
        }
        var result = new JsonObject();
        result.add("results", entries(cx, data));
        return result;
    }

    private static JsonArray entries(Context cx, JsonElement value) {
        var result = new JsonArray();
        if (value.isJsonArray()) for (var entry : value.getAsJsonArray()) result.add(entry(cx, entry));
        else result.add(entry(cx, value));
        return result;
    }

    private static JsonObject entry(Context cx, JsonElement value) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject().deepCopy();
            // Graph weight/chance semantics are not the native ProcessingOutput semantics.
            if (object.has("item") || object.has("mode")) {
                if (object.has("mode") && object.has("results")) object.add("results", entries(cx, object.get("results")));
                return object;
            }
            for (String key : object.keySet()) if (!java.util.Set.of("id", "count", "components").contains(key))
                throw new IllegalArgumentException("Graph output: native item shorthand does not accept " + key
                    + "; use an explicit graph {item, weight, chance} entry");
        }
        var stack = ItemWrapper.wrap(cx, value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value);
        var result = ItemStack.CODEC.encodeStart(RegistryAccessContainer.of(cx).json(), stack).getOrThrow().getAsJsonObject();
        result.add("item", result.remove("id"));
        return result;
    }
}
