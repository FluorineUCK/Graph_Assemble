package dev.graphassembly.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceMetricsTest {
    @AfterEach void restoreProperty() {
        System.clearProperty("graph_assembly.performanceMetrics");
        PerformanceMetrics.refreshProperty();
        // The next enabled test starts from its own reset; no state escapes the test thread.
    }
    @Test void snapshotIsIsolatedToTheCallingThread() throws Exception {
        System.setProperty("graph_assembly.performanceMetrics", "true");
        PerformanceMetrics.refreshProperty();
        PerformanceMetrics.reset();
        PerformanceMetrics.workpieceParse();
        var main = PerformanceMetrics.snapshot();
        var other = new java.util.concurrent.atomic.AtomicReference<PerformanceMetrics.Snapshot>();
        var thread = new Thread(() -> {
            PerformanceMetrics.reset();
            PerformanceMetrics.workpieceParse();
            PerformanceMetrics.workpieceParse();
            other.set(PerformanceMetrics.snapshot());
        });
        thread.start(); thread.join();
        assertEquals(1, main.workpieceParses());
        assertEquals(2, other.get().workpieceParses());
        assertEquals(1, PerformanceMetrics.snapshot().workpieceParses());
    }

    @Test void deltaSubtractsEventsButReportsCurrentGauges() {
        System.setProperty("graph_assembly.performanceMetrics", "true");
        PerformanceMetrics.refreshProperty();
        PerformanceMetrics.reset();
        PerformanceMetrics.cacheLookup(false, 1);
        var before=PerformanceMetrics.snapshot();
        PerformanceMetrics.cacheLookup(true, 2);
        PerformanceMetrics.meshRetainedBytes(256);
        PerformanceMetrics.meshCache(true);
        PerformanceMetrics.meshCache(false);
        var delta=PerformanceMetrics.snapshot().delta(before);
        assertEquals(1, delta.cacheLookups());
        assertEquals(1, delta.cacheHits());
        assertEquals(2, delta.previewEntries());
        assertEquals(256, delta.meshRetainedBytes());
        assertEquals(1, delta.meshCacheHits());
        assertEquals(1, delta.meshCacheRebuilds());
    }

    @Test void resetClearsCurrentThreadAndDisabledCallsAreNoOps() {
        System.setProperty("graph_assembly.performanceMetrics", "true");
        PerformanceMetrics.refreshProperty();
        PerformanceMetrics.reset();
        PerformanceMetrics.workpieceParse();
        assertEquals(1, PerformanceMetrics.snapshot().workpieceParses());
        PerformanceMetrics.reset();
        var empty=PerformanceMetrics.snapshot();
        assertEquals(0, empty.workpieceParses());
        System.setProperty("graph_assembly.performanceMetrics", "false");
        PerformanceMetrics.refreshProperty();
        PerformanceMetrics.workpieceParse();
        PerformanceMetrics.cacheLookup(true, 9);
        PerformanceMetrics.canvas(10, 10, 1, 1, 4);
        assertEquals(empty, PerformanceMetrics.snapshot());
    }

    @Test void oversizedFilterInputNeverStartsJsonParse() {
        System.setProperty("graph_assembly.performanceMetrics", "true");
        PerformanceMetrics.refreshProperty();
        PerformanceMetrics.reset();
        assertThrows(dev.graphassembly.filter.FilterConfiguration.Invalid.class,
            () -> dev.graphassembly.filter.FilterConfiguration.parse(" ".repeat(16385)));
        assertEquals(0, PerformanceMetrics.snapshot().filterParses());
        dev.graphassembly.filter.FilterConfiguration.parse("{\"graph\":\"\",\"targets\":[]}");
        assertEquals(1, PerformanceMetrics.snapshot().filterParses());
    }

}
