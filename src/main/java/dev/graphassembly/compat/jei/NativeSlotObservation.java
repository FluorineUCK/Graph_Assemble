package dev.graphassembly.compat.jei;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.datafixers.util.Either;
import dev.graphassembly.client.view.*;
import dev.graphassembly.client.view.NodePresentation.Bounds;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.*;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** JEI-version bridge. No layout, graphics or slot survives in a static scope after draw returns. */
public final class NativeSlotObservation {
    static final ScopedObservation<NativeSlotObservation> DRAW = new ScopedObservation<>();
    static final ScopedObservation<Consumer<Tooltip>> TOOLTIP = new ScopedObservation<>();
    private final Matrix4f inverseRoot;
    private final SlotFrame.Builder<IRecipeSlotView, ITypedIngredient<?>> frame;

    NativeSlotObservation(GuiGraphics graphics, Collection<? extends IRecipeSlotView> definitions) {
        inverseRoot = new Matrix4f(graphics.pose().last().pose()).invert();
        frame = new SlotFrame.Builder<>(definitions);
    }
    SlotFrame<IRecipeSlotView, ITypedIngredient<?>> finish() { return frame.build(); }
    public static void drawn(IRecipeSlotDrawable slot, GuiGraphics graphics) {
        var owner = DRAW.current();
        if (owner == null) return;
        var relative = new Matrix4f(owner.inverseRoot).mul(graphics.pose().last().pose());
        var rect = slot.getRect();
        Bounds bounds = bounds(relative, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        if (bounds == null) return; // Never invent axis-aligned hits for unsupported transforms.
        Bounds clip = null;
        if (graphics instanceof GuiScissorAccess access && access.graphAssembly$effectiveScissor() != null) {
            var scissor = access.graphAssembly$effectiveScissor();
            clip = bounds(owner.inverseRoot, scissor.left(), scissor.top(), scissor.width(), scissor.height());
            if (clip == null) return;
        }
        owner.frame.add(slot, slot.getDisplayedIngredient().orElse(null), bounds, clip);
    }
    private static Bounds bounds(Matrix4fc matrix, double x, double y, double width, double height) {
        return SlotGeometry.transform(matrix, new Bounds(x, y, x + width, y + height));
    }
    public record Tooltip(List<Either<FormattedText, TooltipComponent>> lines, ITypedIngredient<?> ingredient) {
        public Tooltip { lines = List.copyOf(lines); }
        void append(ITooltipBuilder builder) {
            lines.forEach(line -> line.ifLeft(builder::add).ifRight(builder::add));
            if (ingredient != null) builder.setIngredient(ingredient);
        }
    }
    /** True only while our node asks JEI to collect its native widget/decorator tooltip. */
    public static boolean tooltip(ITooltipBuilder source, ITypedIngredient<?> ingredient) {
        var target = TOOLTIP.current();
        if (target == null) return false;
        target.accept(new Tooltip(source.getLines(), ingredient));
        return true;
    }
}
