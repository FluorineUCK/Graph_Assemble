package dev.graphassembly.client.view;
import java.util.*;
/** Bounded player-facing summaries; detailed technical data is a separate advanced path. */
public record TooltipSummary<T>(List<T> shown, int remaining) {
    public static final int MAX_ROWS = 4;
    public TooltipSummary { shown = List.copyOf(shown); }
    public static <T> TooltipSummary<T> of(Collection<T> values) {
        return new TooltipSummary<>(values.stream().limit(MAX_ROWS).toList(), Math.max(0,values.size()-MAX_ROWS));
    }
}
