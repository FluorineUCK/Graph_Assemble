package dev.graphassembly.integration.vanilla.smithing;

import dev.graphassembly.recipe.OperationSpec;

import com.mojang.serialization.JsonOps;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.integration.NativeRecipeTemplates;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Exact vanilla transform/trim only. Logical input zero is the base, never the template. */
public final class SmithingDefinition {
    private SmithingDefinition() {}
    public static boolean standard(Recipe<?> recipe) {
        return transform(recipe)||trim(recipe);
    }
    public static boolean transform(Recipe<?> recipe) {
        return recipe.getClass()==SmithingTransformRecipe.class && recipe.getSerializer()==RecipeSerializer.SMITHING_TRANSFORM
            && recipe.getType()==RecipeType.SMITHING;
    }
    public static boolean trim(Recipe<?> recipe) {
        return recipe.getClass()==SmithingTrimRecipe.class && recipe.getSerializer()==RecipeSerializer.SMITHING_TRIM
            && recipe.getType()==RecipeType.SMITHING;
    }
    public static SmithingRecipe create(Level level,NodeDefinition<OperationSpec> node,ItemStack base) {
        var json=node.operation().recipe();var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        if(json.get("type").getAsString().equals("minecraft:smithing_trim")&&node.effects().carrierOverride()!=null)
            throw new IllegalArgumentException("Smithing trim uses its native result carrier; remove node transitional_item");
        if(node.inputs().workpieceInputs().stream().anyMatch(role->role.itemInput()>2))throw new IllegalArgumentException("Smithing has three logical inputs");
        if(json.has("recipe_id")&&!standard(level.getRecipeManager().byKey(ResourceLocation.parse(json.get("recipe_id").getAsString())).orElseThrow().value()))
            throw new IllegalArgumentException("Smithing reference requires exact vanilla transform or trim");
        var original=json.has("recipe_id")?NativeRecipeTemplates.reference(level,json):Recipe.CODEC.parse(ops,json).getOrThrow();
        if(!standard(original))throw new IllegalArgumentException("Unsupported smithing class/type/serializer");
        if(trim(original))return (SmithingTrimRecipe)original; // Native base is a real operation condition, not a graph placeholder.
        return withPrimary(level,(SmithingTransformRecipe)original,Ingredient.of(base.getItem()));
    }
    public static SmithingTransformRecipe withPrimary(Level level,SmithingTransformRecipe recipe,Ingredient base) {
        var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        if(!transform(recipe))throw new IllegalArgumentException("Unsupported smithing transform class/type/serializer");
        var materialized=Recipe.CODEC.encodeStart(ops,recipe).getOrThrow().getAsJsonObject();
        materialized.add("base",Ingredient.CODEC.encodeStart(ops,base).getOrThrow());
        return (SmithingTransformRecipe)Recipe.CODEC.parse(ops,materialized).getOrThrow();
    }
}
