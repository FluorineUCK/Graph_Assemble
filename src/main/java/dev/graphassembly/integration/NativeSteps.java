package dev.graphassembly.integration;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.io.WeightedOutput;
import dev.graphassembly.core.io.OutputItem;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.integration.vanilla.brewing.BrewingDefinition;
import dev.graphassembly.integration.vanilla.brewing.BrewingRecipe;
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
    /** Decoding a codec, executing on a device and publishing a candidate are separate permissions. */
    public enum Route { DEVICE, CANDIDATE, UNSUPPORTED }
    public enum Adapter {
        CREATE(Set.of("create:pressing", "create:cutting", "create:deploying", "create:filling", "create:milling", "create:crushing", "create:emptying", "create:mixing", "create:compacting", "create:splashing", "create:haunting", "create:sandpaper_polishing"), Route.DEVICE),
        ANVIL(Set.of("graph_assembly:anvil"), Route.DEVICE),
        BREWING(Set.of(BrewingDefinition.SERIALIZER), Route.DEVICE),
        STONECUTTING(Set.of("minecraft:stonecutting"), Route.DEVICE),
        SMITHING(Set.of("minecraft:smithing_transform", "minecraft:smithing_trim"), Route.DEVICE),
        COOKING(dev.graphassembly.integration.vanilla.furnace.CookingDefinition.SERIALIZERS, Route.DEVICE),
        SPECIAL_CRAFTING(dev.graphassembly.integration.vanilla.crafting.SpecialCraftingDefinition.SERIALIZERS, Route.DEVICE),
        CRAFTING(dev.graphassembly.integration.vanilla.crafting.CraftingDefinition.SERIALIZERS, Route.DEVICE),
        UNSUPPORTED(Set.of(), Route.UNSUPPORTED);

        private final Set<String> serializers;
        private final Route route;
        Adapter(Set<String> serializers,Route route){this.serializers=Set.copyOf(serializers);this.route=route;}
        public Route route(){return route;}
    }
    /** Existing Create processing devices only; retained for preview diagnostics. */
    public static final Set<String> EXECUTION_SERIALIZERS = Adapter.CREATE.serializers;
    private NativeSteps() {}
    public static Adapter adapter(String serializer) {
        for(var adapter:Adapter.values())if(adapter.serializers.contains(serializer))return adapter;
        return Adapter.UNSUPPORTED;
    }
    public static boolean supportsExecution(ResourceLocation serializer,Recipe<?> recipe) {
        if(serializer==null||!serializer.equals(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer())))return false;
        return switch(adapter(serializer.toString())) {
            case CREATE -> recipe instanceof ProcessingRecipe<?,?>;
            case ANVIL -> recipe.getClass()==dev.graphassembly.integration.vanilla.anvil.AnvilRecipe.class;
            case BREWING -> recipe.getClass()==BrewingRecipe.class;
            case STONECUTTING -> dev.graphassembly.integration.vanilla.stonecutting.StonecuttingDefinition.standard(recipe);
            case SMITHING -> dev.graphassembly.integration.vanilla.smithing.SmithingDefinition.standard(recipe);
            case COOKING -> dev.graphassembly.integration.vanilla.furnace.CookingDefinition.standard(recipe);
            case SPECIAL_CRAFTING -> dev.graphassembly.integration.vanilla.crafting.SpecialCraftingDefinition.standard(recipe);
            case CRAFTING -> dev.graphassembly.integration.vanilla.crafting.CraftingDefinition.standard(recipe);
            case UNSUPPORTED -> false;
        };
    }
    /** Validated template for the specific execution family; never coerces unknown recipe subclasses. */
    public static Recipe<?> template(Level level, GraphDefinition<OperationSpec> graph, NodeDefinition<OperationSpec> node, ItemStack input) {
        return switch(adapter(node.operation().recipe().get("type").getAsString())) {
            case BREWING -> BrewingDefinition.create(level,node);
            case ANVIL -> dev.graphassembly.integration.vanilla.anvil.AnvilDefinition.create(level,node);
            case STONECUTTING -> dev.graphassembly.integration.vanilla.stonecutting.StonecuttingDefinition.create(level,node,input);
            case SMITHING -> dev.graphassembly.integration.vanilla.smithing.SmithingDefinition.create(level,node,input);
            case COOKING -> dev.graphassembly.integration.vanilla.furnace.CookingDefinition.create(level,node,input);
            case SPECIAL_CRAFTING -> dev.graphassembly.integration.vanilla.crafting.SpecialCraftingDefinition.original(level,node);
            case CRAFTING -> dev.graphassembly.integration.vanilla.crafting.CraftingDefinition.create(level,node,input);
            // Unknown registered codecs remain discoverable for diagnostics, not executable.
            case CREATE, UNSUPPORTED -> create(level,graph,node,input);
        };
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
        return output(level, new OutputItem(output.item(), output.count(), output.components()));
    }
    public static ItemStack output(Level level, OutputItem output) {
        JsonObject json = new JsonObject();
        json.addProperty("id", output.item());
        json.addProperty("count", output.count());
        json.add("components", output.components());
        var stack = ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
        if (stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Result exceeds one full stack: " + output.item());
        return stack;
    }
    public static ProcessingRecipe<?, ?> create(Level level, GraphDefinition<OperationSpec> graph, NodeDefinition<OperationSpec> node, ItemStack input) {
        JsonObject json = node.operation().recipe().deepCopy();
        boolean reference = json.has("recipe_id");
        String type = json.get("type").getAsString();
        ResourceLocation serializer = ResourceLocation.parse(type);
        if (!BuiltInRegistries.RECIPE_SERIALIZER.containsKey(serializer))
            throw new IllegalArgumentException("Unknown recipe serializer " + type + "; no registered codec or verified device adapter");
        String inputItem = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
        String outputItem = primaryOutputItem(graph,node,input);
        if (!node.operation().completeRecipe()) json = PreviewRecipeData.materialize(json, inputItem, outputItem);
        Recipe<?> decoded;
        try {
            var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            PerformanceMetrics.nativeRecipeDecode();
            decoded = reference ? NativeRecipeTemplates.reference(level, json) : Recipe.CODEC.parse(ops, json).getOrThrow();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Registered recipe serializer " + type + " could not decode the supplied step: " + e.getMessage(), e);
        }
        if (adapter(type)!=Adapter.CREATE) throw new IllegalArgumentException(AssemblyDiscovery.inspect(serializer, decoded).diagnostic());
        if (!(decoded instanceof ProcessingRecipe<?, ?> recipe)) throw new IllegalArgumentException("Not a Create processing recipe: " + type);
        if (node.operation().completeRecipe()) {
            if (recipe.getIngredients().isEmpty() || recipe.getRollableResults().isEmpty())
                throw new IllegalArgumentException("Complete native recipe needs a primary item input and output");
            // Graph semantics own logical input/result 0; all other native fields remain untouched.
            recipe.getIngredients().set(0, Ingredient.of(input.getItem()));
            recipe.getRollableResults().set(0, new com.simibubi.create.content.processing.recipe.ProcessingOutput(stack(outputItem), 1));
        }
        for (var role : node.inputs().workpieceInputs()) if (role.itemInput() >= recipe.getIngredients().size())
            throw new IllegalArgumentException("workpiece_inputs.item_input " + role.itemInput() + " exceeds native item input count " + recipe.getIngredients().size());
        if (type.equals("create:deploying") && recipe.getIngredients().size() != 2)
            throw new IllegalArgumentException("Deploying requires exactly one auxiliary item ingredient");
        if (type.equals("create:filling") && (recipe.getFluidIngredients().size() != 1 || recipe.getRollableResults().size() != 1))
            throw new IllegalArgumentException("Spout requires exactly one fluid ingredient and cannot carry byproducts");
        if (type.equals("create:milling") && recipe.getProcessingDuration() <= 0)
            throw new IllegalArgumentException("Millstone requires processing_time > 0");
        if (type.equals("create:emptying")) {
            if (recipe.getIngredients().size()!=1 || !recipe.getFluidIngredients().isEmpty()
                || recipe.getRollableResults().size()!=1 || recipe.getFluidResults().size()!=1
                || !DrainOperations.validFluid(level,recipe.getFluidResults().getFirst()))
                throw new IllegalArgumentException("Item drain requires one primary item and exactly one nonempty fluid output, without auxiliary inputs/byproducts");
        } else if (!recipe.getFluidResults().isEmpty()) throw new IllegalArgumentException("Device adapter cannot carry fluid outputs");
        for (var output : recipe.getRollableResults()) {
            var stack = output.getStack();
            if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Byproduct must be a nonempty, valid-sized stack");
        }
        if (!recipe.validate().isEmpty()) throw new IllegalArgumentException(String.join("; ", recipe.validate()));
        return recipe;
    }
    /** Shared representative primary result; actual settlement still belongs to graph completion. */
    public static String primaryOutputItem(GraphDefinition<OperationSpec> graph,NodeDefinition<OperationSpec> node,ItemStack input) {
        return node.effects().carrierOverride()!=null?node.effects().carrierOverride():dev.graphassembly.recipe.Workpiece.has(input)
            ?BuiltInRegistries.ITEM.getKey(input.getItem()).toString():graph.defaults().transitionalItem();
    }
}
