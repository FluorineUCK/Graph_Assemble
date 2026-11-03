package dev.graphassembly.integration;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

/** Native CODEC round-trips preserve addon-owned fields; references never guess a JSON schema. */
public final class NativeRecipeTemplates {
    private NativeRecipeTemplates() {}

    public static Recipe<?> copy(Level level, Recipe<?> recipe) {
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        return Recipe.CODEC.parse(ops, Recipe.CODEC.encodeStart(ops, recipe).getOrThrow()).getOrThrow();
    }

    public static Recipe<?> reference(Level level, JsonObject declaration) {
        if (declaration.size() != 2 || !declaration.has("type") || !declaration.has("recipe_id"))
            throw new IllegalArgumentException("recipe_id requires exactly type and recipe_id; configure native fields in the referenced recipe");
        var id = ResourceLocation.parse(declaration.get("recipe_id").getAsString());
        var source = level.getRecipeManager().byKey(id)
            .orElseThrow(() -> new IllegalArgumentException("Missing native recipe reference " + id)).value();
        var serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(source.getSerializer());
        if (!declaration.get("type").getAsString().equals(String.valueOf(serializer)))
            throw new IllegalArgumentException("Native reference serializer mismatch: " + id + " uses " + serializer);
        return copy(level, source);
    }
}
