package dev.graphassembly.compat.jei;

import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import dev.graphassembly.client.view.StepPresentation;
import java.util.Optional;
import java.util.List;
import dev.graphassembly.client.view.SlotFrame;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;

/** Owns one layout per visible node; assembly animation instances remain node-local. */
public final class NativeStepPreview {
    public record Step(ProcessingRecipe<?, ?> recipe, SequencedAssemblySubCategory assembly, int index) {}
    private final IRecipeLayoutDrawable<?> layout;
    private final List<IRecipeSlotView> definitions;
    private SlotFrame<IRecipeSlotView, ITypedIngredient<?>> frame = new SlotFrame<>(List.of());
    private NativeSlotObservation.Tooltip nativeTooltip;
    private double tooltipX = Double.NaN, tooltipY = Double.NaN;
    private NativeStepPreview(IRecipeLayoutDrawable<?> layout) {
        this.layout = layout;
        this.definitions = List.copyOf(layout.getRecipeSlotsView().getSlotViews());
    }
    public IRecipeLayoutDrawable<?> layout() { return layout; }
    public List<IRecipeSlotView> definitions() { return definitions; }
    public SlotFrame<IRecipeSlotView, ITypedIngredient<?>> frame() { return frame; }
    public void invalidateFrame() { frame = new SlotFrame<>(List.of()); nativeTooltip = null; }
    public IRecipeSlotDrawable slotAt(double x, double y) {
        var hit = frame.hit(x, y);
        return hit != null && hit.key() instanceof IRecipeSlotDrawable slot ? slot : null;
    }
    public void draw(GuiGraphics graphics, int x, int y) {
        invalidateFrame();
        layout.setPosition(0, 0);
        var observation = new NativeSlotObservation(graphics, definitions);
        try (var scope = NativeSlotObservation.DRAW.open(observation)) {
            layout.drawRecipe(graphics, x, y);
        }
        frame = observation.finish();
        tooltipX = x; tooltipY = y;
        // The native path includes decorators and extras. Collect only the tooltip: the parent
        // renders its rich lines later in screen space, never inside the node's scaled pose.
        if (x >= 0 && y >= 0 && slotAt(x, y) == null && layout.getSlotUnderMouse(x, y).isEmpty()) {
            try (var scope = NativeSlotObservation.TOOLTIP.open(value -> nativeTooltip = value)) {
                layout.drawOverlays(graphics, x, y);
            }
        }
    }
    public Component title() { return layout.getRecipeCategory().getTitle(); }
    public void getTooltip(ITooltipBuilder tooltip, double localX, double localY) {
        if (nativeTooltip != null && (int)localX == tooltipX && (int)localY == tooltipY) nativeTooltip.append(tooltip);
    }
    public static Optional<NativeStepPreview> create(IJeiRuntime runtime, IGuiHelper gui,
            ProcessingRecipe<?, ?> recipe, int index, IFocusGroup focuses) {
        return create(runtime, gui, (Recipe<?>)recipe, index, focuses);
    }
    public static Optional<NativeStepPreview> create(IJeiRuntime runtime, IGuiHelper gui,
            Recipe<?> recipe, int index, IFocusGroup focuses) {
        return create(runtime, gui, new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("graph_assembly", "preview/" + index), recipe), index, focuses);
    }
    public static Optional<NativeStepPreview> create(IJeiRuntime runtime, IGuiHelper gui,
            RecipeHolder<?> holder, int index, IFocusGroup focuses) {
        return create(runtime, gui, holder, index, focuses, false);
    }
    public static Optional<NativeStepPreview> create(IJeiRuntime runtime, IGuiHelper gui,
            RecipeHolder<?> holder, int index, IFocusGroup focuses, boolean completeRecipe) {
        var recipe = holder.value();
        var serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        var route = StepPresentation.previewRoute(serializer == null ? "" : serializer.toString(), completeRecipe);
        if (route == StepPresentation.PreviewRoute.NATIVE_CATEGORY) {
            var nativeCategory = NativeRecipeCategoryPreview.create(runtime, holder, focuses);
            // A complete native reference must never silently lose its primary input/output to a sequence thumbnail.
            if (nativeCategory.isPresent() || completeRecipe) return nativeCategory.map(NativeStepPreview::new);
        }
        if (!(recipe instanceof ProcessingRecipe<?, ?> processing)) return Optional.empty();
        SequencedAssemblySubCategory category;
        if (processing instanceof MillingRecipe) category = null;
        else if (processing instanceof IAssemblyRecipe assembly && assembly.supportsAssembly())
            category = java.util.Objects.requireNonNull(assembly.getJEISubCategory().get().get());
        else return Optional.empty();
        var step = new Step(processing, category, index);
        return runtime.getRecipeManager().createRecipeLayoutDrawable(new NativeStepCategory(gui, step), step, focuses,
            (graphics, x, y, width, height) -> {}, 0).map(NativeStepPreview::new);
    }
}
