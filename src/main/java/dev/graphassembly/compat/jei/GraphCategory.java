package dev.graphassembly.compat.jei;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.client.view.RecipeViewport;
import dev.graphassembly.core.*;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.integration.GraphPreviewRecipes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class GraphCategory implements IRecipeCategory<GraphDefinition> {
    private final IDrawable icon;
    private final IGuiHelper helper;
    public GraphCategory(IGuiHelper helper) { this.helper = helper; icon = helper.createDrawableItemStack(GraphAssembly.FILTER.get().getDefaultInstance()); }
    @Override public RecipeType<GraphDefinition> getRecipeType() { return GraphJeiPlugin.TYPE; }
    @Override public Component getTitle() { return Component.translatable("graph_assembly.jei.title"); }
    private static RecipeViewport viewport() {
        var window = Minecraft.getInstance().getWindow();
        return RecipeViewport.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }
    @Override public int getWidth() { return viewport().width(); }
    @Override public int getHeight() { return viewport().height(); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public ResourceLocation getRegistryName(GraphDefinition recipe) { return ResourceLocation.parse(recipe.id()); }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, GraphDefinition graph, IFocusGroup focuses) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var inputs = new ArrayList<net.minecraft.world.item.ItemStack>();
        for (var node : graph.nodes().values()) {
            if (node.start()) inputs.addAll(Arrays.asList(NativeSteps.ingredient(level, node.ingredient() == null ? graph.ingredient() : node.ingredient()).getItems()));
            try {
                var holder = GraphPreviewRecipes.resolve(level, graph, node);
                if (node.completeRecipe()) {
                // Complete native recipes (including custom/hidden ingredients) are indexed
                // by GraphNativeRecipeLookupPlugin from JEI's supplier, not guessed from a recipe class.
                } else if (holder.value() instanceof dev.graphassembly.integration.brewing.BrewingRecipe brewing)
                    inputs.addAll(brewing.previewAddition());
                else if (holder.value() instanceof ProcessingRecipe<?, ?> processing)
                    GraphRecipeIngredients.addProcessing(builder, processing);
            } catch(dev.graphassembly.integration.PreviewRecipeData.UnknownDynamicCarrier unknown) {
                // Skip only this node's unknowable material enrichment; the diagram, known
                // ingredients and settlement remain available. GraphWidget diagnoses the node.
            }
        }
        // Lookup is complete and independent of the currently selected finish or popup page.
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(inputs);
        var groups = dev.graphassembly.client.view.SettlementGroups.from(graph);
        var outputs = new ArrayList<net.minecraft.world.item.ItemStack>();
        groups.overview().forEach(candidate -> outputs.add(NativeSteps.output(level, candidate.output())));
        groups.finishes().forEach(finish -> finish.scrap().forEach(candidate -> outputs.add(NativeSteps.output(level, candidate.output()))));
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(outputs);
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, GraphDefinition graph, IFocusGroup focuses) {
        var widget = new ResponsiveGraphWidget(new GraphWidget(graph, helper, focuses), viewport());
        builder.addSlottedWidget(widget, widget.allSlots()); builder.addInputHandler(widget);
    }
}
