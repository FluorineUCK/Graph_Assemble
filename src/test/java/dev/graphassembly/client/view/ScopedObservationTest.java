package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScopedObservationTest {
    @Test void nestedFailureRestoresParentAndLeavesNoGlobalOwner() {
        var scopes = new ScopedObservation<Object>();
        var outer = new Object();
        assertNull(scopes.current());
        try (var first = scopes.open(outer)) {
            assertSame(outer, scopes.current());
            assertThrows(IllegalStateException.class, () -> {
                try (var second = scopes.open(new Object())) {
                    assertNotSame(outer, scopes.current());
                    throw new IllegalStateException("native draw failed");
                }
            });
            assertSame(outer, scopes.current());
        }
        assertNull(scopes.current());
    }
}
