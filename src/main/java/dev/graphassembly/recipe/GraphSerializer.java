package dev.graphassembly.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import java.util.stream.Stream;

public final class GraphSerializer implements RecipeSerializer<GraphRecipe> {
    private static final MapCodec<GraphRecipe> CODEC = new MapCodec<>() {
        @Override public <T> DataResult<GraphRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
            try {
                JsonObject json = new JsonObject();
                input.entries().forEach(e -> json.add(ops.getStringValue(e.getFirst()).getOrThrow(), ops.convertTo(JsonOps.INSTANCE, e.getSecond())));
                return DataResult.success(new GraphRecipe(json));
            } catch (RuntimeException e) { return DataResult.error(() -> "Invalid graph: " + e.getMessage()); }
        }
        @Override public <T> RecordBuilder<T> encode(GraphRecipe value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            value.json().entrySet().stream().filter(e -> !e.getKey().equals("type")).forEach(e -> prefix.add(e.getKey(), JsonOps.INSTANCE.convertTo(ops, e.getValue())));
            return prefix;
        }
        @Override public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of("ingredient", "transitional_item", "nodes", "edges", "outputs").map(ops::createString);
        }
    };
    private static final StreamCodec<RegistryFriendlyByteBuf, GraphRecipe> STREAM = StreamCodec.of(
        (buf, recipe) -> buf.writeUtf(recipe.json().toString(), 1_048_576),
        buf -> new GraphRecipe(JsonParser.parseString(buf.readUtf(1_048_576)).getAsJsonObject()));
    @Override public MapCodec<GraphRecipe> codec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, GraphRecipe> streamCodec() { return STREAM; }
}
