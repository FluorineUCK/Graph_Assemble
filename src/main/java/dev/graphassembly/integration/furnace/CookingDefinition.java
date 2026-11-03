package dev.graphassembly.integration.furnace;

import com.mojang.serialization.JsonOps;
import dev.graphassembly.core.Node;
import dev.graphassembly.integration.NativeRecipeTemplates;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Exact vanilla cooking codecs; no subclass admission through a codec round trip. */
public final class CookingDefinition {
    public static final java.util.Set<String> SERIALIZERS=java.util.Set.of("minecraft:smelting","minecraft:smoking","minecraft:blasting","minecraft:campfire_cooking");
    private CookingDefinition() {}
    public static boolean standard(Recipe<?> recipe) {
        return recipe.getClass()==SmeltingRecipe.class && recipe.getSerializer()==RecipeSerializer.SMELTING_RECIPE
            || recipe.getClass()==SmokingRecipe.class && recipe.getSerializer()==RecipeSerializer.SMOKING_RECIPE
            || recipe.getClass()==BlastingRecipe.class && recipe.getSerializer()==RecipeSerializer.BLASTING_RECIPE
            || recipe.getClass()==CampfireCookingRecipe.class && recipe.getSerializer()==RecipeSerializer.CAMPFIRE_COOKING_RECIPE;
    }
    public static AbstractCookingRecipe create(Level level,Node node,ItemStack input) {
        var json=node.recipe();
        if(json.has("recipe_id")) {
            var original=level.getRecipeManager().byKey(ResourceLocation.parse(json.get("recipe_id").getAsString())).orElseThrow().value();
            if(!standard(original))throw new IllegalArgumentException("Cooking reference requires exact standard vanilla recipe");
        }
        if(node.workpieceInputs().stream().anyMatch(role->role.itemInput()!=0))throw new IllegalArgumentException("Cooking has only primary input 0; fuel is not a workpiece role");
        Recipe<?> recipe=json.has("recipe_id")?NativeRecipeTemplates.reference(level,json)
            :Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
        if(!standard(recipe))throw new IllegalArgumentException("Unsupported cooking class or serializer");
        var cooking=(AbstractCookingRecipe)recipe;
        if(cooking.getCookingTime()<=0 || !Float.isFinite(cooking.getExperience()) || cooking.getExperience()<0)
            throw new IllegalArgumentException("Cooking requires positive cookingtime and finite nonnegative experience");
        return withPrimary(level,(AbstractCookingRecipe)recipe,Ingredient.of(input.getItem()));
    }
    public static AbstractCookingRecipe withPrimary(Level level,AbstractCookingRecipe source,Ingredient primary) {
        var result=source.getResultItem(level.registryAccess()).copy();
        if(source.getClass()==SmeltingRecipe.class)return new SmeltingRecipe(source.getGroup(),source.category(),primary,result,source.getExperience(),source.getCookingTime());
        if(source.getClass()==SmokingRecipe.class)return new SmokingRecipe(source.getGroup(),source.category(),primary,result,source.getExperience(),source.getCookingTime());
        if(source.getClass()==BlastingRecipe.class)return new BlastingRecipe(source.getGroup(),source.category(),primary,result,source.getExperience(),source.getCookingTime());
        if(source.getClass()==CampfireCookingRecipe.class)return new CampfireCookingRecipe(source.getGroup(),source.category(),primary,result,source.getExperience(),source.getCookingTime());
        throw new IllegalArgumentException("Unsupported cooking recipe");
    }
}
