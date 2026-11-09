package dev.graphassembly.client;

import dev.graphassembly.recipe.OperationSpec;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import dev.graphassembly.client.view.StepPresentation;
import dev.graphassembly.integration.vanilla.campfire.CampfireKind;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.integration.vanilla.anvil.AnvilRecipe;
import dev.graphassembly.integration.vanilla.brewing.BrewingRecipe;
import dev.graphassembly.client.view.TooltipSummary;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import java.util.*;

/** Client presentation shared by JEI and the JEI-independent filter screen. */
public final class StepDescription {
    private StepDescription() {}
    public static Component describeCampfire(NodeDefinition<OperationSpec> node) {
        var normal = Component.translatable("block.minecraft.campfire");
        var soul = Component.translatable("block.minecraft.soul_campfire");
        return switch (node.operation().campfire()) {
            case NORMAL -> normal;
            case SOUL -> soul;
            case ANY -> Component.translatable("graph_assembly.campfire.any", normal, soul);
        };
    }
    public static ItemStack campfireMachine(NodeDefinition<OperationSpec> node) {
        return new ItemStack(node.operation().campfire() == CampfireKind.SOUL ? Items.SOUL_CAMPFIRE : Items.CAMPFIRE);
    }
    public static ItemStack machine(NodeDefinition<OperationSpec> node, Recipe<?> recipe) {
        return node.operation().isCampfire() ? campfireMachine(node) : machine(recipe);
    }
    public static Component player(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if(recipe instanceof ProcessingRecipe<?,?> processing)return player(processing);
        if (recipe instanceof BrewingRecipe) return describe(recipe);
        if(recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe || recipe.getType()==net.minecraft.world.item.crafting.RecipeType.STONECUTTING
            || dev.graphassembly.integration.vanilla.smithing.SmithingDefinition.standard(recipe))return describe(recipe);
        return Component.translatable("graph_assembly.operation.summary",machine(recipe).getHoverName(),describe(recipe));
    }
    public static Component describe(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if(recipe instanceof ProcessingRecipe<?,?> processing)return describe(processing);
        if (recipe instanceof BrewingRecipe brewing)
            return Component.translatable("graph_assembly.operation.summary", machine(brewing).getHoverName(),
                Component.translatable("graph_assembly.brewing.primary", brewingRole(brewing, 0)));
        if(recipe instanceof AnvilRecipe anvil) {
            if(anvil.action()==AnvilRecipe.Action.RENAME) {
                var name=anvil.json().get("name");
                if(name!=null) {
                    String target=name.getAsString();
                    return target.isEmpty()?Component.translatable("graph_assembly.operation.anvil.rename_remove")
                        :Component.translatable("graph_assembly.operation.anvil.rename_to",Component.literal(target));
                }
            }
            return Component.translatable("graph_assembly.operation.anvil."+anvil.action().name().toLowerCase(Locale.ROOT));
        }
        var key=recipe.getType()==net.minecraft.world.item.crafting.RecipeType.SMELTING?"container.furnace":recipe.getType()==net.minecraft.world.item.crafting.RecipeType.SMOKING?"container.smoker":recipe.getType()==net.minecraft.world.item.crafting.RecipeType.BLASTING?"container.blast_furnace":recipe.getType()==net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING?"block.minecraft.campfire":recipe.getType()==net.minecraft.world.item.crafting.RecipeType.CRAFTING?"container.crafting":recipe.getType()==net.minecraft.world.item.crafting.RecipeType.STONECUTTING?"container.stonecutter":recipe.getType()==net.minecraft.world.item.crafting.RecipeType.SMITHING?"block.minecraft.smithing_table":"graph_assembly.operation.unavailable";
        return Component.translatable(key);
    }
    public static ItemStack machine(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if(recipe instanceof ProcessingRecipe<?,?> processing)return machine(processing);
        if (recipe instanceof BrewingRecipe) return new ItemStack(Items.BREWING_STAND);
        if(recipe instanceof AnvilRecipe)return new ItemStack(net.minecraft.world.item.Items.ANVIL);
        var item=recipe.getType()==net.minecraft.world.item.crafting.RecipeType.SMELTING?net.minecraft.world.item.Items.FURNACE:recipe.getType()==net.minecraft.world.item.crafting.RecipeType.SMOKING?net.minecraft.world.item.Items.SMOKER:recipe.getType()==net.minecraft.world.item.crafting.RecipeType.BLASTING?net.minecraft.world.item.Items.BLAST_FURNACE:recipe.getType()==net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING?net.minecraft.world.item.Items.CAMPFIRE:recipe.getType()==net.minecraft.world.item.crafting.RecipeType.CRAFTING?net.minecraft.world.item.Items.CRAFTING_TABLE:recipe.getType()==net.minecraft.world.item.crafting.RecipeType.STONECUTTING?net.minecraft.world.item.Items.STONECUTTER:recipe.getType()==net.minecraft.world.item.crafting.RecipeType.SMITHING?net.minecraft.world.item.Items.SMITHING_TABLE:net.minecraft.world.item.Items.AIR;
        return new ItemStack(item);
    }
    public static List<Component> materials(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        if(recipe instanceof ProcessingRecipe<?,?> processing)return materials(processing);
        if (recipe instanceof BrewingRecipe brewing)
            return List.of(Component.translatable("graph_assembly.brewing.addition", brewingInput(brewing, 1)));
        if(recipe instanceof AnvilRecipe anvil) {
            var names=Component.empty();
            for(var stack:anvil.previewAddition().getItems()) {
                if(!names.getSiblings().isEmpty())names.append(" / ");
                names.append(stack.getHoverName());
            }
            return names.getSiblings().isEmpty()?List.of():List.of(names);
        }
        var ingredients=recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe
            ?List.of(((dev.graphassembly.mixin.vanilla.SmithingTransformAccess)recipe).graphAssembly$template(),
                ((dev.graphassembly.mixin.vanilla.SmithingTransformAccess)recipe).graphAssembly$addition())
            :recipe instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe
                ?List.of(((dev.graphassembly.mixin.vanilla.SmithingTrimAccess)recipe).graphAssembly$template(),
                    ((dev.graphassembly.mixin.vanilla.SmithingTrimAccess)recipe).graphAssembly$addition())
            :recipe.getIngredients().stream().filter(i->!i.isEmpty()).toList();
        var lines=new ArrayList<Component>();
        for(int i=recipe instanceof net.minecraft.world.item.crafting.SmithingTransformRecipe||recipe instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe?0:1;i<ingredients.size();i++) {
            var names=Component.empty();
            for(var stack:ingredients.get(i).getItems()) {
                if(!names.getString().isEmpty())names.append(" / ");
                names.append(stack.getHoverName());
            }
            lines.add(names);
        }
        return lines;
    }
    private static Component brewingRole(BrewingRecipe recipe, int index) {
        boolean bottle = (recipe.primary() == BrewingRecipe.Primary.BOTTLE) == (index == 0);
        return Component.translatable(bottle ? "graph_assembly.brewing.bottle" : "graph_assembly.brewing.ingredient");
    }

