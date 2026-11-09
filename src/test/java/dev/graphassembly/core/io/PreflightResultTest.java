package dev.graphassembly.core.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreflightResultTest {
    @Test void preservesOrderedProblemsAndOwnsItsSnapshot() {
        var allowed = new ArrayList<>(List.of("ready"));
        var blocked = new LinkedHashMap<String, PreflightResult.Problem>();
        var conditions = new PreflightResult.Problem(PreflightResult.BlockedReason.PRIMARY_CONDITIONS, "conditions");
        var capacity = new PreflightResult.Problem(PreflightResult.BlockedReason.MARK_CAPACITY, "capacity");
        blocked.put("z", conditions);
        blocked.put("a", capacity);
        var result = new PreflightResult(null, allowed, blocked);
        allowed.clear();
        blocked.clear();

        assertEquals(List.of("ready"), result.allowed());
        assertEquals(List.of("z", "a"), new ArrayList<>(result.blocked().keySet()));
        assertEquals(List.of(conditions, capacity), new ArrayList<>(result.blocked().values()));
        assertThrows(UnsupportedOperationException.class, () -> result.blocked().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.allowed().clear());
    }
}
