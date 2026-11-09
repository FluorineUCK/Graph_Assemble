package dev.graphassembly.compat.jei;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.integration.GraphPreviewRecipes;
import mezz.jei.api.*;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import dev.graphassembly.client.view.PreviewReloads;

@JeiPlugin
public final class GraphJeiPlugin implements IModPlugin {
    private static mezz.jei.api.runtime.IJeiRuntime runtime;
    private static java.util.List<GraphDefinition<OperationSpec>> registeredGraphs = java.util.List.of();
    private static final GraphNativeRecipeLookupPlugin nativeLookup = new GraphNativeRecipeLookupPlugin();
    static java.util.List<GraphDefinition<OperationSpec>> registeredGraphs() { return registeredGraphs; }
    private static final dev.graphassembly.client.view.RecipeViewStateCache<GraphDefinition<OperationSpec>, GraphViewState> views =
        new dev.graphassembly.client.view.RecipeViewStateCache<>();
    static GraphViewState viewState(GraphDefinition<OperationSpec> graph) {
        return views.get(graph, () -> new GraphViewState(ViewPreference.sphere(), graph));
    }
    private static final java.util.Set<GraphWidget> widgets = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final java.util.function.Consumer<PreviewReloads.Change> lifecycle = GraphJeiPlugin::changed;
    static mezz.jei.api.runtime.IJeiRuntime runtime() { return runtime; }
    private static void changed(PreviewReloads.Change change) {
        if (change == PreviewReloads.Change.SESSION_ENDED) {
            stop(true);
            return;
        }
        release();
        if (change == PreviewReloads.Change.RECIPES) {
            var level = Minecraft.getInstance().level;
            registeredGraphs = level == null ? java.util.List.of() : GraphPreviewRecipes.graphs(level);
            // JEI's StartEventObserver restarts registration on recipe sync. Do not hide/add
            // here: hiding is not removal and would retain replaced recipes in JEI's index.
        }
    }
    static void track(GraphWidget widget) { widgets.add(widget); }
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime value) {
        release(); runtime = value;
        PreviewReloads.installLifecycle(lifecycle);
    }
    private static void release() {
        NativeRecipeCategoryPreview.clear(); nativeLookup.clear();
        widgets.forEach(GraphWidget::clearPreviews);
    }
    @Override public void onRuntimeUnavailable() {
        stop(Minecraft.getInstance().level == null || !Minecraft.getInstance().isRunning());
    }
    private static void stop(boolean clearViews) {
        PreviewReloads.removeLifecycle(lifecycle);
        runtime = null;
        release();
        registeredGraphs = java.util.List.of();
        if (clearViews) views.clear();
        widgets.clear();
    }
    public static final RecipeType<GraphCategory.Display> TYPE = RecipeType.create(GraphAssembly.ID, "assembly", GraphCategory.Display.class);
    @Override public ResourceLocation getPluginUid() { return GraphAssembly.id("jei"); }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        var handler = new GraphFilterGuiHandler();
        registration.addGuiScreenHandler(dev.graphassembly.client.GraphFilterScreen.class, handler);
        registration.addGlobalGuiHandler(handler);
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new GraphCategory(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeManagerPlugin(nativeLookup);
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registeredGraphs = GraphPreviewRecipes.graphs(level);
            registration.addRecipes(TYPE, registeredGraphs.stream().map(GraphCategory.Display::new).toList());
            registeredGraphs.forEach(graph -> SettlementInfo.register(registration, level, graph));
        }
    }
}
