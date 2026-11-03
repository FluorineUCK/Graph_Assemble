package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FilterDraftTest {
    @Test void localEditsAreIsolatedAndStaleTargetsAreNotSilentlyDropped() {
        var saved = new ArrayList<>(List.of("x", "stale"));
        var draft = new FilterDraft("a", saved);
        saved.add("later");
        assertEquals(List.of("x", "stale"), draft.targets());
        assertFalse(draft.canConfirm(Set.of("x")));
        draft.remove("stale"); assertTrue(draft.canConfirm(Set.of("x")));
        draft.chooseGraph("a"); assertEquals(List.of("x"), draft.targets());
        draft.clear(); assertFalse(draft.canConfirm(Set.of("x")));
        assertEquals(List.of("x", "stale", "later"), saved);
        assertFalse(new FilterDraft("", List.of("x")).canConfirm(Set.of("x")));
        assertThrows(UnsupportedOperationException.class, () -> draft.targets().add("x"));
    }
    @Test void switchingGraphsDiscardsOldTargetsAndOnlyValidSelectionsConfirm() {
        var draft = new FilterDraft("a", List.of("x"));
        draft.add("x"); assertEquals(List.of("x"), draft.targets());
        draft.chooseGraph("b"); assertTrue(draft.targets().isEmpty());
        assertFalse(draft.canConfirm(Set.of("y")));
        draft.add("y"); assertTrue(draft.canConfirm(Set.of("y")));
        assertFalse(draft.canConfirm(Set.of("z")));
    }
}
