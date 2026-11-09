package dev.graphassembly.core.condition.checkpoint;

import dev.graphassembly.core.progress.ProgressMutation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointAccountingTest {
    @Test void editsOwnTheirWorkingCopyAndSnapshotsCannotBeChangedByLaterEdits() {
        var input = new ArrayList<>(List.of("s", "a"));
        var accounting = new CheckpointAccounting(input);
        input.clear();
        var before = accounting.snapshot();
        accounting.record("s");
        var recorded = accounting.snapshot();
        accounting.clear(new ProgressMutation.ClearCheckpoint("s"));

        assertEquals(List.of("s", "a"), before);
        assertEquals(List.of("s", "a", "s"), recorded);
        assertEquals(List.of("a"), accounting.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> recorded.add("external"));
        var unchanged = new CheckpointAccounting(before);
        assertSame(before, unchanged.snapshot());
    }

    @Test void recordsDuplicatesClearsEveryOccurrenceAndPopsOnlyTheTail() {
        var records = new CheckpointAccounting(List.of("s", "a", "s"));
        records.record("a");
        assertEquals(List.of("s", "a", "s", "a"), records.snapshot());
        assertEquals(Map.of("s", 2, "a", 2), CheckpointAccounting.counts(records.snapshot()));
        records.clear(new ProgressMutation.ClearCheckpoint("s"));
        assertEquals(List.of("a", "a"), records.snapshot());
        records.pop();
        assertEquals(List.of("a"), records.snapshot());
        assertEquals(Map.of("a", 1), CheckpointAccounting.counts(records.snapshot()));
        records.pop();
        records.pop();
        records.clear(new ProgressMutation.ClearCheckpoint("missing"));
        assertTrue(records.snapshot().isEmpty());
    }

    @Test void countIndexIsAnImmutableSnapshotRatherThanLifetimeVisitCounts() {
        var records = new CheckpointAccounting(List.of("a", "a"));
        var before = CheckpointAccounting.counts(records.snapshot());
        records.clear(new ProgressMutation.ClearCheckpoint("a"));
        assertEquals(Map.of("a", 2), before);
        assertEquals(Map.of(), CheckpointAccounting.counts(records.snapshot()));
        assertThrows(UnsupportedOperationException.class, () -> before.put("a", 3));
    }
}
