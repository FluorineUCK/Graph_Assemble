package dev.graphassembly.core.condition;

import java.util.List;
import java.util.Map;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement.CheckpointMode;
import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement;
import dev.graphassembly.core.condition.mark.MarkRequirement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressRequirementsTest {
    @Test void rangeOnlyConditionsOwnTheirConstraintsWithoutEnablingEmptyExactMatches() {
        var ranges = new java.util.HashMap<>(Map.of("x", new IntRange(1, 2)));
        var counts = new java.util.HashMap<>(Map.of("s", new IntRange(2, 2)));
        var requirements = new ProgressRequirements(new MarkRequirement(null, ranges),
            new CheckpointRequirement(null, counts));
        ranges.clear(); counts.clear();
        assertNull(requirements.marks().match());
        assertNull(requirements.checkpoints().match());
        assertTrue(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("x", 2), List.of("s", "s"))));
        assertFalse(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("x", 3), List.of("s", "s"))));
        assertFalse(ProgressMatcher.matches(requirements, new ProgressFacts(Map.of("x", 2), List.of("s"))));
        assertThrows(UnsupportedOperationException.class, () -> requirements.marks().ranges().clear());
        assertThrows(UnsupportedOperationException.class, () -> requirements.checkpoints().counts().clear());
    }

    @Test
    void explicitEmptyIsNotDisabledAndEnabledConditionsAreCombinedWithAnd() {
        var marks = Map.of("x", 3);
        var history = List.of("s", "f");
        assertFalse(ProgressMatcher.matches(new ProgressRequirements(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.EXACT, Map.of()), Map.of()), CheckpointRequirement.NONE), new ProgressFacts(marks, history)));
        assertTrue(ProgressMatcher.matches(new ProgressRequirements(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.PARTIAL, Map.of()), Map.of()), CheckpointRequirement.NONE), new ProgressFacts(marks, history)));
        assertFalse(ProgressMatcher.matches(new ProgressRequirements(MarkRequirement.NONE, new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.EXACT, List.of()), Map.of())), new ProgressFacts(marks, history)));
        assertTrue(ProgressMatcher.matches(new ProgressRequirements(MarkRequirement.NONE, new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.SUBSEQUENCE, List.of()), Map.of())), new ProgressFacts(marks, history)));
        var both = new ProgressRequirements(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.EXACT, marks), Map.of()), new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.EXACT, history), Map.of()));
        assertTrue(ProgressMatcher.matches(both, new ProgressFacts(marks, history)));
        assertFalse(ProgressMatcher.matches(both, new ProgressFacts(Map.of(), history)));
        assertFalse(ProgressMatcher.matches(both, new ProgressFacts(marks, List.of())));
    }

    @Test
    void conditionsCopyInputsAndMatchingDoesNotConsumeRecords() {
        var marks = new java.util.HashMap<>(Map.of("x", 1));
        var history = new java.util.ArrayList<>(List.of("s"));
        var both = new ProgressRequirements(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.PARTIAL, marks), Map.of()), new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.EXACT, history), Map.of()));
        marks.clear(); history.clear();
        var actual = new java.util.HashMap<>(Map.of("x", 2));
        assertTrue(ProgressMatcher.matches(both, new ProgressFacts(actual, List.of("s"))));
        assertEquals(Map.of("x", 2), actual);
        assertThrows(UnsupportedOperationException.class, () -> both.marks().match().values().clear());
        assertThrows(UnsupportedOperationException.class, () -> both.checkpoints().match().nodes().clear());
    }
    @Test
    void checkpointsPreserveOrderDuplicatesAndLegacyUnorderedContainsAll() {
        var nodes = List.of("s", "a", "a", "f");
        var exact = new ProgressRequirements(MarkRequirement.NONE, new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.EXACT, nodes), Map.of()));
        var ordered = new ProgressRequirements(MarkRequirement.NONE, new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.SUBSEQUENCE, nodes), Map.of()));
        var unordered = new ProgressRequirements(MarkRequirement.NONE, new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.UNORDERED, nodes), Map.of()));
        assertTrue(ProgressMatcher.matches(exact, new ProgressFacts(Map.of("ignored", 2), nodes)));
        assertFalse(ProgressMatcher.matches(exact, new ProgressFacts(Map.of(), List.of("s", "a", "x", "a", "f"))));
        assertTrue(ProgressMatcher.matches(ordered, new ProgressFacts(Map.of(), List.of("s", "a", "x", "a", "f"))));
        assertFalse(ProgressMatcher.matches(ordered, new ProgressFacts(Map.of(), List.of("s", "a", "f"))));
        assertFalse(ProgressMatcher.matches(ordered, new ProgressFacts(Map.of(), List.of("f", "a", "a", "s"))));
        assertTrue(ProgressMatcher.matches(unordered, new ProgressFacts(Map.of(), List.of("f", "a", "s"))));
        assertFalse(ProgressMatcher.matches(unordered, new ProgressFacts(Map.of(), List.of("f", "s"))));
    }
    @Test
    void exactMarksRequireEqualKeysAndCountsWhilePartialRequiresOnlyMinimums() {
        var exact = new ProgressRequirements(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.EXACT, Map.of("x", 3)), Map.of()), CheckpointRequirement.NONE);
        var partial = new ProgressRequirements(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.PARTIAL, Map.of("x", 3)), Map.of()), CheckpointRequirement.NONE);
        assertTrue(ProgressMatcher.matches(exact, new ProgressFacts(Map.of("x", 3), List.of("ignored"))));
        for (var actual : List.of(Map.of("x", 4), Map.of("x", 3, "extra", 1), Map.of("x", 2), Map.<String,Integer>of()))
            assertFalse(ProgressMatcher.matches(exact, new ProgressFacts(actual, List.of())));
        assertTrue(ProgressMatcher.matches(partial, new ProgressFacts(Map.of("x", 4, "extra", 9), List.of("ignored"))));
        assertFalse(ProgressMatcher.matches(partial, new ProgressFacts(Map.of("x", 2), List.of())));
        assertFalse(ProgressMatcher.matches(partial, new ProgressFacts(Map.of(), List.of())));
    }
    @Test
    void disabledConditionsAcceptAnyRecordedMarksAndHistory() {
        assertTrue(ProgressMatcher.matches(ProgressRequirements.NONE, new ProgressFacts(Map.of("extra", 9), List.of("s", "f"))));
    }
}
