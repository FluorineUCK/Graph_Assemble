package dev.graphassembly.integration.stonecutting;

import dev.graphassembly.integration.GraphOperation;
import dev.graphassembly.integration.menu.MenuOutputPreflight;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.function.*;

/** Compatibility entrypoint; shared menu preflight owns the existing algorithm. */
final class StonecuttingOutputs {
    private StonecuttingOutputs() {}
    static boolean fits(Level level,GraphOperation operation,Consumer<ItemStack> prepare,ToIntFunction<ItemStack> limit,Predicate<List<ItemStack>> fits) {
        return MenuOutputPreflight.fits(level,operation,prepare,limit,fits);
    }
}
