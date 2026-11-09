package dev.graphassembly.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/** Shared path-aware JSON shape and scalar validation for the concrete codecs. */
public final class JsonValidation {
    private JsonValidation() {}

    public static void fields(JsonObject object, String path, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String name : object.keySet()) if (!names.contains(name)) throw invalid(path + "." + name, "unknown field");
    }

    public static JsonObject object(JsonElement value, String path) {
        if (value == null || !value.isJsonObject()) throw invalid(path, "must be an object");
        return value.getAsJsonObject();
    }

    public static JsonArray array(JsonObject json, String field, String parent, boolean required) {
        if (!json.has(field) && !required) return new JsonArray();
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonArray()) throw invalid(parent + "." + field, "must be an array");
        return value.getAsJsonArray();
    }

    public static String requiredText(JsonObject json, String field, String path) {
        return text(json.get(field), path + "." + field);
    }

    public static String text(JsonElement value, String path) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank())
            throw invalid(path, "must be a nonempty string");
        return value.getAsString();
    }

    public static int integer(JsonElement value, String path, int minimum) {
        number(value, path);
        try {
            int result = value.getAsBigDecimal().intValueExact();
            if (result < minimum) throw invalid(path, "must be at least " + minimum);
            return result;
        } catch (ArithmeticException e) {
            throw invalid(path, "must be an integer between " + minimum + " and " + Integer.MAX_VALUE);
        }
    }

    public static double number(JsonElement value, String path) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid(path, "must be a number");
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw invalid(path, "must be finite");
        return number;
    }

    public static IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException(path + ": " + reason);
    }
}
