package dev.graphassembly.core.progress;

import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.route.RouteState;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProgressTest {
    private static Progress snapshot() {
        return new Progress("test:graph", "s", "a", new MarkAccounting.State(Map.of("worked", 2), Map.of("a", 2)), List.of("s", "a"), new RouteState("f", null), List.of("s", "a"));
    }

    @Test void replacingRoutingPreservesCompletedFactsAndTheOriginalSnapshot() {
        var before = snapshot();
        var route = new RouteState(null, new dev.graphassembly.core.route.PendingRoute("next", null));
        var routed = before.withRouting(route);
        assertEquals(route, routed.routing());
        assertEquals("f", before.routing().lockedNext());
        assertNull(before.routing().pendingRoute());
        assertEquals(before, routed.withRouting(before.routing()));
        assertSame(before.marks(), routed.marks());
        assertSame(before.checkpoints(), routed.checkpoints());
        assertSame(before.recentHistory(), routed.recentHistory());
    }

}
