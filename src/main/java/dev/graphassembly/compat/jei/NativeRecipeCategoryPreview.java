package dev.graphassembly.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Embeds a registered category, including its slots and widgets, without recreating its artwork. */
public final class NativeRecipeCategoryPreview {
    private static final Map<IRecipeManager, List<Category>> CATALOGS = new WeakHashMap<>();
    private NativeRecipeCategoryPreview() {}

    public static Optional<IRecipeLayoutDrawable<?>> create(IJeiRuntime runtime, RecipeHolder<?> recipe,
            IFocusGroup focuses) {
        return find(runtime.getRecipeManager(), recipe).flatMap(match -> match.create(runtime.getRecipeManager(), focuses));
    }

    /** Includes every native ingredient alternative and invisible/custom ingredients, not just the current frame. */
    public static Optional<IIngredientSupplier> ingredients(IJeiRuntime runtime, RecipeHolder<?> recipe) {
        return find(runtime.getRecipeManager(), recipe).map(match -> match.ingredients(runtime.getRecipeManager()));
    }

    /** Call from a layout's setRecipe; recipe extras are too late for JEI's ingredient index. */
    public static void copyIngredients(IRecipeLayoutBuilder builder, IIngredientSupplier ingredients) {
        for (var role : RecipeIngredientRole.values()) {
            var values = ingredients.getIngredients(role);
            if (!values.isEmpty()) builder.addInvisibleIngredients(role).addTypedIngredients(values);
        }
    }

    public static void clear() { CATALOGS.clear(); }

    private static Optional<Match> find(IRecipeManager manager, RecipeHolder<?> holder) {
        var recipe = holder.value();
        var serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        var typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        var preferredId = preferredCategory(serializerId);
        var categories = CATALOGS.computeIfAbsent(manager, key -> key.createRecipeCategoryLookup().includeHidden().get()
            .filter(category -> !category.getRecipeType().getUid().getNamespace().equals("graph_assembly"))
            .map(Category::new).toList());

        // A dedicated serializer/category identity beats automatic conversions that use the same Java class.
        for (var preferred : new ResourceLocation[] { preferredId, serializerId, typeId }) {
            if (preferred == null) continue;
            for (var category : categories) {
                if (category.category.getRecipeType().getUid().equals(preferred)) {
                    var payload = category.payload(holder);
                    if (payload != null && category.handles(payload)) return Optional.of(new Match(category.category, payload));
                }
            }
        }

        List<Match> best = new ArrayList<>();
        int bestScore = 0;
        for (var category : categories) {
            var payload = category.payload(holder);
            int score = 0;
            for (var sample : category.samples(manager)) {
                var sampledRecipe = unwrap(sample);
                if (sampledRecipe != null && compatible(recipe, sampledRecipe)) {
                    int candidateScore = sampledRecipe == recipe ? 3
                        : sample instanceof RecipeHolder<?> sampledHolder && sampledHolder.id().equals(holder.id()) ? 2 : 1;
                    score = Math.max(score, candidateScore);
                } else if (payload == null && holder.id().equals(category.registryName(sample))) {
                    // An arbitrary JEI wrapper cannot be synthesized safely. Reuse its registered payload by ID.
                    payload = sample;
                    score = 2;
                }
            }
            if (score == 0 || payload == null || !category.handles(payload)) continue;
            if (score > bestScore) { best.clear(); bestScore = score; }
            if (score == bestScore) best.add(new Match(category.category, payload));
        }
        // Shared classes/types (e.g. fractionation versus condensation) need real recipe membership, not first-match order.
        return best.size() == 1 ? Optional.of(best.getFirst()) : Optional.empty();
    }

    private static ResourceLocation preferredCategory(ResourceLocation serializer) {
        if (serializer == null) return null;
        return switch (serializer.toString()) {
            case "minecraft:smoking" -> ResourceLocation.parse("create:fan_smoking");
            case "minecraft:smelting", "minecraft:blasting" -> ResourceLocation.parse("create:fan_blasting");
            case "create:splashing" -> ResourceLocation.parse("create:fan_washing");
            case "create:haunting" -> ResourceLocation.parse("create:fan_haunting");
            default -> serializer;
        };
    }

    private static Recipe<?> unwrap(Object value) {
        return value instanceof RecipeHolder<?> holder ? holder.value() : value instanceof Recipe<?> recipe ? recipe : null;
    }

    private static boolean compatible(Recipe<?> recipe, Recipe<?> sample) {
        return recipe.getType() == sample.getType() && recipe.getClass() == sample.getClass();
    }

    private static final class Category {
        private final IRecipeCategory<?> category;
        private List<?> samples;
        private Category(IRecipeCategory<?> category) { this.category = category; }
        private Object payload(RecipeHolder<?> holder) {
            Class<?> payloadClass = category.getRecipeType().getRecipeClass();
            if (RecipeHolder.class.isAssignableFrom(payloadClass) && payloadClass.isInstance(holder)) return holder;
            if (Recipe.class.isAssignableFrom(payloadClass) && payloadClass.isInstance(holder.value())) return holder.value();
            return null;
        }
        private List<?> samples(IRecipeManager manager) {
            if (samples == null) samples = manager.createRecipeLookup(category.getRecipeType()).includeHidden().get().toList();
            return samples;
        }
        @SuppressWarnings("unchecked")
        private boolean handles(Object payload) { return ((IRecipeCategory<Object>)category).isHandled(payload); }
        @SuppressWarnings("unchecked")
        private ResourceLocation registryName(Object payload) {
            return ((IRecipeCategory<Object>)category).getRegistryName(payload);
        }
    }

    private record Match(IRecipeCategory<?> category, Object recipe) {
        @SuppressWarnings("unchecked")
        private Optional<IRecipeLayoutDrawable<?>> create(IRecipeManager manager, IFocusGroup focuses) {
            return manager.createRecipeLayoutDrawable((IRecipeCategory<Object>)category, recipe, focuses,
                (graphics, x, y, width, height) -> {}, 0).map(layout -> layout);
        }
        @SuppressWarnings("unchecked")
        private IIngredientSupplier ingredients(IRecipeManager manager) {
            return manager.getRecipeIngredients((IRecipeCategory<Object>)category, recipe);
        }
    }
}
