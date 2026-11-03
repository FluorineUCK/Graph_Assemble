package dev.graphassembly.filter;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FilterConfigurationTest {
    @Test void rejectsConfigurationBeforeItCanOverflowTheWire() {
        assertThrows(IllegalArgumentException.class, () -> new FilterConfiguration("test:graph", List.of("x".repeat(32768))));
    }
    @Test void draftCannotConfirmOversizeEvenWhenAllTargetsExist() {
        var ids = java.util.stream.IntStream.range(0,257).mapToObj(i -> "node" + i).toList();
        var draft = new dev.graphassembly.client.view.FilterDraft("test:graph", ids);
        assertFalse(draft.canConfirm(Set.copyOf(ids)));
    }
    @Test void countIdsAndUtf8BudgetAreIndependentAndNeverTruncate() {
        var ids = java.util.stream.IntStream.range(0,256).mapToObj(i -> "节点" + i).toList();
        var config = new FilterConfiguration("test:graph", ids);
        assertEquals(config, FilterConfiguration.parse(config.toJson()));
        assertEquals(2, new FilterConfiguration("test:graph", List.of("a","b","a")).targets().size());
        assertEquals(FilterConfiguration.Problem.ID_TOO_LONG, FilterConfiguration.problem("g".repeat(1025), List.of()));
        assertEquals(FilterConfiguration.Problem.TOO_LARGE, FilterConfiguration.problem("test:graph",
            java.util.stream.IntStream.range(0,20).mapToObj(i -> "中".repeat(900) + i).toList()));
        assertThrows(FilterConfiguration.Invalid.class, () -> FilterConfiguration.parse("{\"graph\":3,\"targets\":[]}"));
        assertThrows(FilterConfiguration.Invalid.class, () -> FilterConfiguration.parse(" ".repeat(16385)));
        assertEquals(new FilterConfiguration("",List.of()), FilterConfiguration.parse("{\"graph\":\"\",\"targets\":[]}"));
    }
}
