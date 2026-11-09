package dev.graphassembly.compat.jei;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.integration.GraphPreviewRecipes;
import dev.graphassembly.integration.NativeSteps;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Adds native node ingredients to graph lookup without changing execution capabilities. */
public final class GraphNativeRecipeLookupPlugin implements IRecipeManagerPlugin {
    private final Map<GraphDefinition<OperationSpec>, Map<RecipeIngredientRole, Set<IngredientKey>>> ingredients = new IdentityHashMap<>();
    private IJeiRuntime cachedRuntime;
    private Level cachedLevel;
    private List<GraphDefinition<OperationSpec>> graphs = List.of();

    @Override public <V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus) {
        return matchingGraphs(focus).isEmpty() ? List.of() : List.of(GraphJeiPlugin.TYPE);
    }

    @Override public <T, V> List<T> getRecipes(IRecipeCategory<T> category, IFocus<V> focus) {
        if (!category.getRecipeType().equals(GraphJeiPlugin.TYPE)) return List.of();
        return matchingGraphs(focus).stream().map(GraphCategory.Display::new).<T>map(category.getRecipeType().getRecipeClass()::cast).toList();
    }

    @Override public <T> List<T> getRecipes(IRecipeCategory<T> category) {
        // GraphJeiPlugin already registers the ordinary unfocused recipe list.
        return List.of();
    }

    public void clear() {
        ingredients.clear();
        graphs = List.of();
        cachedRuntime = null;
        cachedLevel = null;
    }

    private <V> List<GraphDefinition<OperationSpec>> matchingGraphs(IFocus<V> focus) {
        IJeiRuntime runtime = GraphJeiPlugin.runtime();
        Level level = Minecraft.getInstance().level;
        if (runtime == null || level == null) {
            clear();
            return List.of();
        }
        if (runtime != cachedRuntime || level != cachedLevel) {
            clear();
            cachedRuntime = runtime;
            cachedLevel = level;
            graphs = GraphJeiPlugin.registeredGraphs();
        }
        RecipeIngredientRole role = lookupRole(focus.getRole());
        if (role == RecipeIngredientRole.RENDER_ONLY) return List.of();
        IngredientKey key = key(runtime, focus.getTypedValue());
        List<GraphDefinition<OperationSpec>> matches = new ArrayList<>();
        for (GraphDefinition<OperationSpec> graph : graphs) {
            var index = ingredients.computeIfAbsent(graph, value -> collect(runtime, level, value));
            if (index.getOrDefault(role, Set.of()).contains(key)) matches.add(graph);
        }
        return List.copyOf(matches);
    }

    private static Map<RecipeIngredientRole, Set<IngredientKey>> collect(IJeiRuntime runtime, Level level,
            GraphDefinition<OperationSpec> graph) {
        Map<RecipeIngredientRole, Set<IngredientKey>> result = new EnumMap<>(RecipeIngredientRole.class);
        for (var node : graph.nodes().values()) {
            try {
                var holder = GraphPreviewRecipes.resolve(level, graph, node);
                if (holder.value() instanceof dev.graphassembly.integration.vanilla.brewing.BrewingRecipe brewing) {
                    var keys = result.computeIfAbsent(RecipeIngredientRole.INPUT, ignored -> new HashSet<>());
                    for (var stack : brewing.previewAddition())
                        runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack)
                            .ifPresent(value -> keys.add(key(runtime, value)));
                    continue;
                }
                var supplier = NativeRecipeCategoryPreview.ingredients(runtime, holder);
                if (supplier.isEmpty()) continue;
                boolean completeRecipe = node.operation().completeRecipe();
                var workpieces = new HashSet<IngredientKey>();
                // Unknown dynamic carriers cannot identify a safe exclusion. Keep the
                // native supplier's real candidates/auxiliaries rather than dropping this node.
                for (String item : dev.graphassembly.integration.PreviewRecipeData.nativeInputsToHide(graph, node))
                        runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, NativeSteps.stack(item))
                            .ifPresent(value -> workpieces.add(key(runtime, value)));
                for (var role : List.of(RecipeIngredientRole.INPUT, RecipeIngredientRole.OUTPUT, RecipeIngredientRole.CATALYST)) {
                    // Inline results[0] is the internal workpiece, not a graph product.
                    // Explicit inline byproducts and settlement are already indexed by GraphCategory.
                    if (!completeRecipe && role == RecipeIngredientRole.OUTPUT) continue;
                    var keys = result.computeIfAbsent(lookupRole(role), ignored -> new HashSet<>());
                    for (var ingredient : supplier.get().getIngredients(role)) {
                        var nativeKey = key(runtime, ingredient);
                        if (!completeRecipe && role == RecipeIngredientRole.INPUT && workpieces.contains(nativeKey)) continue;
                        keys.add(nativeKey);
                    }
                }
            } catch (RuntimeException | LinkageError error) {
                GraphAssembly.LOGGER.warn("Cannot index native graph ingredients at {} / {}", graph.id(), node.id(), error);
            }
        }
        return result;
    }

    private static RecipeIngredientRole lookupRole(RecipeIngredientRole role) {
        return role == RecipeIngredientRole.CATALYST ? RecipeIngredientRole.INPUT : role;
    }

    private static <T> IngredientKey key(IJeiRuntime runtime, ITypedIngredient<T> ingredient) {
        var type = ingredient.getType();
        var helper = runtime.getIngredientManager().getIngredientHelper(type);
        return new IngredientKey(type, helper.getUid(ingredient.getIngredient(), UidContext.Recipe));
    }

    private record IngredientKey(IIngredientType<?> type, Object uid) {}
}
