package dev.graphassembly.client.view;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TooltipSummaryTest {
    @Test void longSelectionHasBoundedRowsAndAnAccurateRemainder() {
        var ids = java.util.stream.IntStream.range(0,256).mapToObj(i->"n"+i).toList();
        var summary = TooltipSummary.of(ids);
        assertEquals(List.of("n0","n1","n2","n3"),summary.shown());
        assertEquals(252,summary.remaining());
        assertEquals(0,TooltipSummary.of(List.of()).remaining());
    }
}
