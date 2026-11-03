package dev.graphassembly.recipe;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.Progress;
import dev.graphassembly.diagnostics.PerformanceMetrics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class Workpiece {
    private Workpiece() {}
    public static boolean has(ItemStack stack) { return stack.has(GraphAssembly.PROGRESS.get()); }
    public static Progress read(ItemStack stack) {
        String json = stack.get(GraphAssembly.PROGRESS.get());
        if (json == null) return null;
        PerformanceMetrics.workpieceParse();
        return Progress.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    /** Level-scoped snapshots parse current progress; without a level, read the current format without caching. */
    public static Progress read(Level level, ItemStack stack) {
        if (level == null) return read(stack);
        String json = stack.get(GraphAssembly.PROGRESS.get());
        return json == null ? null : RuntimeSnapshots.progress(level.getRecipeManager(), json);
    }
    public static ItemStack write(ItemStack stack, Progress progress) {
        ItemStack result = stack.copyWithCount(1);
        result.set(DataComponents.MAX_STACK_SIZE, 1);
        result.set(GraphAssembly.PROGRESS.get(), progress.toJsonString());
        return result;
    }
}
