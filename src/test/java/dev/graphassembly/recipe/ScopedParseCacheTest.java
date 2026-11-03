package dev.graphassembly.recipe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.ToLongBiFunction;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScopedParseCacheTest {
    private static final ScopedParseCache.Observer NOOP = (kind, hit, entries, bytes, evicted, bypassed) -> {};
    private record Observation(ScopedParseCache.Kind kind, boolean hit, int entries, long bytes, boolean evicted, boolean bypassed) {}
    @Test void coldProgressParseDoesNotBlockAnotherOwnersWarmRead() throws Exception {
        blockedWorkDoesNotBlockWarmReads(ScopedParseCache.Kind.PROGRESS, false, false);
    }
    @Test void coldFilterParseDoesNotBlockAnotherOwnersWarmRead() throws Exception {
        blockedWorkDoesNotBlockWarmReads(ScopedParseCache.Kind.FILTER, false, false);
    }
    @ParameterizedTest @EnumSource(ScopedParseCache.Kind.class)
    void coldWeightEstimateDoesNotBlockAnotherOwnersWarmReads(ScopedParseCache.Kind kind) throws Exception {
        blockedWorkDoesNotBlockWarmReads(kind, true, false);
    }
    @ParameterizedTest @EnumSource(ScopedParseCache.Kind.class)
    void coldParseDoesNotBlockTheOtherBucketOfTheSameOwner(ScopedParseCache.Kind kind) throws Exception {
        blockedWorkDoesNotBlockWarmReads(kind, false, true);
    }
    private static void blockedWorkDoesNotBlockWarmReads(ScopedParseCache.Kind coldKind,
                                                        boolean blockEstimate, boolean sameOwner) throws Exception {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        var coldOwner = new Object(); var warmOwner = sameOwner ? coldOwner : new Object();
        var warm = new EnumMap<ScopedParseCache.Kind,String>(ScopedParseCache.Kind.class);
        for (var kind : ScopedParseCache.Kind.values()) {
            if (!sameOwner || kind != coldKind) warm.put(kind, lookup(cache, kind, warmOwner, "warm", String::new, (k,v) -> 1));
        }
        var working = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        Runnable pause = () -> { working.countDown(); awaitGate(resume); };
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<String> cold = workers.submit(() -> lookup(cache, coldKind, coldOwner, "cold", key -> {
                if (!blockEstimate) pause.run();
                return key;
            }, (k,v) -> { if (blockEstimate) pause.run(); return 1; }));
            try {
                assertTrue(working.await(5, TimeUnit.SECONDS), "Cold work must be running before the hot reads");
                Future<?> hot = workers.submit(() -> warm.forEach((kind, value) -> assertSame(value,
                    lookup(cache, kind, warmOwner, "warm", key -> fail("Warm read must not parse again"),
                        (k,v) -> fail("Warm read must not estimate again")))));
                hot.get(5, TimeUnit.SECONDS); // Must finish before releasing either parser or estimator.
            } finally {
                resume.countDown();
                assertEquals("cold", cold.get(5, TimeUnit.SECONDS));
            }
        }
    }
    private enum Discard { RELEASE, CLEAR, EVICT }
    @ParameterizedTest
    @CsvSource({"PROGRESS, RELEASE", "FILTER, RELEASE", "PROGRESS, CLEAR", "FILTER, CLEAR",
        "PROGRESS, EVICT", "FILTER, EVICT"})
    void discardingAnInFlightContextDoesNotBlockOrPublishItsOldResult(
            ScopedParseCache.Kind kind, Discard discard) throws Exception {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        var owner = new Object();
        var parsing = new CountDownLatch(1); var resume = new CountDownLatch(1);
        // Strong references keep this test about explicit eviction, not weak-owner collection.
        var otherOwners = new ArrayList<Object>();
        for (int i = 0; i < ScopedParseCache.MAX_CONTEXTS; i++) otherOwners.add(new Object());
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<String> old = workers.submit(() -> lookup(cache, kind, owner, "key", key -> {
                parsing.countDown(); awaitGate(resume); return "old";
            }, (k,v) -> 1));
            try {
                assertTrue(parsing.await(5, TimeUnit.SECONDS));
                Future<?> discarded = workers.submit(() -> {
                    switch (discard) {
                        case RELEASE -> cache.release(owner);
                        case CLEAR -> cache.clear();
                        case EVICT -> otherOwners.forEach(other -> lookup(cache, kind, other, "key", k -> "other", (k,v) -> 1));
                    }
                    assertEquals(discard == Discard.EVICT ? ScopedParseCache.MAX_CONTEXTS : 0, cache.contextCount());
                });
                discarded.get(5, TimeUnit.SECONDS);
                Future<String> fresh = workers.submit(() -> lookup(cache, kind, owner, "key", k -> "fresh", (k,v) -> 1));
                assertEquals("fresh", fresh.get(5, TimeUnit.SECONDS), "A replacement context must not wait for the detached parser");
            } finally {
                resume.countDown();
                assertEquals("old", old.get(5, TimeUnit.SECONDS), "Existing callers may finish with their detached snapshot");
            }
        }
        assertEquals("fresh", lookup(cache, kind, owner, "key", k -> fail("Fresh snapshot was replaced"),
            (k,v) -> fail("Fresh snapshot was re-estimated")));
        assertEquals(discard == Discard.EVICT ? ScopedParseCache.MAX_CONTEXTS : 1, cache.contextCount());
    }
    @ParameterizedTest @EnumSource(ScopedParseCache.Kind.class)
    void releasedColdCompletionDoesNotRecreateAContext(ScopedParseCache.Kind kind) throws Exception {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        var owner = new Object(); var parsing = new CountDownLatch(1); var resume = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<String> cold = workers.submit(() -> lookup(cache, kind, owner, "key", key -> {
                parsing.countDown(); awaitGate(resume); return "old";
            }, (k,v) -> 1));
            try {
                assertTrue(parsing.await(5, TimeUnit.SECONDS));
                workers.submit(() -> cache.release(owner)).get(5, TimeUnit.SECONDS);
            } finally {
                resume.countDown();
                assertEquals("old", cold.get(5, TimeUnit.SECONDS));
            }
        }
        assertEquals(0, cache.contextCount(), "Completing a detached lookup must not register its owner again");
        assertEquals("new", lookup(cache, kind, owner, "key", k -> "new", (k,v) -> 1));
    }
    @ParameterizedTest @EnumSource(ScopedParseCache.Kind.class)
    void concurrentSameBucketLookupsParseOnceAndKeepEvictionAccountingBounded(ScopedParseCache.Kind kind) throws Exception {
        var observations = new ConcurrentLinkedQueue<Observation>();
        var cache = new ScopedParseCache<Object,String,String>((k,hit,entries,bytes,evicted,bypassed) ->
            observations.add(new Observation(k,hit,entries,bytes,evicted,bypassed)));
        var owner = new Object(); var parses = new AtomicInteger(); var estimates = new AtomicInteger();
        var start = new CountDownLatch(1);
        long budget = kind == ScopedParseCache.Kind.PROGRESS ? ScopedParseCache.PROGRESS_BUDGET : ScopedParseCache.FILTER_BUDGET;
        try (var workers = Executors.newFixedThreadPool(4)) {
            var reads = new ArrayList<Future<String>>();
            for (int i = 0; i < 32; i++) reads.add(workers.submit(() -> {
                awaitGate(start);
                return lookup(cache, kind, owner, "same", k -> k + parses.incrementAndGet(),
                    (k,v) -> { estimates.incrementAndGet(); return 1; });
            }));
            start.countDown();
            String snapshot = reads.getFirst().get(5, TimeUnit.SECONDS);
            for (var read : reads) assertSame(snapshot, read.get(5, TimeUnit.SECONDS));
            assertEquals(1, parses.get()); assertEquals(1, estimates.get());
            assertEquals(31, observations.stream().filter(Observation::hit).count());

            var misses = new ArrayList<Future<String>>();
            for (int i = 0; i < 192; i++) {
                String key = "unique" + i;
                misses.add(workers.submit(() -> lookup(cache, kind, owner, key, String::new, (k,v) -> budget / 3)));
            }
            for (int i = 0; i < misses.size(); i++) assertEquals("unique" + i, misses.get(i).get(5, TimeUnit.SECONDS));
        } finally { start.countDown(); }
        assertTrue(observations.stream().anyMatch(Observation::evicted));
        assertTrue(observations.stream().allMatch(o -> o.kind() == kind && o.entries() <= ScopedParseCache.MAX_ENTRIES
            && o.bytes() >= 0 && o.bytes() <= budget));
        assertEquals(1, cache.contextCount());
    }
    @ParameterizedTest @EnumSource(ScopedParseCache.Kind.class)
    void aFailedConcurrentParseReleasesTheBucketForRetry(ScopedParseCache.Kind kind) throws Exception {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        var owner = new Object(); var parsing = new CountDownLatch(1); var resume = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<String> failed = workers.submit(() -> lookup(cache, kind, owner, "key", k -> {
                parsing.countDown(); awaitGate(resume); throw new IllegalArgumentException("broken JSON");
            }, (k,v) -> fail("Failed parsing must not be weighed")));
            Future<String> retry;
            try {
                assertTrue(parsing.await(5, TimeUnit.SECONDS));
                retry = workers.submit(() -> lookup(cache, kind, owner, "key", k -> "recovered", (k,v) -> 1));
            } finally { resume.countDown(); }
            var failure = assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertEquals("recovered", retry.get(5, TimeUnit.SECONDS));
        }
        assertEquals("recovered", lookup(cache, kind, owner, "key", k -> fail("Retry must be cached"), (k,v) -> 1));
    }
    private static String lookup(ScopedParseCache<Object,String,String> cache, ScopedParseCache.Kind kind,
                                 Object owner, String key, Function<String,String> parser, ToLongBiFunction<String,String> estimate) {
        return kind == ScopedParseCache.Kind.PROGRESS ? cache.progress(owner, key, parser, estimate)
            : cache.filter(owner, key, parser, estimate);
    }
    private static void awaitGate(CountDownLatch gate) {
        try { assertTrue(gate.await(20, TimeUnit.SECONDS), "Test must release the paused operation"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    @Test void contentIdentityAndOwnerIsolation() {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        var a = new Object(); var b = new Object(); var parses = new AtomicInteger();
        var parser = (java.util.function.Function<String,String>) s -> s + parses.incrementAndGet();
        var weight = (java.util.function.ToLongBiFunction<String,String>) (k,v) -> 10;
        assertSame(cache.progress(a, "alpha", parser, weight), cache.progress(a, new String("alpha"), parser, weight));
        assertEquals(1, parses.get());
        cache.progress(a, "beta", parser, weight);
        var retainedB = cache.progress(b, "alpha", parser, weight);
        assertEquals(3, parses.get());
        cache.release(a);
        assertSame(retainedB, cache.progress(b, "alpha", parser, weight), "Releasing one owner must preserve the other owner's snapshot");
        cache.progress(a, "alpha", parser, weight);
        assertEquals(4, parses.get());
    }
    @Test void countWeightAndOversizeAreBoundedWithoutChangingValues() {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        var owner = new Object(); var parses = new AtomicInteger();
        java.util.function.Function<String,String> parser = s -> s + ":" + parses.incrementAndGet();
        java.util.function.ToLongBiFunction<String,String> tiny = (k,v) -> 1;
        for (int i = 0; i < 129; i++) cache.progress(owner, "k" + i, parser, tiny);
        cache.progress(owner, "k0", parser, tiny);
        assertEquals(130, parses.get(), "Oldest of 129 entries must be evicted");
        cache.clear();
        java.util.function.ToLongBiFunction<String,String> half = (k,v) -> ScopedParseCache.PROGRESS_BUDGET / 2 + 1;
        cache.progress(owner, "a", parser, half);
        cache.progress(owner, "b", parser, half);
        cache.progress(owner, "a", parser, half);
        assertEquals(133, parses.get(), "Weight must evict even below the count limit");
        java.util.function.ToLongBiFunction<String,String> oversize = (k,v) -> ScopedParseCache.PROGRESS_BUDGET + 1;
        cache.progress(owner, "huge", parser, oversize);
        cache.progress(owner, "huge", parser, oversize);
        assertEquals(135, parses.get(), "An oversized value parses but is never retained");
    }
    @Test void hitPromotionAndWeightEstimationAreActualLruOperations() {
        var observations = new ArrayList<Observation>();
        var cache = new ScopedParseCache<Object,String,String>((kind,hit,entries,bytes,evicted,bypassed) ->
            observations.add(new Observation(kind,hit,entries,bytes,evicted,bypassed)));
        var owner = new Object(); var parses = new AtomicInteger(); var estimates = new AtomicInteger();
        java.util.function.Function<String,String> parser = key -> key + ":" + parses.incrementAndGet();
        java.util.function.ToLongBiFunction<String,String> estimate = (key,value) -> { estimates.incrementAndGet(); return 10; };
        for (int i=0;i<128;i++) cache.progress(owner,"key"+i,parser,estimate);
        var first = cache.progress(owner,"key0",parser,estimate);
        assertEquals(128, parses.get()); assertEquals(128, estimates.get(), "A hit must not rescan parsed data for weight");
        cache.progress(owner,"key128",parser,estimate);
        assertSame(first,cache.progress(owner,"key0",parser,estimate),"Recently used entry must survive insertion");
        assertEquals(129,parses.get());
        cache.progress(owner,"key1",parser,estimate);
        assertEquals(130,parses.get(),"Least-recently-used entry, not oldest inserted, must be evicted");
        assertEquals(130,estimates.get());
        assertTrue(observations.stream().anyMatch(o -> o.kind()==ScopedParseCache.Kind.PROGRESS && o.evicted()));
        assertTrue(observations.stream().allMatch(o -> o.entries()<=128 && o.bytes()<=ScopedParseCache.PROGRESS_BUDGET));
    }
    @Test void filterHasIndependentCountAndOneMiBBudget() {
        var observations = new ArrayList<Observation>();
        var cache = new ScopedParseCache<Object,String,String>((kind,hit,entries,bytes,evicted,bypassed) ->
            observations.add(new Observation(kind,hit,entries,bytes,evicted,bypassed)));
        var owner = new Object(); var parses = new AtomicInteger(); var estimates = new AtomicInteger();
        java.util.function.Function<String,String> parser = key -> key + ":" + parses.incrementAndGet();
        java.util.function.ToLongBiFunction<String,String> small = (key,value) -> { estimates.incrementAndGet(); return 1; };
        var progress = cache.progress(owner,"shared",parser,small);
        var filter = cache.filter(owner,"shared",parser,small);
        assertNotEquals(progress,filter,"Filter and Progress must not share a snapshot bucket");
        assertSame(progress,cache.progress(owner,"shared",parser,small));
        assertSame(filter,cache.filter(owner,"shared",parser,small));
        for (int i=0;i<128;i++) cache.filter(owner,"f"+i,parser,small);
        assertSame(progress,cache.progress(owner,"shared",parser,small),"Filter eviction must not evict Progress");
        cache.filter(owner,"shared",parser,small);
        assertEquals(131,parses.get(),"Filter count must evict its oldest entry at 128");
        cache.clear();
        java.util.function.ToLongBiFunction<String,String> half = (key,value) -> { estimates.incrementAndGet(); return ScopedParseCache.FILTER_BUDGET/2+1; };
        cache.filter(owner,"a",parser,half);cache.filter(owner,"b",parser,half);cache.filter(owner,"a",parser,half);
        assertEquals(134,parses.get(),"Filter must use 1 MiB budget, not 16 MiB Progress budget");
        java.util.function.ToLongBiFunction<String,String> huge = (key,value) -> { estimates.incrementAndGet(); return ScopedParseCache.FILTER_BUDGET+1; };
        cache.filter(owner,"too-large",parser,huge);cache.filter(owner,"too-large",parser,huge);
        assertEquals(136,parses.get(),"Oversized filter parses but is not retained");
        assertEquals(136,estimates.get(),"Estimator runs once per successful miss, never on hit");
        assertTrue(observations.stream().anyMatch(o -> o.kind()==ScopedParseCache.Kind.FILTER && o.evicted()));
        assertTrue(observations.stream().anyMatch(o -> o.kind()==ScopedParseCache.Kind.FILTER && o.bypassed()));
        assertTrue(observations.stream().filter(o -> o.kind()==ScopedParseCache.Kind.FILTER)
            .allMatch(o -> o.entries()<=128 && o.bytes()<=ScopedParseCache.FILTER_BUDGET));
    }
    @Test void concurrentOwnersAndFourContextCapDoNotMixResults() throws Exception {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        var owners = new Object[5]; for (int i=0;i<5;i++) owners[i]=new Object();
        var parses = new AtomicInteger();
        java.util.function.Function<String,String> parser = s -> s + parses.incrementAndGet();
        java.util.function.ToLongBiFunction<String,String> weight = (k,v) -> 1;
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<String> a = workers.submit(() -> { start.await(); return cache.progress(owners[0], "same", parser, weight); });
            Future<String> b = workers.submit(() -> { start.await(); return cache.progress(owners[1], "same", parser, weight); });
            start.countDown();
            var first = a.get(); var second = b.get();
            assertNotNull(first); assertNotNull(second); assertNotEquals(first, second);
            assertSame(first,cache.progress(owners[0],"same",parser,weight));
            assertSame(second,cache.progress(owners[1],"same",parser,weight));
            assertEquals(2,parses.get());
        }
        for (int i=2;i<5;i++) cache.progress(owners[i], "same", parser, weight);
        assertEquals(4, cache.contextCount());
    }
    @Test void concurrentOwnerWorkerExceptionCannotBeMistakenForSuccess() throws Exception {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<String> failed = workers.submit(() -> cache.progress(new Object(),"bad",key -> {throw new IllegalStateException("worker failure");},(k,v)->1));
            Future<String> good = workers.submit(() -> cache.progress(new Object(),"good",key -> key,(k,v)->1));
            assertEquals("good",good.get());
            assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,failed::get).getCause());
        }
    }
    @Test void malformedParseNeverRetainsPartialSnapshot() {
        var cache = new ScopedParseCache<Object,String,String>(NOOP);
        var count = new AtomicInteger(); var owner = new Object();
        java.util.function.Function<String,String> parser = s -> { count.incrementAndGet(); throw new IllegalArgumentException("bad"); };
        for (int i=0;i<2;i++) assertEquals("bad", assertThrows(IllegalArgumentException.class,
            () -> cache.filter(owner, "broken", parser, (k,v) -> 1)).getMessage());
        assertEquals(2, count.get());
    }
}
