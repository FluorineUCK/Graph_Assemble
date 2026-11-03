package dev.graphassembly.client.view;

import dev.graphassembly.diagnostics.PerformanceMetrics;
import java.util.*;
import java.util.function.*;

/** Per-widget lazy layouts; only visible expanded layouts advance. No renderer dependency. */
public final class PreviewLifecycle<K,V> {
    public static final int IDLE_TICKS = 200;
    private final Map<K,V> values = new HashMap<>();
    private final Map<K,Integer> idle = new HashMap<>();
    public V get(K key) { var value=values.get(key); PerformanceMetrics.cacheLookup(value!=null,values.size()); return value; }
    public V prepare(K key, boolean eligible, Supplier<V> factory) {
        if (!eligible) {
            var existing=values.get(key);
            PerformanceMetrics.cacheLookup(existing!=null,values.size());
            return existing;
        }
        var value = values.get(key);
        PerformanceMetrics.cacheLookup(value!=null,values.size());
        if (value == null) {
            value = factory.get();
            if (value != null) { values.put(key,value); PerformanceMetrics.previewEntries(values.size()); }
        }
        return value;
    }
    public Set<K> tick(Set<K> active, Consumer<V> tick) {
        var removed = new HashSet<K>();
        for (var entry : values.entrySet()) {
            if (active.contains(entry.getKey())) { idle.remove(entry.getKey()); tick.accept(entry.getValue()); }
            else if (idle.merge(entry.getKey(),1,Integer::sum) >= IDLE_TICKS) removed.add(entry.getKey());
        }
        removed.forEach(this::remove); return removed;
    }
    public void remove(K key) { values.remove(key); idle.remove(key); PerformanceMetrics.previewEntries(values.size()); }
    public void clear() { values.clear(); idle.clear(); PerformanceMetrics.previewEntries(0); }
    public static boolean shouldPrepare(double scale, boolean front, boolean visible) {
        return front && visible && Double.isFinite(scale) && scale >= NodePresentation.COLLAPSE_SCALE;
    }
}
