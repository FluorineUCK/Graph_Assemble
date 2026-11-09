package dev.graphassembly.core.condition.mark;

import dev.graphassembly.core.condition.IntRange;
import dev.graphassembly.core.condition.ProgressFacts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkMatcherTest {
    @Test void exactEmptyDiffersFromDisabledAndPreservesZeroKeys() {
        var facts = new ProgressFacts(Map.of("zero", 0), List.of());
        assertTrue(MarkMatcher.matches(MarkRequirement.NONE, facts));
        assertFalse(MarkMatcher.matches(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.EXACT, Map.of()), Map.of()), facts));
        assertTrue(MarkMatcher.matches(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.EXACT, Map.of("zero", 0)), Map.of()), facts));
        assertTrue(MarkMatcher.matches(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.PARTIAL, Map.of()), Map.of()), facts));
    }

    @Test void rangesAndMinimumValuesMustBothMatch() {
        var required = new MarkRequirement.Match(MarkRequirement.Mode.PARTIAL, Map.of("quality", 2));
        var ranges = Map.of("quality", new IntRange(1, 4));
        assertFalse(MarkMatcher.matches(new MarkRequirement(required, ranges), new ProgressFacts(Map.of("quality", 1), List.of())));
        assertTrue(MarkMatcher.matches(new MarkRequirement(required, ranges), new ProgressFacts(Map.of("quality", 2, "extra", 9), List.of())));
        assertTrue(MarkMatcher.matches(new MarkRequirement(required, ranges), new ProgressFacts(Map.of("quality", 4), List.of())));
        assertFalse(MarkMatcher.matches(new MarkRequirement(required, ranges), new ProgressFacts(Map.of("quality", 5), List.of())));
    }

    @Test void rangesStillApplyWithoutAValueRequirementAndMissingMarksAreZero() {
        var facts = new ProgressFacts(Map.of(), List.of());
        assertTrue(MarkMatcher.matches(new MarkRequirement(null, Map.of("missing", new IntRange(0, 0))), facts));
        assertFalse(MarkMatcher.matches(new MarkRequirement(null, Map.of("missing", new IntRange(1, 2))), facts));
    }
}
