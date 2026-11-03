package dev.graphassembly.filter;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FilterRuleTest {
    @Test void nativeTruthTableCoversEveryAvailableSetAndPolarity() {
        for (int bits = 0; bits < 4; bits++) for (int signs = 0; signs < 4; signs++) {
            Set<String> available = new HashSet<>(), inverted = new HashSet<>();
            if ((bits & 1) != 0) available.add("a"); if ((bits & 2) != 0) available.add("b");
            if ((signs & 1) != 0) inverted.add("a"); if ((signs & 2) != 0) inverted.add("b");
            boolean a = available.contains("a") != inverted.contains("a");
            boolean b = available.contains("b") != inverted.contains("b");
            for (var mode : FilterConfiguration.Mode.values()) {
                var rule = new FilterConfiguration("test:graph", List.of("a", "b"), inverted, mode);
                assertEquals(switch (mode) { case ANY -> a || b; case ALL -> a && b; case NONE -> !a && !b; }, rule.matches(available));
            }
        }
        for (var mode : FilterConfiguration.Mode.values())
            assertFalse(new FilterConfiguration("test:graph", List.of(), Set.of(), mode).matches(Set.of("a")));
    }

    @Test void editingPolarityAndModesIsLocalAndUsesCombinedCapacity() {
        var original = new FilterConfiguration("test:graph", List.of("a"), Set.of("a"), FilterConfiguration.Mode.ALL);
        var draft = new dev.graphassembly.client.view.FilterDraft(original);
        draft.add("a", false);
        assertEquals(Set.of("a"), draft.inverted(), "A selected node cannot be silently inverted by the other add button");
        draft.remove("a"); draft.add("a", false); draft.add("b", true);
        draft.setMode(FilterConfiguration.Mode.NONE);
        assertEquals(Set.of("b"), draft.configuration().inverted());
        assertEquals(FilterConfiguration.Mode.NONE, draft.configuration().mode());
        assertEquals(Set.of("a"), original.inverted());
        assertFalse(draft.canConfirm(Set.of("a")), "Stale negative targets are invalid too");
        draft.clear(); assertTrue(draft.inverted().isEmpty());
        assertFalse(draft.canConfirm(Set.of("a", "b")));
        for (int i = 0; i <= FilterConfiguration.MAX_TARGETS; i++) draft.add("n" + i, i % 2 == 0);
        assertEquals(FilterConfiguration.Problem.TOO_MANY_TARGETS, draft.capacityProblem());
        draft.chooseGraph("test:other"); assertTrue(draft.targets().isEmpty()); assertTrue(draft.inverted().isEmpty());
    }

    @Test void omittedDefaultsRoundTripWithShortJsonEncoding() {
        String shortJson = "{\"graph\":\"test:graph\",\"targets\":[\"a\",\"b\"]}";
        var defaults = FilterConfiguration.parse(shortJson);
        assertEquals(FilterConfiguration.Mode.ANY, defaults.mode());
        assertTrue(defaults.inverted().isEmpty());
        assertEquals(shortJson, defaults.toJson());
        for (var mode : FilterConfiguration.Mode.values()) {
            var rule = new FilterConfiguration("test:graph", List.of("a", "b"), Set.of("b"), mode);
            assertEquals(rule, FilterConfiguration.parse(rule.toJson()));
        }
        assertThrows(FilterConfiguration.Invalid.class, () -> FilterConfiguration.parse(
            "{\"graph\":\"test:graph\",\"targets\":[\"a\"],\"mode\":\"mystery\"}"));
        assertThrows(FilterConfiguration.Invalid.class, () -> FilterConfiguration.parse(
            "{\"graph\":\"test:graph\",\"targets\":[\"a\"],\"inverted\":[\"b\"]}"));
    }
}
