package dev.graphassembly.compat.jei;

import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import dev.graphassembly.integration.sequence.SequenceStepRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Target-only hint: the native category owns the surrounding sequence UI. */
public final class SequenceBlockHint extends SequencedAssemblySubCategory {
    public SequenceBlockHint() { super(25); }

    @Override public void draw(SequencedRecipe<?> recipe, GuiGraphics graphics, double mouseX, double mouseY, int index) {
        // Create caches one subcategory per serializer, so never retain the first recipe's machine.
        if (recipe.getRecipe() instanceof SequenceStepRecipe step)
            graphics.renderItem(new ItemStack(step.machine()), 4, 56);
    }
}
