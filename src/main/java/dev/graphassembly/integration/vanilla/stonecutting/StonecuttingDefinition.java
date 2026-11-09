package dev.graphassembly.integration.vanilla.stonecutting;

import dev.graphassembly.recipe.OperationSpec;

import com.mojang.serialization.JsonOps;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.integration.NativeRecipeTemplates;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Exact vanilla admission before any reference codec can erase a subclass identity. */
public final class StonecuttingDefinition {
    private StonecuttingDefinition() {}
    public static boolean standard(Recipe<?> recipe) {
        return recipe.getClass()==StonecutterRecipe.class && recipe.getSerializer()==RecipeSerializer.STONECUTTER
            && recipe.getType()==RecipeType.STONECUTTING;
    }
    public static StonecutterRecipe create(Level level,NodeDefinition<OperationSpec> node,ItemStack input) {
        var json=node.operation().recipe();
        if(node.inputs().workpieceInputs().stream().anyMatch(role->role.itemInput()!=0))
            throw new IllegalArgumentException("Stonecutting has only primary input 0");
        if(json.has("recipe_id") && !standard(level.getRecipeManager().byKey(ResourceLocation.parse(json.get("recipe_id").getAsString())).orElseThrow().value()))
            throw new IllegalArgumentException("Stonecutting reference requires exact vanilla recipe");
        var recipe=json.has("recipe_id")?NativeRecipeTemplates.reference(level,json)
            :Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
        if(!standard(recipe))throw new IllegalArgumentException("Unsupported stonecutting class/type/serializer");
        return new StonecutterRecipe(recipe.getGroup(),Ingredient.of(input.getItem()),recipe.getResultItem(level.registryAccess()).copy());
    }
}
