package dev.graphassembly.integration.anvil;

import com.google.gson.*;
import com.mojang.serialization.*;
import dev.graphassembly.GraphAssembly;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.Set;
import java.util.stream.Stream;

/** Synchronized operation declaration, never an independently executable ordinary recipe. */
public final class AnvilRecipe implements Recipe<RecipeInput> {
    public enum Action { WORK, REPAIR, ENCHANT, RENAME, COMBINE }
    private final JsonObject json;
    private final Action action;
    private final Ingredient previewAddition;
    public AnvilRecipe(JsonObject data) { this(data, Ingredient.EMPTY); }
    AnvilRecipe(JsonObject data, Ingredient previewAddition) {
        this.previewAddition=previewAddition;
        json=data.deepCopy();
        for(String key:json.keySet())if(!Set.of("type","action","addition","name").contains(key))throw new IllegalArgumentException("Unknown anvil field "+key);
        if(json.has("type")&&!json.get("type").getAsString().equals("graph_assembly:anvil"))throw new IllegalArgumentException("Not an anvil descriptor");
        String actionName=json.has("action")?text(json.get("action")):"work";
        if(!Set.of("work","repair","enchant","rename","combine").contains(actionName))throw new IllegalArgumentException("Unknown anvil action "+actionName);
        action=Action.valueOf(actionName.toUpperCase(java.util.Locale.ROOT));
        if(json.has("name")) {
            String name=text(json.get("name"));
            if(action!=Action.RENAME||name.length()>50||!StringUtil.filterText(name).equals(name)||!name.isEmpty()&&StringUtil.isBlank(name))throw new IllegalArgumentException("Invalid exact native anvil name");
        }
        if(action==Action.RENAME&&json.has("addition"))throw new IllegalArgumentException("Rename has no addition");
    }
    private static String text(JsonElement value) {
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");
        return value.getAsString();
    }
    public JsonObject json(){return json.deepCopy();}
    public Action action(){return action;}
    public Ingredient previewAddition(){return previewAddition;}
    @Override public boolean matches(RecipeInput input,Level level){return false;}
    @Override public ItemStack assemble(RecipeInput input,HolderLookup.Provider registries){return ItemStack.EMPTY;}
    @Override public ItemStack getResultItem(HolderLookup.Provider registries){return ItemStack.EMPTY;}
    @Override public boolean canCraftInDimensions(int width,int height){return false;}
    @Override public boolean isSpecial(){return true;}
    @Override public RecipeType<?> getType(){return GraphAssembly.ANVIL_TYPE.get();}
    @Override public RecipeSerializer<?> getSerializer(){return GraphAssembly.ANVIL_SERIALIZER.get();}
    public static final class Serializer implements RecipeSerializer<AnvilRecipe> {
        private static final MapCodec<AnvilRecipe> CODEC=new MapCodec<>() {
            @Override public <T> DataResult<AnvilRecipe> decode(DynamicOps<T> ops,MapLike<T> input) {
                try {
                    var data=new JsonObject();input.entries().forEach(entry->data.add(ops.getStringValue(entry.getFirst()).getOrThrow(),ops.convertTo(JsonOps.INSTANCE,entry.getSecond())));
                    return DataResult.success(new AnvilRecipe(data));
                }catch(RuntimeException invalid){return DataResult.error(()->"Invalid anvil operation: "+invalid.getMessage());}
            }
            @Override public <T> RecordBuilder<T> encode(AnvilRecipe value,DynamicOps<T> ops,RecordBuilder<T> prefix) {
                value.json.entrySet().stream().filter(e->!e.getKey().equals("type")).forEach(e->prefix.add(e.getKey(),JsonOps.INSTANCE.convertTo(ops,e.getValue())));return prefix;
            }
            @Override public <T> Stream<T> keys(DynamicOps<T> ops){return Stream.of("action","addition","name").map(ops::createString);}
        };
        private static final StreamCodec<RegistryFriendlyByteBuf,AnvilRecipe> STREAM=StreamCodec.of(
            (buf,value)->buf.writeUtf(value.json.toString(),1_048_576),buf->new AnvilRecipe(JsonParser.parseString(buf.readUtf(1_048_576)).getAsJsonObject()));
        @Override public MapCodec<AnvilRecipe> codec(){return CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,AnvilRecipe> streamCodec(){return STREAM;}
    }
}
