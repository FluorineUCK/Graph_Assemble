package dev.graphassembly.integration.vanilla.crafting;

import dev.graphassembly.recipe.OperationSpec;

import com.mojang.serialization.JsonOps;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.integration.NativeRecipeTemplates;
import dev.graphassembly.diagnostics.PerformanceMetrics;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.Optional;

/** Exact vanilla recipe classes and serializers only. Primary is the first nonempty logical ingredient. */
public final class CraftingDefinition {
    public static final java.util.Set<String> SERIALIZERS=java.util.Set.of("minecraft:crafting_shaped","minecraft:crafting_shapeless");
    private CraftingDefinition() {}
    public static boolean standard(Recipe<?> recipe) {
        return recipe.getClass()==ShapedRecipe.class && recipe.getSerializer()==RecipeSerializer.SHAPED_RECIPE
            || recipe.getClass()==ShapelessRecipe.class && recipe.getSerializer()==RecipeSerializer.SHAPELESS_RECIPE;
    }
    public static CraftingRecipe create(Level level,NodeDefinition<OperationSpec> node,ItemStack input) {
        return withPrimary(level,original(level,node),Ingredient.of(input.getItem()));
    }
    /** Validated unmodified native template for upstream device qualification. */
    public static CraftingRecipe original(Level level,NodeDefinition<OperationSpec> node) {
        var json=node.operation().recipe();
        if(json.has("recipe_id")) {
            var original=level.getRecipeManager().byKey(ResourceLocation.parse(json.get("recipe_id").getAsString())).orElseThrow().value();
            if(!standard(original))throw new IllegalArgumentException("Crafting reference must be an exact standard vanilla recipe class");
        }
        PerformanceMetrics.nativeRecipeDecode();
        Recipe<?> decoded=json.has("recipe_id") ? NativeRecipeTemplates.reference(level,json)
            : Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
        if(!standard(decoded))throw new IllegalArgumentException("Unsupported crafting class or serializer");
        var source=(CraftingRecipe)decoded;
        long roles=source.getIngredients().stream().filter(ingredient->!ingredient.isEmpty()).count();
        for(var role:node.inputs().workpieceInputs())if(role.itemInput()>=roles)throw new IllegalArgumentException("workpiece_inputs.item_input exceeds crafting role count");
        return source;
    }

    /** Read-only ingredient substitution; execution admission is deliberately checked by the caller. */
    public static CraftingRecipe withPrimary(Level level,CraftingRecipe source,Ingredient primary) {
        var ingredients=NonNullList.<Ingredient>create(); ingredients.addAll(source.getIngredients());
        int first=-1;
        for(int i=0;i<ingredients.size();i++)if(!ingredients.get(i).isEmpty()) {first=i;break;}
        if(first<0)throw new IllegalArgumentException("Crafting needs a primary ingredient");
        ingredients.set(first,primary);
        if(source instanceof ShapedRecipe shaped)return new ShapedRecipe(source.getGroup(),source.category(),
            new ShapedRecipePattern(shaped.getWidth(),shaped.getHeight(),ingredients,Optional.empty()),source.getResultItem(level.registryAccess()).copy(),source.showNotification());
        return new ShapelessRecipe(source.getGroup(),source.category(),source.getResultItem(level.registryAccess()).copy(),ingredients);
    }
}
