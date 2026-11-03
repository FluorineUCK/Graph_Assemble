package dev.graphassembly.integration;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.graphassembly.core.GraphDefinition;
import dev.graphassembly.core.Node;
import dev.graphassembly.integration.stonecutting.StonecuttingDefinition;
import dev.graphassembly.integration.crafting.CraftingDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** Device-local native acceptance, not registered recipe projections or a second graph matcher. */
public final class CreateNativeRecipes {
    private CreateNativeRecipes() {}
    public static List<RecipeType<?>> sourceTypes(RecipeType<?> deviceType) {
        if(deviceType==AllRecipeTypes.CUTTING.getType())return List.of(deviceType,RecipeType.STONECUTTING);
        if(deviceType==AllRecipeTypes.MIXING.getType()||deviceType==AllRecipeTypes.COMPACTING.getType())return List.of(deviceType,RecipeType.CRAFTING);
        return List.of(deviceType);
    }
    /** Original reference ID owns upstream automation exclusion, not the containing graph ID. */
    public static ResourceLocation automationId(GraphDefinition graph,Node node) {
        return ResourceLocation.parse(node.recipe().has("recipe_id")?node.recipe().get("recipe_id").getAsString():graph.id());
    }
    /** Fan's upstream gate deliberately omits shouldIgnoreInAutomation's serializer tag. */
    public static boolean fanAutomated(GraphOperation operation,AbstractCookingRecipe recipe) {
        return AllRecipeTypes.CAN_BE_AUTOMATED.test(new RecipeHolder<>(automationId(operation.graph(),operation.node()),recipe));
    }
    public static List<RecipeType<? extends AbstractCookingRecipe>> fanSources(FanProcessingType type) {
        if(type==AllFanProcessingTypes.SMOKING)return List.of(RecipeType.SMOKING);
        if(type==AllFanProcessingTypes.BLASTING)return List.of(RecipeType.SMELTING,RecipeType.BLASTING);
        return List.of();
    }
    public static boolean fanAdmitted(Level level,FanProcessingType type,GraphOperation operation,AbstractCookingRecipe recipe) {
        if(!fanAutomated(operation,recipe))return false;
        if(type!=AllFanProcessingTypes.BLASTING)return true;
        // Read-only source qualification, never ordinary execution of a protected workpiece.
        // Compare original native result items, not the graph carrier representative.
        var input=new SingleRecipeInput(operation.primary());
        var smoking=level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING).stream()
            .filter(holder->holder.value().matches(input,level)).findFirst().filter(AllRecipeTypes.CAN_BE_AUTOMATED);
        return smoking.isEmpty()||!ItemStack.isSameItem(smoking.get().value().getResultItem(level.registryAccess()),recipe.getResultItem(level.registryAccess()));
    }
    public static ProcessingRecipe<?,?> processing(Level level,GraphDefinition graph,Node node,ItemStack primary,RecipeType<?> deviceType) {
        if(deviceType==AllRecipeTypes.MIXING.getType()||deviceType==AllRecipeTypes.COMPACTING.getType()) {
            var source=basinSource(level,graph,node,deviceType);
            if(source==null)return null;
            var id=automationId(graph,node);
            // Qualification must see original equivalent ingredients, BEFORE logical primary0
            // is replaced with its graph carrier. Never call client-only convertShapeless.
            var bound=CraftingDefinition.withPrimary(level,source,Ingredient.of(primary.getItem()));
            return new StandardProcessingRecipe.Builder<BasinRecipe>(
                deviceType==AllRecipeTypes.MIXING.getType()?MixingRecipe::new:CompactingRecipe::new,id)
                .withItemIngredients(bound.getIngredients())
                .withSingleItemOutput(NativeSteps.stack(NativeSteps.primaryOutputItem(graph,node,primary))).build();
        }
        if(deviceType!=AllRecipeTypes.CUTTING.getType()||!AllConfigs.server().recipes.allowStonecuttingOnSaw.get())return null;
        var source=StonecuttingDefinition.create(level,node,primary);
        var id=automationId(graph,node);
        if(AllRecipeTypes.shouldIgnoreInAutomation(new RecipeHolder<>(id,source)))return null;
        // Saw uses fifty ticks for native stonecutting, not CuttingRecipe's default duration.
        // Representative output follows the existing graph processing path; settlement owns
        // actual results, so the ordinary source result is never granted as an extra output.
        return new StandardProcessingRecipe.Builder<CuttingRecipe>(CuttingRecipe::new,id)
            .withItemIngredients(source.getIngredients())
            .withSingleItemOutput(NativeSteps.stack(NativeSteps.primaryOutputItem(graph,node,primary)))
            .duration(50).build();
    }
    private static CraftingRecipe basinSource(Level level,GraphDefinition graph,Node node,RecipeType<?> deviceType) {
        var source=CraftingDefinition.original(level,node);
        if(AllRecipeTypes.shouldIgnoreInAutomation(new RecipeHolder<>(automationId(graph,node),source)))return null;
        boolean compress=MechanicalPressBlockEntity.canCompress(source);
        if(deviceType==AllRecipeTypes.MIXING.getType()) {
            if(!(source instanceof ShapelessRecipe)||!AllConfigs.server().recipes.allowShapelessInMixer.get()
                ||source.getIngredients().size()<=1||compress)return null;
        } else if(!compress)return null;
        return source;
    }
    /** Reuse original-source qualification at the final basin pre-RNG boundary. */
    public static boolean basinAdmitted(Level level,GraphOperation operation,RecipeType<?> deviceType) {
        return !CraftingDefinition.SERIALIZERS.contains(operation.node().recipe().get("type").getAsString())
            ||basinSource(level,operation.graph(),operation.node(),deviceType)!=null;
    }
    public static boolean crafterAdmitted(Level level,GraphOperation operation,CraftingInput input) {
        var source=CraftingDefinition.original(level,operation.node());
        return AllConfigs.server().recipes.allowRegularCraftingInCrafter.get()
            &&com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.isRecipeAllowed(
                new RecipeHolder<>(automationId(operation.graph(),operation.node()),source),input);
    }
}
