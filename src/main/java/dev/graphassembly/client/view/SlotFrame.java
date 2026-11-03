package dev.graphassembly.client.view;

import java.util.*;
import dev.graphassembly.client.view.NodePresentation.Bounds;

/** Immutable successful draw frame. Definitions without a draw never acquire a hit area. */
public record SlotFrame<K,V>(List<Entry<K,V>> entries) {
    public SlotFrame { entries = List.copyOf(entries); }
    public record Entry<K,V>(K key, V value, Bounds bounds, Bounds visible) {}
    public Entry<K,V> hit(double x, double y) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            var e = entries.get(i); var b = e.visible();
            if (b != null && x >= b.left() && y >= b.top() && x < b.right() && y < b.bottom()) return e;
        }
        return null;
    }
    public static final class Builder<K,V> {
        private final Set<K> members = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<Entry<K,V>> entries = new ArrayList<>();
        public Builder(Collection<? extends K> definitions) { members.addAll(definitions); }
        public void add(K key, V value, Bounds bounds, Bounds clip) {
            if (!members.contains(key)) return;
            Bounds visible = bounds;
            if (clip != null) {
                double l = Math.max(bounds.left(), clip.left()), t = Math.max(bounds.top(), clip.top());
                double r = Math.min(bounds.right(), clip.right()), b = Math.min(bounds.bottom(), clip.bottom());
                visible = r > l && b > t ? new Bounds(l, t, r, b) : null;
            }
            entries.add(new Entry<>(key, value, bounds, visible));
        }
        public SlotFrame<K,V> build() { return new SlotFrame<>(entries); }
    }
}
