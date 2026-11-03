package dev.graphassembly.integration;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.GraphDefinition;
import dev.graphassembly.core.Node;
import dev.graphassembly.integration.brewing.BrewingDefinition;
import dev.graphassembly.recipe.GraphIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** Read-only native recipes for the graph viewer. Does not grant machine execution support. */
public final class GraphPreviewRecipes {
    private GraphPreviewRecipes() {}

    public static RecipeHolder<?> resolve(Level level, GraphDefinition graph, Node node) {
        var data = node.recipe();
        var serializer = ResourceLocation.parse(data.get("type").getAsString());
        if (data.has("recipe_id")) {
            var id = ResourceLocation.parse(data.get("recipe_id").getAsString());
            var holder = level.getRecipeManager().byKey(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing native recipe " + id));
            var actual = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
            if (!serializer.equals(actual))
                throw new IllegalArgumentException("Native recipe " + id + " uses " + actual + ", not " + serializer);
            return holder; // Preserve ALL inputs, outputs, custom ingredients and component data.
        }
        try {return inline(level,graph,node,serializer,data);}
        catch(PreviewRecipeData.UnknownDynamicCarrier unknown) {
            // Native full definitions can still show their original candidates. Create inline
            // ingredients/results are graph auxiliaries, not a complete native primary recipe.
            if(NativeSteps.EXECUTION_SERIALIZERS.contains(serializer.toString()))throw unknown;
            var candidate=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),data).result();
            if(candidate.isEmpty())throw unknown;
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()),candidate.get());
        }
    }
    private static RecipeHolder<?> inline(Level level,GraphDefinition graph,Node node,ResourceLocation serializer,com.google.gson.JsonObject data) {
        if (serializer.toString().equals(BrewingDefinition.SERIALIZER))
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()), BrewingDefinition.preview(level, node));
        if (serializer.toString().equals(dev.graphassembly.integration.anvil.AnvilDefinition.SERIALIZER))
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()),
                dev.graphassembly.integration.anvil.AnvilDefinition.preview(level,node));
        if (node.nativeRecipe()) {
            // A full inline snapshot has the same display authority as a native reference.
            // Runtime admission and primary substitution remain NativeSteps' responsibility.
            var recipe = Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), data).getOrThrow();
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()), recipe);
        }
        if(dev.graphassembly.integration.crafting.SpecialCraftingDefinition.SERIALIZERS.contains(serializer.toString()))
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()),dev.graphassembly.integration.crafting.SpecialCraftingDefinition.original(level,node));
        if(serializer.toString().equals("minecraft:smithing_trim")) {
            var decoded=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),data).getOrThrow();
            if(!dev.graphassembly.integration.smithing.SmithingDefinition.trim(decoded))throw new IllegalArgumentException("Trim display requires exact vanilla recipe");
            // A dynamic result has no static graph-default carrier. Keep all native candidates.
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()),decoded);
        }
        if(serializer.toString().equals("minecraft:smithing_transform")) {
            var primary=node.start()?NativeSteps.ingredient(level,node.ingredient()==null?graph.ingredient():node.ingredient())
                :net.minecraft.world.item.crafting.Ingredient.of(PreviewRecipeData.carriedInputs(graph,node).stream().map(NativeSteps::stack));
            var decoded=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),data).getOrThrow();
            if(!dev.graphassembly.integration.smithing.SmithingDefinition.standard(decoded))throw new IllegalArgumentException("Smithing display requires exact vanilla transform");
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()),dev.graphassembly.integration.smithing.SmithingDefinition.withPrimary(level,(net.minecraft.world.item.crafting.SmithingTransformRecipe)decoded,primary));
        }
        if(serializer.toString().equals("minecraft:stonecutting")) {
            var primary=node.start()?NativeSteps.ingredient(level,node.ingredient()==null?graph.ingredient():node.ingredient())
                :net.minecraft.world.item.crafting.Ingredient.of(PreviewRecipeData.carriedInputs(graph,node).stream().map(NativeSteps::stack));
            var decoded=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),data).getOrThrow();
            if(!dev.graphassembly.integration.stonecutting.StonecuttingDefinition.standard(decoded))throw new IllegalArgumentException("Stonecutting display requires exact vanilla recipe");
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()),new net.minecraft.world.item.crafting.StonecutterRecipe(decoded.getGroup(),primary,decoded.getResultItem(level.registryAccess()).copy()));
        }
        if(dev.graphassembly.integration.crafting.CraftingDefinition.SERIALIZERS.contains(serializer.toString())) {
            net.minecraft.world.item.crafting.Ingredient primary;
            if(node.start())primary=NativeSteps.ingredient(level,node.ingredient()==null?graph.ingredient():node.ingredient());
            else {
                var carriers=PreviewRecipeData.carriedInputs(graph,node);
                primary=net.minecraft.world.item.crafting.Ingredient.of(NativeSteps.stack(carriers.isEmpty()?graph.transitionalItem():carriers.getFirst()).getItem());
            }
            var decoded=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),data).getOrThrow();
            if(!(decoded instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting))throw new IllegalArgumentException("Crafting display needs a crafting recipe");
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()),
                dev.graphassembly.integration.crafting.CraftingDefinition.withPrimary(level,crafting,primary));
        }
        if(dev.graphassembly.integration.furnace.CookingDefinition.SERIALIZERS.contains(serializer.toString())) {
            var primary=node.start()?NativeSteps.ingredient(level,node.ingredient()==null?graph.ingredient():node.ingredient())
                :net.minecraft.world.item.crafting.Ingredient.of(PreviewRecipeData.carriedInputs(graph,node).stream().map(NativeSteps::stack));
            var decoded=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),data).getOrThrow();
            return new RecipeHolder<>(ResourceLocation.parse(graph.id()),dev.graphassembly.integration.furnace.CookingDefinition.withPrimary(level,(net.minecraft.world.item.crafting.AbstractCookingRecipe)decoded,primary));
        }
        var materialized = PreviewRecipeData.forNode(graph, node);
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var recipe = Recipe.CODEC.parse(ops, materialized).getOrThrow();
        if (recipe instanceof ProcessingRecipe<?, ?> processing && !processing.validate().isEmpty())
            throw new IllegalArgumentException(String.join("; ", processing.validate()));
        return new RecipeHolder<>(ResourceLocation.parse(graph.id()), recipe);
    }

    /** Includes structurally valid graphs whose native recipes can be viewed but not yet executed. */
    public static List<GraphDefinition> graphs(Level level) {
        var executable = GraphIndex.get(level).graphs();
        var result = new ArrayList<GraphDefinition>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(GraphAssembly.RECIPE_TYPE.get())) {
            try {
                var graph = executable.get(holder.id().toString());
                if (graph == null) {
                    graph = holder.value().definition(holder.id().toString());
                    NativeSteps.stack(graph.transitionalItem());
                    for (var node : graph.nodes().values()) {
                        if (node.start()) NativeSteps.ingredient(level, node.ingredient() == null ? graph.ingredient() : node.ingredient());
                        resolve(level, graph, node);
                        node.outputs().forEach(rule -> rule.results().forEach(out -> NativeSteps.output(level, out)));
                        node.scrap().forEach(out -> NativeSteps.output(level, out));
                    }
                    graph.outputs().forEach(rule -> rule.results().forEach(out -> NativeSteps.output(level, out)));
                }
                result.add(graph);
            } catch (RuntimeException error) {
                GraphAssembly.LOGGER.warn("Cannot preview graph {}: {}", holder.id(), error.getMessage());
            }
        }
        return List.copyOf(result);
    }
}
