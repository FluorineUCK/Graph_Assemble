package dev.graphassembly.core.condition.mark;

import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.progress.ProgressMutation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkAccountingTest {
    @Test void editsDoNotChangeInputOrPreviouslyReturnedSnapshots() {
        var before = new MarkAccounting.State(Map.of("quality", 3), Map.of("polish", 2));
        var accounting = new MarkAccounting(before);
        accounting.clear(new ProgressMutation.ClearMark("quality", 1));
        var first = accounting.snapshot();
        accounting.clear(new ProgressMutation.ClearMark("quality", null));

        assertEquals(Map.of("quality", 3), before.values());
        assertEquals(Map.of("quality", 2), first.values());
        assertEquals(Map.of(), accounting.snapshot().values());
        assertEquals(before.grantCounts(), first.grantCounts());
        assertEquals(before.grantCounts(), accounting.snapshot().grantCounts());
    }

    @Test void stateKeepsIndependentImmutableCopiesOfValuesAndApplicationCounts() {
        var values = new HashMap<>(Map.of("quality", 0));
        var counts = new HashMap<>(Map.of("polish", 2));
        var state = new MarkAccounting.State(values, counts);
        values.clear();
        counts.clear();
        assertEquals(Map.of("quality", 0), state.values());
        assertEquals(Map.of("polish", 2), state.grantCounts());
        assertThrows(UnsupportedOperationException.class, () -> state.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.grantCounts().clear());
    }

    @Test void effectsKeepImmutableGrantAmountsAndTheOptionalApplicationLimit() {
        var amounts = new HashMap<>(Map.of("quality", 0, "worked", 1));
        var effects = new NodeDefinition.NodeEffects(null, amounts, 2, false, List.of());
        amounts.clear();
        assertEquals(Map.of("quality", 0, "worked", 1), effects.markAmounts());
        assertEquals(2, effects.maxMarkApplications());
        assertThrows(UnsupportedOperationException.class, () -> effects.markAmounts().clear());
        var unlimited = new NodeDefinition.NodeEffects(null, Map.of(), null, false, List.of());
        assertNull(unlimited.maxMarkApplications());
        assertTrue(unlimited.markAmounts().isEmpty());
    }
}
