package dev.graphassembly.recipe;

import com.google.gson.JsonParser;
import dev.graphassembly.core.Progress;
import dev.graphassembly.diagnostics.PerformanceMetrics;
import dev.graphassembly.filter.FilterConfiguration;
import dev.graphassembly.filter.FilterSelection;
import net.minecraft.world.item.crafting.RecipeManager;

/** Parsed data only: no graph/index/ingredient/match outcomes are retained. */
public final class RuntimeSnapshots {
    private RuntimeSnapshots() {}
    private static final ScopedParseCache<RecipeManager, Progress, FilterSelection> CACHE =
        new ScopedParseCache<>(PerformanceMetrics::parseCacheLookup);
    public static Progress progress(RecipeManager owner, String contents) {
        return CACHE.progress(owner, contents, RuntimeSnapshots::parseProgress, RuntimeSnapshots::progressWeight);
    }
    public static FilterSelection filter(RecipeManager owner, String contents) {
        return CACHE.filter(owner, contents, RuntimeSnapshots::parseFilter, RuntimeSnapshots::filterWeight);
    }
    public static void release(RecipeManager owner) { CACHE.release(owner); }
    private static Progress parseProgress(String json) {
        PerformanceMetrics.workpieceParse();
        return Progress.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    private static FilterSelection parseFilter(String json) {
        return FilterSelection.fromConfiguration(FilterConfiguration.parse(json));
    }
    private static long string(String value) { return value == null ? 0 : 48L + 2L * value.length(); }
    private static long progressWeight(String key, Progress p) {
        long bytes = 256 + string(key) + string(p.graphId()) + string(p.entry()) + string(p.current()) + string(p.lockedNext());
        if (p.pendingRoute()!=null) bytes += 32 + string(p.pendingRoute().node()) + string(p.pendingRoute().from());
        for (var id : p.checkpoints()) bytes += 24 + string(id);
        for (var id : p.history()) bytes += 24 + string(id);
        for (var mark : p.marks().entrySet()) bytes += 96 + string(mark.getKey());
        for (var reward : p.rewardCounts().entrySet()) bytes += 96 + string(reward.getKey());
        return bytes;
    }
    private static long filterWeight(String key, FilterSelection f) {
        long bytes = 192 + string(key) + string(f.graph());
        for (var id : f.targets()) bytes += 24 + string(id);
        for (var id : f.inverted()) bytes += 32 + string(id);
        return bytes;
    }
}
