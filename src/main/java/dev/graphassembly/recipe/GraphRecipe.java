package dev.graphassembly.recipe;

import com.google.gson.JsonObject;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.GraphCodec;
import dev.graphassembly.core.GraphDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Standard recipes carry and synchronize the definition; only native machines execute nodes. */
public final class GraphRecipe implements Recipe<RecipeInput> {
    private final JsonObject json;
    public GraphRecipe(JsonObject json) {
        this.json = json.deepCopy();
        GraphCodec.parse("graph_assembly:unbound", this.json);
    }
    public JsonObject json() { return json.deepCopy(); }
    public GraphDefinition definition(String id) { return GraphCodec.parse(id, json); }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public RecipeSerializer<?> getSerializer() { return GraphAssembly.SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return GraphAssembly.RECIPE_TYPE.get(); }
    @Override public boolean isSpecial() { return true; }
}
