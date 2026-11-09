package dev.graphassembly.core.condition.checkpoint;

import dev.graphassembly.core.condition.IntRange;
import dev.graphassembly.core.condition.ProgressFacts;
import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement.CheckpointMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointMatcherTest {
    @Test void unorderedDoesNotCountDuplicatesButExplicitCountsStillApply() {
        var unordered = new CheckpointRequirement.Match(CheckpointMode.UNORDERED, List.of("a", "a"));
        var once = new ProgressFacts(Map.of(), List.of("a"));
        assertTrue(CheckpointMatcher.matches(new CheckpointRequirement(unordered, Map.of()), once));
        assertFalse(CheckpointMatcher.matches(new CheckpointRequirement(unordered, Map.of("a", new IntRange(2, 2))), once));
        assertTrue(CheckpointMatcher.matches(new CheckpointRequirement(unordered, Map.of("a", new IntRange(2, 2))), new ProgressFacts(Map.of(), List.of("a", "a"))));
        assertFalse(CheckpointMatcher.matches(new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.SUBSEQUENCE, List.of("a", "a")), Map.of()), once));
    }

    @Test void cachedCountsStayWithTheirSnapshotAndMissingRecordsCountAsZero() {
        var records = new ArrayList<>(List.of("a", "a"));
        var before = new ProgressFacts(Map.of(), records);
        assertTrue(CheckpointMatcher.matches(new CheckpointRequirement(null, Map.of("a", new IntRange(2, 2))), before));
        records.clear();
        var after = new ProgressFacts(Map.of(), records);
        assertTrue(CheckpointMatcher.matches(new CheckpointRequirement(null, Map.of("a", new IntRange(2, 2))), before));
        assertFalse(CheckpointMatcher.matches(new CheckpointRequirement(null, Map.of("a", new IntRange(2, 2))), after));
        assertTrue(CheckpointMatcher.matches(new CheckpointRequirement(null, Map.of("a", new IntRange(0, 0))), after));
        assertFalse(CheckpointMatcher.matches(new CheckpointRequirement(new CheckpointRequirement.Match(CheckpointMode.EXACT, List.of()), Map.of()), before));
    }
}
