package dev.graphassembly.diagnostics;

import dev.graphassembly.recipe.ScopedParseCache;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/** Opt-in, per-thread observations of completed work. Never shares render and server counters. */
public final class PerformanceMetrics {
    private static volatile boolean enabled = Boolean.getBoolean("graph_assembly.performanceMetrics");
    public static boolean enabled() { return enabled; }
    static void refreshProperty() { enabled = Boolean.getBoolean("graph_assembly.performanceMetrics"); }
    private static final ThreadLocal<Counters> LOCAL = ThreadLocal.withInitial(Counters::new);
    private static final ThreadMXBean CPU = ManagementFactory.getThreadMXBean();

    private PerformanceMetrics() {}
    private static final class Counters {
        long canvasWallNanos, canvasCpuNanos, rectangleSubmissions, meshSubmissions, vertices;
        long geometryRebuilds, geometryWallNanos, geometryCpuNanos, previewAttempts, previewSuccesses, cacheLookups, cacheHits;
        long workpieceParses, filterParses, nativeRecipeDecodes;
        long edgeVisits, rawEntryCandidates, stepCandidates, indexHolderComparisons, indexBuilds;
        long previewEntries, meshRetainedBytes, meshCacheHits, meshCacheRebuilds;
        long progressCacheLookups, progressCacheHits, progressCacheEntries, progressCacheAccountedBytes, progressCacheEvictions, progressCacheBypasses;
        long filterCacheLookups, filterCacheHits, filterCacheEntries, filterCacheAccountedBytes, filterCacheEvictions, filterCacheBypasses;
    }
    public record Snapshot(long canvasWallNanos, long canvasCpuNanos,
            long rectangleSubmissions, long meshSubmissions, long vertices,
            long geometryRebuilds, long geometryWallNanos, long geometryCpuNanos, long previewAttempts, long previewSuccesses,
            long cacheLookups, long cacheHits, long previewEntries, long meshRetainedBytes,
            long meshCacheHits, long meshCacheRebuilds,
            long workpieceParses, long filterParses, long nativeRecipeDecodes,
            long edgeVisits, long rawEntryCandidates, long stepCandidates, long indexHolderComparisons, long indexBuilds,
            long progressCacheLookups, long progressCacheHits, long progressCacheEntries, long progressCacheAccountedBytes, long progressCacheEvictions, long progressCacheBypasses,
            long filterCacheLookups, long filterCacheHits, long filterCacheEntries, long filterCacheAccountedBytes, long filterCacheEvictions, long filterCacheBypasses) {
        public Snapshot delta(Snapshot before) {
            return new Snapshot(canvasWallNanos-before.canvasWallNanos, canvasCpuNanos-before.canvasCpuNanos,
                rectangleSubmissions-before.rectangleSubmissions, meshSubmissions-before.meshSubmissions, vertices-before.vertices,
                geometryRebuilds-before.geometryRebuilds, geometryWallNanos-before.geometryWallNanos, geometryCpuNanos-before.geometryCpuNanos,
                previewAttempts-before.previewAttempts, previewSuccesses-before.previewSuccesses,
                cacheLookups-before.cacheLookups, cacheHits-before.cacheHits, previewEntries, meshRetainedBytes,
                meshCacheHits-before.meshCacheHits, meshCacheRebuilds-before.meshCacheRebuilds,
                workpieceParses-before.workpieceParses, filterParses-before.filterParses, nativeRecipeDecodes-before.nativeRecipeDecodes,
                edgeVisits-before.edgeVisits, rawEntryCandidates-before.rawEntryCandidates, stepCandidates-before.stepCandidates,
                indexHolderComparisons-before.indexHolderComparisons, indexBuilds-before.indexBuilds,
                progressCacheLookups-before.progressCacheLookups, progressCacheHits-before.progressCacheHits, progressCacheEntries, progressCacheAccountedBytes,
                progressCacheEvictions-before.progressCacheEvictions, progressCacheBypasses-before.progressCacheBypasses,
                filterCacheLookups-before.filterCacheLookups, filterCacheHits-before.filterCacheHits, filterCacheEntries, filterCacheAccountedBytes,
                filterCacheEvictions-before.filterCacheEvictions, filterCacheBypasses-before.filterCacheBypasses);
        }
    }
    public static Snapshot snapshot() {
        var c = LOCAL.get();
        return new Snapshot(c.canvasWallNanos,c.canvasCpuNanos,c.rectangleSubmissions,c.meshSubmissions,c.vertices,
            c.geometryRebuilds,c.geometryWallNanos,c.geometryCpuNanos,c.previewAttempts,c.previewSuccesses,c.cacheLookups,c.cacheHits,c.previewEntries,
            c.meshRetainedBytes,c.meshCacheHits,c.meshCacheRebuilds,c.workpieceParses,c.filterParses,c.nativeRecipeDecodes,
            c.edgeVisits,c.rawEntryCandidates,c.stepCandidates,c.indexHolderComparisons,c.indexBuilds,
            c.progressCacheLookups,c.progressCacheHits,c.progressCacheEntries,c.progressCacheAccountedBytes,c.progressCacheEvictions,c.progressCacheBypasses,
            c.filterCacheLookups,c.filterCacheHits,c.filterCacheEntries,c.filterCacheAccountedBytes,c.filterCacheEvictions,c.filterCacheBypasses);
    }
    public static void reset() { if (enabled()) LOCAL.remove(); }
    public static long cpuTime() { return enabled() && CPU.isCurrentThreadCpuTimeSupported() ? CPU.getCurrentThreadCpuTime() : 0; }
    public static void canvas(long wallNanos,long cpuNanos,long rectangles,long meshBatches,long vertices) {
        if (!enabled()) return;
        var c=LOCAL.get(); c.canvasWallNanos+=wallNanos; c.canvasCpuNanos+=cpuNanos;
        c.rectangleSubmissions+=rectangles; c.meshSubmissions+=meshBatches; c.vertices+=vertices;
    }
    public static void geometryRebuild() { if (enabled()) LOCAL.get().geometryRebuilds++; }
    public static void geometryTime(long wallNanos,long cpuNanos) {
        if (!enabled()) return;
        var c=LOCAL.get();c.geometryWallNanos+=wallNanos;c.geometryCpuNanos+=cpuNanos;
    }
    public static void previewAttempt() { if (enabled()) LOCAL.get().previewAttempts++; }
    public static void previewSuccess() { if (enabled()) LOCAL.get().previewSuccesses++; }
    public static void cacheLookup(boolean hit,long entries) {
        if (!enabled()) return;
        var c=LOCAL.get(); c.cacheLookups++; if (hit) c.cacheHits++; c.previewEntries=entries;
    }
    public static void previewEntries(long entries) { if (enabled()) LOCAL.get().previewEntries=entries; }
    /** Future P1 mesh cache gauge; measured allocated storage only, never object-heap estimates. */
    public static void meshRetainedBytes(long bytes) { if (enabled()) LOCAL.get().meshRetainedBytes=bytes; }
    public static void meshCache(boolean hit) {
        if (!enabled()) return;
        if (hit) LOCAL.get().meshCacheHits++; else LOCAL.get().meshCacheRebuilds++;
    }
    public static void workpieceParse() { if (enabled()) LOCAL.get().workpieceParses++; }
    public static void filterParse() { if (enabled()) LOCAL.get().filterParses++; }
    public static void nativeRecipeDecode() { if (enabled()) LOCAL.get().nativeRecipeDecodes++; }
    public static void edgeVisited(int count) { if (enabled()) LOCAL.get().edgeVisits += count; }
    public static void rawEntryCandidate() { if (enabled()) LOCAL.get().rawEntryCandidates++; }
    public static void stepCandidate() { if (enabled()) LOCAL.get().stepCandidates++; }
    public static void indexHolderComparison() { if (enabled()) LOCAL.get().indexHolderComparisons++; }
    public static void indexBuild() { if (enabled()) LOCAL.get().indexBuilds++; }
    /** Per-current-thread events and last-observed context gauges; bytes are conservative estimates, not measured heap. */
    public static void parseCacheLookup(ScopedParseCache.Kind kind, boolean hit, int entries, long accountedBytes, boolean evicted, boolean bypassed) {
        if (!enabled()) return;
        var c = LOCAL.get();
        if (kind == ScopedParseCache.Kind.PROGRESS) {
            c.progressCacheLookups++; if (hit) c.progressCacheHits++;
            if (evicted) c.progressCacheEvictions++; if (bypassed) c.progressCacheBypasses++;
            c.progressCacheEntries = entries; c.progressCacheAccountedBytes = accountedBytes;
        } else {
            c.filterCacheLookups++; if (hit) c.filterCacheHits++;
            if (evicted) c.filterCacheEvictions++; if (bypassed) c.filterCacheBypasses++;
            c.filterCacheEntries = entries; c.filterCacheAccountedBytes = accountedBytes;
        }
    }
}
