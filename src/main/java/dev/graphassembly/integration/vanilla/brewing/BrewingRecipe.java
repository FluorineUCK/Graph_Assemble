package dev.graphassembly.integration.vanilla.brewing;

import com.google.gson.*;
import com.mojang.serialization.*;
import dev.graphassembly.GraphAssembly;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Synchronized operation declaration, never an independently executable ordinary recipe. */
public final class BrewingRecipe implements Recipe<RecipeInput> {
    public enum Primary { BOTTLE, INGREDIENT }
    private final JsonObject json;
    private final Primary primary;
    private final Ingredient addition;
    BrewingRecipe(JsonObject data) { this(data, Ingredient.EMPTY); }
    BrewingRecipe(JsonObject data, Ingredient addition) {
        this.addition=addition;
        json=data.deepCopy();
        for (String key : json.keySet()) if (!Set.of("type", "primary", "addition").contains(key))
            throw new IllegalArgumentException("Unknown brewing field " + key);
        if (json.has("type") && !text(json.get("type")).equals(BrewingDefinition.SERIALIZER))
            throw new IllegalArgumentException("Not a brewing descriptor");
        primary = switch (json.has("primary") ? text(json.get("primary")) : "bottle") {
            case "bottle" -> Primary.BOTTLE;
            case "ingredient" -> Primary.INGREDIENT;
            default -> throw new IllegalArgumentException("Brewing primary must be bottle or ingredient");
        };
    }
    private static String text(JsonElement value) {
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");
        return value.getAsString();
    }
    public JsonObject json(){return json.deepCopy();}
    public Primary primary(){return primary;}
    /** Display candidates only; callers cannot modify the runtime predicate or its cached stacks. */
    public List<ItemStack> previewAddition() {
        return json.has("addition") ? Arrays.stream(addition.getItems()).map(ItemStack::copy).toList() : List.of();
    }

    /** Single-stack role compatibility only; the other input and native mix are not checked. */
    public boolean matchesInput(PotionBrewing brewing, int logicalIndex, ItemStack stack) {
        if (stack.isEmpty() || logicalIndex < 0 || logicalIndex > 1) return false;
        boolean bottle = (primary == Primary.BOTTLE) == (logicalIndex == 0);
        boolean nativeRole = bottle ? brewing.isInput(stack.copy()) : brewing.isIngredient(stack.copy());
        return nativeRole && (logicalIndex == 0 || matchesAddition(stack));
    }

    boolean matchesAddition(ItemStack auxiliary) {
        return !json.has("addition") || addition.test(auxiliary.copy());
    }
    @Override public boolean matches(RecipeInput input,Level level){return false;}
    @Override public ItemStack assemble(RecipeInput input,HolderLookup.Provider registries){return ItemStack.EMPTY;}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return ItemStack.EMPTY;}
    @Override public boolean canCraftInDimensions(int width,int height){return false;}
    @Override public boolean isSpecial(){return true;}
    @Override public RecipeType<?> getType(){return GraphAssembly.BREWING_TYPE.get();}
    @Override public RecipeSerializer<?> getSerializer(){return GraphAssembly.BREWING_SERIALIZER.get();}
    public static final class Serializer implements RecipeSerializer<BrewingRecipe> {
        private static final MapCodec<BrewingRecipe> CODEC=new MapCodec<>() {
            @Override public <T> DataResult<BrewingRecipe> decode(DynamicOps<T> ops,MapLike<T> input) {
                try {
                    var data=new JsonObject();input.entries().forEach(entry->data.add(ops.getStringValue(entry.getFirst()).getOrThrow(),ops.convertTo(JsonOps.INSTANCE,entry.getSecond())));
                    var addition = input.get("addition");
                    var ingredient = addition == null ? Ingredient.EMPTY : Ingredient.CODEC.parse(ops, addition).getOrThrow();
                    return DataResult.success(new BrewingRecipe(data, ingredient));
                }catch(RuntimeException invalid){return DataResult.error(()->"Invalid brewing operation: "+invalid.getMessage());}
            }
            @Override public <T> RecordBuilder<T> encode(BrewingRecipe value,DynamicOps<T> ops,RecordBuilder<T> prefix) {
                value.json.entrySet().stream().filter(e->!e.getKey().equals("type")).forEach(e->prefix.add(e.getKey(),JsonOps.INSTANCE.convertTo(ops,e.getValue())));return prefix;
            }
            @Override public <T> Stream<T> keys(DynamicOps<T> ops){return Stream.of("primary","addition").map(ops::createString);}
        };
        private static final StreamCodec<RegistryFriendlyByteBuf,BrewingRecipe> STREAM=StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.json.toString(), 1_048_576), buf -> {
                var json = JsonParser.parseString(buf.readUtf(1_048_576)).getAsJsonObject();
                var ops = buf.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                var addition = json.has("addition") ? Ingredient.CODEC.parse(ops, json.get("addition")).getOrThrow() : Ingredient.EMPTY;
                return new BrewingRecipe(json, addition);
            });
        @Override public MapCodec<BrewingRecipe> codec(){return CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,BrewingRecipe> streamCodec(){return STREAM;}
    }
}
