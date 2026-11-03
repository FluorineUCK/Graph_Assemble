package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PreviewLifecycleTest {
    @Test void thousandCollapsedNodesNeverConstructOrTickLayouts() {
        var pool = new PreviewLifecycle<String,Object>(); var created = new AtomicInteger(); var ticks = new AtomicInteger();
        for(int i=0;i<1000;i++) assertNull(pool.prepare("n"+i,false,()->{created.incrementAndGet();return new Object();}));
        pool.tick(Set.of(), p->ticks.incrementAndGet());
        assertEquals(0,created.get()); assertEquals(0,ticks.get());
    }
    @Test void onlyVisibleExpandedLayoutsTickAndInactiveOnesExpire() {
        var pool = new PreviewLifecycle<String,AtomicInteger>(); var creates = new AtomicInteger();
        var a = pool.prepare("a",true,()->{creates.incrementAndGet();return new AtomicInteger();});
        var b = pool.prepare("b",true,AtomicInteger::new);
        assertSame(a,pool.prepare("a",true,()->{fail("Cached preview must be reused");return null;}));
        for(int i=0;i<PreviewLifecycle.IDLE_TICKS;i++) pool.tick(Set.of("a"),AtomicInteger::incrementAndGet);
        assertEquals(PreviewLifecycle.IDLE_TICKS,a.get()); assertEquals(0,b.get());
        assertNull(pool.get("b")); assertSame(a,pool.get("a"));
        pool.clear(); assertNull(pool.get("a")); assertEquals(1,creates.get());
    }
    @Test void backsOffscreenAndSmallDotsDoNotRequestLayouts() {
        assertFalse(PreviewLifecycle.shouldPrepare(1,false,true));
        assertFalse(PreviewLifecycle.shouldPrepare(2,false,true));
        assertFalse(PreviewLifecycle.shouldPrepare(2,true,false));
        assertFalse(PreviewLifecycle.shouldPrepare(1,true,true));
        assertTrue(PreviewLifecycle.shouldPrepare(1.12,true,true));
        assertFalse(PreviewLifecycle.shouldPrepare(Double.NaN,true,true));
    }
}
