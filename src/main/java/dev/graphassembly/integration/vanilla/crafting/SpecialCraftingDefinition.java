package dev.graphassembly.integration.vanilla.crafting;

import dev.graphassembly.recipe.OperationSpec;

import com.mojang.serialization.JsonOps;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.integration.NativeRecipeTemplates;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.Set;

/** Dynamic native crafting is never rewritten into a static shapeless recipe. */
public final class SpecialCraftingDefinition {
    public static final Set<String> SERIALIZERS=Set.of("minecraft:crafting_special_armordye","minecraft:crafting_special_repairitem");
    private SpecialCraftingDefinition() {}
    public static boolean standard(Recipe<?> recipe) {
        return recipe.getType()==RecipeType.CRAFTING && (recipe.getClass()==ArmorDyeRecipe.class
            && recipe.getSerializer()==RecipeSerializer.ARMOR_DYE
            || recipe.getClass()==RepairItemRecipe.class && recipe.getSerializer()==RecipeSerializer.REPAIR_ITEM);
    }
    public static CraftingRecipe original(Level level,NodeDefinition<OperationSpec> node) {
        if(node.effects().carrierOverride()!=null)throw new IllegalArgumentException("Special crafting cannot override its native carrier");
        var json=node.operation().recipe();
        if(json.has("recipe_id")) {
            var original=level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(json.get("recipe_id").getAsString())).orElseThrow().value();
            if(!standard(original))throw new IllegalArgumentException("Special crafting reference requires exact vanilla class and serializer");
        }
        Recipe<?> decoded=json.has("recipe_id")?NativeRecipeTemplates.reference(level,json)
            :Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
        if(!standard(decoded))throw new IllegalArgumentException("Unsupported special crafting class or serializer");
        return (CraftingRecipe)decoded;
    }
    /** Unique dyeable item is role zero; dyes retain physical row-major order. */
    public static int[] roles(CraftingRecipe recipe,CraftingInput input) {
        if(recipe.getClass()==RepairItemRecipe.class) {
            return java.util.stream.IntStream.range(0,input.size()).filter(i->!input.getItem(i).isEmpty()).toArray();
        }
        var slots=new java.util.ArrayList<Integer>(); int primary=-1;
        for(int i=0;i<input.size();i++)if(!input.getItem(i).isEmpty()) {
            if(input.getItem(i).is(ItemTags.DYEABLE)) {if(primary>=0)return new int[0];primary=i;}
            else slots.add(i);
        }
        if(primary<0)return new int[0];
        slots.addFirst(primary);return slots.stream().mapToInt(i->i).toArray();
    }
    public static ItemStack carrier(Level level,CraftingRecipe recipe,CraftingInput input) {
        var copied=CraftingInput.of(input.width(),input.height(),input.items().stream().map(ItemStack::copy).toList());
        if(!standard(recipe)||!recipe.matches(copied,level))return ItemStack.EMPTY;
        var result=recipe.assemble(copied,level.registryAccess());
        return result.isEmpty()||result.getCount()!=1||!result.isItemEnabled(level.enabledFeatures())?ItemStack.EMPTY:result.copy();
    }
}
