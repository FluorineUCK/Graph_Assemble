package dev.graphassembly.compat.jei;

import com.simibubi.create.compat.jei.category.animations.AnimatedMillstone;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import dev.graphassembly.client.StepDescription;
import dev.graphassembly.client.view.NativePreviewGeometry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Internal layout only: not registered as a JEI recipe category. */
public final class NativeStepCategory implements IRecipeCategory<NativeStepPreview.Step> {
    private static final RecipeType<NativeStepPreview.Step> TYPE = RecipeType.create("graph_assembly", "native_preview", NativeStepPreview.Step.class);
    private static final int PADDING = NativePreviewGeometry.STEP_PADDING;
    private final NativeStepPreview.Step step;
    private final IDrawable background;
    private final AnimatedMillstone mill;
    public NativeStepCategory(IGuiHelper gui, NativeStepPreview.Step step) {
        this.step = step;
        this.background = gui.createBlankDrawable(getWidth(), getHeight());
        this.mill = step.assembly() == null ? new AnimatedMillstone() : null;
    }
    @Override public RecipeType<NativeStepPreview.Step> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return StepDescription.describe(step.recipe()); }
    @Override public int getWidth() { return step.assembly() == null ? NativePreviewGeometry.MILLING_WIDTH : step.assembly().getWidth() + PADDING * 2; }
    @Override public int getHeight() { return 84; }
    @Override public IDrawable getBackground() { return background; }
    @Override public IDrawable getIcon() { return null; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, NativeStepPreview.Step step, IFocusGroup focuses) {
        if (step.assembly() != null) step.assembly().setRecipe(builder, new SequencedRecipe<>(step.recipe()), focuses, PADDING);
    }
    @Override public void draw(NativeStepPreview.Step step, IRecipeSlotsView slots, GuiGraphics g, double mx, double my) {
        g.pose().pushPose();
        try {
            if (mill != null) {
                var transform = NativePreviewGeometry.milling(getWidth());
                g.pose().translate(transform.x(), transform.y(), 0);
                g.pose().scale((float)transform.scale(), (float)transform.scale(), (float)transform.scale());
                mill.draw(g, 0, 0);
            }
            else {
                g.pose().translate(PADDING, 0, 0);
                step.assembly().draw(new SequencedRecipe<>(step.recipe()), g, mx - PADDING, my, step.index());
            }
        } finally { g.pose().popPose(); }
    }
}
