package dev.graphassembly.recipe;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongBiFunction;

/** Content-keyed, weighted LRU snapshots scoped by owner identity. Values must not retain owners. */
public final class ScopedParseCache<O, P, F> {
    public static final int MAX_CONTEXTS = 4, MAX_ENTRIES = 128;
    public static final long PROGRESS_BUDGET = 16L * 1024 * 1024, FILTER_BUDGET = 1024 * 1024;
    public enum Kind { PROGRESS, FILTER }
    /** Callbacks from independent buckets may run concurrently. */
    public interface Observer { void lookup(Kind kind, boolean hit, int entries, long accountedBytes, boolean evicted, boolean bypassed); }
    private record Entry<V>(V value, long weight) {}
    private static final class Bucket<V> {
        final LinkedHashMap<String, Entry<V>> values = new LinkedHashMap<>(16, .75f, true);
        long weight;
        synchronized V get(String key, Function<String,V> parser, ToLongBiFunction<String,V> estimate,
                  long budget, Kind kind, Observer observer) {
            var existing = values.get(key);
            if (existing != null) {
                observer.lookup(kind, true, values.size(), weight, false, false);
                return existing.value();
            }
            V parsed = parser.apply(key); // Failed parsing is never retained or reported as successful.
            long size = Math.max(0, estimate.applyAsLong(key, parsed));
            boolean bypass = size > budget;
            boolean evicted = false;
            if (!bypass) {
                while (!values.isEmpty() && (values.size() >= MAX_ENTRIES || weight > budget - size)) {
                    var oldest = values.entrySet().iterator().next();
                    weight -= oldest.getValue().weight(); values.remove(oldest.getKey()); evicted = true;
                }
                values.put(key, new Entry<>(parsed, size)); weight += size;
            }
            observer.lookup(kind, false, values.size(), weight, evicted, bypass);
            return parsed;
        }
    }
    private final class Context {
        final WeakReference<O> owner;
        final Bucket<P> progress = new Bucket<>();
        final Bucket<F> filters = new Bucket<>();
        Context(O owner) { this.owner = new WeakReference<>(owner); }
    }
    private final ArrayDeque<Context> contexts = new ArrayDeque<>();
    private final Observer observer;
    public ScopedParseCache(Observer observer) { this.observer = observer; }
    // The global monitor protects only the weak-owner LRU, never bucket work or bucket-lock waits.
    private synchronized Context context(O owner) {
        for (Iterator<Context> it = contexts.iterator(); it.hasNext();) {
            Context context = it.next(); O candidate = context.owner.get();
            if (candidate == null) it.remove();
            else if (candidate == owner) { it.remove(); contexts.addLast(context); return context; }
        }
        if (contexts.size() == MAX_CONTEXTS) contexts.removeFirst();
        var created = new Context(owner); contexts.addLast(created); return created;
    }
    public P progress(O owner, String key, Function<String,P> parser, ToLongBiFunction<String,P> estimate) {
        if (owner == null) throw new IllegalArgumentException("Missing cache owner");
        return context(owner).progress.get(key, parser, estimate, PROGRESS_BUDGET, Kind.PROGRESS, observer);
    }
    public F filter(O owner, String key, Function<String,F> parser, ToLongBiFunction<String,F> estimate) {
        if (owner == null) throw new IllegalArgumentException("Missing cache owner");
        return context(owner).filters.get(key, parser, estimate, FILTER_BUDGET, Kind.FILTER, observer);
    }
    // In-flight lookups may finish in detached contexts, but cannot reattach them after release/eviction.
    public synchronized void release(O owner) { contexts.removeIf(c -> c.owner.get() == null || c.owner.get() == owner); }
    public synchronized void clear() { contexts.clear(); }
    public synchronized int contextCount() { contexts.removeIf(c -> c.owner.get() == null); return contexts.size(); }
}
