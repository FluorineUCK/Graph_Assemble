package dev.graphassembly.client.view;

import java.util.function.Supplier;
import java.util.Map;
import java.util.WeakHashMap;

/** View-only data, independent of layout size, native previews and drag gestures. */
public final class RecipeViewStateCache<K, V> {
    // Values must not retain their recipe key; unloaded recipe definitions can then be collected.
    private final Map<K, V> states = new WeakHashMap<>();
    public V get(K recipe, Supplier<V> factory) { return states.computeIfAbsent(recipe, key -> factory.get()); }
    public void clear() { states.clear(); }
}
