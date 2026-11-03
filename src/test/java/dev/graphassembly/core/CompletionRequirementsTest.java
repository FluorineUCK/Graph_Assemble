package dev.graphassembly.core;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompletionRequirementsTest {
    @Test
    void explicitEmptyIsNotDisabledAndEnabledConditionsAreCombinedWithAnd() {
        var marks = Map.of("x", 3);
        var history = List.of("s", "f");
        assertFalse(new CompletionRequirements(new MarkRequirement(MarkRequirement.Mode.EXACT, Map.of()), null).matches(marks, history));
        assertTrue(new CompletionRequirements(new MarkRequirement(MarkRequirement.Mode.PARTIAL, Map.of()), null).matches(marks, history));
        assertFalse(new CompletionRequirements(null, new CheckpointRequirement(CheckpointMode.EXACT, List.of())).matches(marks, history));
        assertTrue(new CompletionRequirements(null, new CheckpointRequirement(CheckpointMode.SUBSEQUENCE, List.of())).matches(marks, history));
        var both = new CompletionRequirements(new MarkRequirement(MarkRequirement.Mode.EXACT, marks),
            new CheckpointRequirement(CheckpointMode.EXACT, history));
        assertTrue(both.matches(marks, history));
        assertFalse(both.matches(Map.of(), history));
        assertFalse(both.matches(marks, List.of()));
    }

    @Test
    void conditionsCopyInputsAndMatchingDoesNotConsumeRecords() {
        var marks = new java.util.HashMap<>(Map.of("x", 1));
        var history = new java.util.ArrayList<>(List.of("s"));
        var both = new CompletionRequirements(new MarkRequirement(MarkRequirement.Mode.PARTIAL, marks),
            new CheckpointRequirement(CheckpointMode.EXACT, history));
        marks.clear(); history.clear();
        var actual = new java.util.HashMap<>(Map.of("x", 2));
        assertTrue(both.matches(actual, List.of("s")));
        assertEquals(Map.of("x", 2), actual);
        assertThrows(UnsupportedOperationException.class, () -> both.marks().values().clear());
        assertThrows(UnsupportedOperationException.class, () -> both.checkpoints().nodes().clear());
    }
    @Test
    void checkpointsPreserveOrderDuplicatesAndLegacyUnorderedContainsAll() {
        var nodes = List.of("s", "a", "a", "f");
        var exact = new CompletionRequirements(null, new CheckpointRequirement(CheckpointMode.EXACT, nodes));
        var ordered = new CompletionRequirements(null, new CheckpointRequirement(CheckpointMode.SUBSEQUENCE, nodes));
        var unordered = new CompletionRequirements(null, new CheckpointRequirement(CheckpointMode.UNORDERED, nodes));
        assertTrue(exact.matches(Map.of("ignored", 2), nodes));
        assertFalse(exact.matches(Map.of(), List.of("s", "a", "x", "a", "f")));
        assertTrue(ordered.matches(Map.of(), List.of("s", "a", "x", "a", "f")));
        assertFalse(ordered.matches(Map.of(), List.of("s", "a", "f")));
        assertFalse(ordered.matches(Map.of(), List.of("f", "a", "a", "s")));
        assertTrue(unordered.matches(Map.of(), List.of("f", "a", "s")));
        assertFalse(unordered.matches(Map.of(), List.of("f", "s")));
    }
    @Test
    void exactMarksRequireEqualKeysAndCountsWhilePartialRequiresOnlyMinimums() {
        var exact = new CompletionRequirements(new MarkRequirement(MarkRequirement.Mode.EXACT, Map.of("x", 3)), null);
        var partial = new CompletionRequirements(new MarkRequirement(MarkRequirement.Mode.PARTIAL, Map.of("x", 3)), null);
        assertTrue(exact.matches(Map.of("x", 3), List.of("ignored")));
        for (var actual : List.of(Map.of("x", 4), Map.of("x", 3, "extra", 1), Map.of("x", 2), Map.<String,Integer>of()))
            assertFalse(exact.matches(actual, List.of()));
        assertTrue(partial.matches(Map.of("x", 4, "extra", 9), List.of("ignored")));
        assertFalse(partial.matches(Map.of("x", 2), List.of()));
        assertFalse(partial.matches(Map.of(), List.of()));
    }
    @Test
    void disabledConditionsAcceptAnyRecordedMarksAndHistory() {
        assertTrue(new CompletionRequirements(null, null).matches(Map.of("extra", 9), List.of("s", "f")));
    }
}
