package dev.graphassembly.integration;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.core.*;
import dev.graphassembly.integration.brewing.BrewingDefinition;
import dev.graphassembly.integration.brewing.BrewingRecipe;
import dev.graphassembly.diagnostics.PerformanceMetrics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import java.util.Set;

/** Serializer admission allow-list, not RecipeType routing: discovery alone never grants execution support. */
public final class NativeSteps {
    /** Existing Create processing-device serializers only; native menus have a separately verified family. */
    public static final Set<String> EXECUTION_SERIALIZERS = Set.of("create:pressing", "create:cutting", "create:deploying", "create:filling", "create:milling", "create:mixing", "create:compacting");
    private NativeSteps() {}

    public static boolean supportsExecution(ResourceLocation serializer,Recipe<?> recipe) {
        if(serializer==null)return false;
        return recipe.getClass()==dev.graphassembly.integration.anvil.AnvilRecipe.class && serializer.toString().equals("graph_assembly:anvil")
            || recipe.getClass() == BrewingRecipe.class && serializer.toString().equals(BrewingDefinition.SERIALIZER)
            || dev.graphassembly.integration.crafting.SpecialCraftingDefinition.standard(recipe)
                && serializer.equals(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()))
            || EXECUTION_SERIALIZERS.contains(serializer.toString())
            || dev.graphassembly.integration.stonecutting.StonecuttingDefinition.standard(recipe) && serializer.toString().equals("minecraft:stonecutting")
            || dev.graphassembly.integration.smithing.SmithingDefinition.standard(recipe)
                && (serializer.toString().equals("minecraft:smithing_transform")||serializer.toString().equals("minecraft:smithing_trim"))
            || dev.graphassembly.integration.furnace.CookingDefinition.standard(recipe) && serializer.equals(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()))
            || dev.graphassembly.integration.crafting.CraftingDefinition.SERIALIZERS.contains(serializer.toString())
                && dev.graphassembly.integration.crafting.CraftingDefinition.standard(recipe)
                && serializer.equals(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
    }

    /** Validated template for the specific execution family; never coerces unknown recipe subclasses. */
    public static Recipe<?> template(Level level, GraphDefinition graph, Node node, ItemStack input) {
        String serializer=node.recipe().get("type").getAsString();
        if (serializer.equals(BrewingDefinition.SERIALIZER)) return BrewingDefinition.create(level, node);
        if(serializer.equals("graph_assembly:anvil"))return dev.graphassembly.integration.anvil.AnvilDefinition.create(level,node);
        if(serializer.equals("minecraft:stonecutting"))return dev.graphassembly.integration.stonecutting.StonecuttingDefinition.create(level,node,input);
        if(serializer.equals("minecraft:smithing_transform")||serializer.equals("minecraft:smithing_trim"))return dev.graphassembly.integration.smithing.SmithingDefinition.create(level,node,input);
        if (dev.graphassembly.integration.furnace.CookingDefinition.SERIALIZERS.contains(serializer))
            return dev.graphassembly.integration.furnace.CookingDefinition.create(level,node,input);
        if (dev.graphassembly.integration.crafting.SpecialCraftingDefinition.SERIALIZERS.contains(serializer))
            return dev.graphassembly.integration.crafting.SpecialCraftingDefinition.original(level,node);
        if (dev.graphassembly.integration.crafting.CraftingDefinition.SERIALIZERS.contains(serializer))
            return dev.graphassembly.integration.crafting.CraftingDefinition.create(level,node,input);
        return create(level,graph,node,input);
    }

    public static Ingredient ingredient(Level level, JsonElement json) {
        return Ingredient.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }
    public static ItemStack stack(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalArgumentException("Unknown item " + id);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key));
        if (stack.isEmpty()) throw new IllegalArgumentException("Empty item " + id);
        return stack;
    }
    public static ItemStack output(Level level, WeightedOutput output) {
        JsonObject json = new JsonObject();
        json.addProperty("id", output.item());
        json.addProperty("count", output.count());
        json.add("components", output.components());
        var stack = ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        if (stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Result exceeds one full stack: " + output.item());
        return stack;
    }
    public static ProcessingRecipe<?, ?> create(Level level, GraphDefinition graph, Node node, ItemStack input) {
        JsonObject json = node.recipe().deepCopy();
        boolean reference = json.has("recipe_id");
        String type = json.get("type").getAsString();
        ResourceLocation serializer = ResourceLocation.parse(type);
        if (!BuiltInRegistries.RECIPE_SERIALIZER.containsKey(serializer))
            throw new IllegalArgumentException("Unknown recipe serializer " + type + "; no registered codec or verified device adapter");
        String inputItem = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
        String outputItem = primaryOutputItem(graph,node,input);
        if (!node.completeRecipe()) json = PreviewRecipeData.materialize(json, inputItem, outputItem);
        Recipe<?> decoded;
        try {
            var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            PerformanceMetrics.nativeRecipeDecode();
            decoded = reference ? NativeRecipeTemplates.reference(level, json) : Recipe.CODEC.parse(ops, json).getOrThrow();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Registered recipe serializer " + type + " could not decode the supplied step: " + e.getMessage(), e);
        }
        if (!EXECUTION_SERIALIZERS.contains(type)) throw new IllegalArgumentException(AssemblyDiscovery.inspect(serializer, decoded).diagnostic());
        if (!(decoded instanceof ProcessingRecipe<?, ?> recipe)) throw new IllegalArgumentException("Not a Create processing recipe: " + type);
        if (node.completeRecipe()) {
            if (recipe.getIngredients().isEmpty() || recipe.getRollableResults().isEmpty())
                throw new IllegalArgumentException("Complete native recipe needs a primary item input and output");
            // Graph semantics own logical input/result 0; all other native fields remain untouched.
            recipe.getIngredients().set(0, Ingredient.of(input.getItem()));
            recipe.getRollableResults().set(0, new com.simibubi.create.content.processing.recipe.ProcessingOutput(stack(outputItem), 1));
        }
        for (var role : node.workpieceInputs()) if (role.itemInput() >= recipe.getIngredients().size())
            throw new IllegalArgumentException("workpiece_inputs.item_input " + role.itemInput() + " exceeds native item input count " + recipe.getIngredients().size());
        if (type.equals("create:deploying") && recipe.getIngredients().size() != 2)
            throw new IllegalArgumentException("Deploying requires exactly one auxiliary item ingredient");
        if (type.equals("create:filling") && (recipe.getFluidIngredients().size() != 1 || recipe.getRollableResults().size() != 1))
            throw new IllegalArgumentException("Spout requires exactly one fluid ingredient and cannot carry byproducts");
        if (type.equals("create:milling") && recipe.getProcessingDuration() <= 0)
            throw new IllegalArgumentException("Millstone requires processing_time > 0");
        if (!recipe.getFluidResults().isEmpty()) throw new IllegalArgumentException("Device adapter cannot carry fluid outputs");
        for (var output : recipe.getRollableResults()) {
            var stack = output.getStack();
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Byproduct must be a nonempty, valid-sized stack");
        }
        if (!recipe.validate().isEmpty()) throw new IllegalArgumentException(String.join("; ", recipe.validate()));
        return recipe;
    }
    /** Shared representative primary result; actual settlement still belongs to graph completion. */
    public static String primaryOutputItem(GraphDefinition graph,Node node,ItemStack input) {
        return node.transitionalItem()!=null?node.transitionalItem():dev.graphassembly.recipe.Workpiece.has(input)
            ?BuiltInRegistries.ITEM.getKey(input.getItem()).toString():graph.transitionalItem();
    }
}