    static Component brewingInput(BrewingRecipe recipe, int index) {
        var role = brewingRole(recipe, index);
        if (index == 0) return role;
        var candidates = TooltipSummary.of(recipe.previewAddition());
        if (candidates.shown().isEmpty()) return role;
        var text = role.copy().append(" (");
        boolean first = true;
        for (var item : candidates.shown()) {
            if (!first) text.append(Component.translatable("graph_assembly.join.or"));
            text.append(item.getHoverName());
            first = false;
        }
        if (candidates.remaining() > 0)
            text.append(Component.translatable("graph_assembly.summary.more", candidates.remaining()));
        return text.append(")");
    }

    public static Component player(ProcessingRecipe<?, ?> recipe) {
        var machine = machine(recipe);
        return machine.isEmpty() ? describe(recipe)
            : Component.translatable("graph_assembly.operation.summary", machine.getHoverName(), describe(recipe));
    }
    public static Component describe(ProcessingRecipe<?, ?> recipe) {
        if (recipe instanceof IAssemblyRecipe assembly) return assembly.getDescriptionForAssembly();
        return Component.translatable(StepPresentation.nonAssemblyMachine(serializer(recipe))
            .map(StepPresentation.Machine::descriptionKey).orElse("graph_assembly.operation.unavailable"));
    }
    public static ItemStack machine(ProcessingRecipe<?, ?> recipe) {
        if (recipe instanceof IAssemblyRecipe assembly) {
            Set<ItemLike> machines = new LinkedHashSet<>(); assembly.addRequiredMachines(machines);
            if (!machines.isEmpty()) return new ItemStack(machines.iterator().next());
        }
        return StepPresentation.nonAssemblyMachine(serializer(recipe)).map(presentation -> {
            var id = ResourceLocation.parse(presentation.item());
            return BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
        }).orElse(ItemStack.EMPTY);
    }
    private static String serializer(ProcessingRecipe<?, ?> recipe) {
        var id = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        return id == null ? "" : id.toString();
    }
    public static List<Component> materials(ProcessingRecipe<?, ?> recipe) {
        List<Component> lines = new ArrayList<>();
        for (int i = 1; i < recipe.getIngredients().size(); i++) {
            var candidates = recipe.getIngredients().get(i).getItems();
            var names = Component.empty();
            for (int j = 0; j < candidates.length; j++) {
                if (j > 0) names.append(" / "); names.append(candidates[j].getHoverName());
            }
            lines.add(names);
        }
        for (var fluid : recipe.getFluidIngredients()) {
            var names = Component.empty();
            for (var candidate : fluid.getFluids()) {
                if (!names.getString().isEmpty()) names.append(" / "); names.append(candidate.getHoverName());
            }
            lines.add(Component.translatable("graph_assembly.operation.fluid", names, fluid.amount()));
        }
        return lines;
    }
}
