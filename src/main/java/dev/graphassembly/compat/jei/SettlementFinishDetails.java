package dev.graphassembly.compat.jei;

import dev.graphassembly.client.view.SettlementGroups;
import dev.graphassembly.core.IntRange;
import java.util.ArrayList;
import java.util.TreeMap;

/** Bounded single-rule summary. It is never a substitute for the complete Info conditions. */
record SettlementFinishDetails(int rules, String ranges, boolean moreConditions) {
    static SettlementFinishDetails from(SettlementGroups.Finish finish) {
        if (finish.rules().size() != 1) return new SettlementFinishDetails(finish.rules().size(), "", true);
        var rule = finish.rules().getFirst().condition();
        var conditions = rule.requirements();
        var parts = new ArrayList<String>();
        new TreeMap<>(conditions.markRanges()).forEach((id, range) -> parts.add(id + " " + range(range)));
        new TreeMap<>(conditions.checkpointCounts()).forEach((id, range) -> parts.add(id + " ×" + range(range)));
        boolean more = parts.size() > 2 || rule.start() != null || conditions.marks() != null || conditions.checkpoints() != null;
        return new SettlementFinishDetails(1, String.join("; ", parts.subList(0, Math.min(2, parts.size()))), more);
    }
    private static String range(IntRange range) {
        if (range.min() == range.max()) return Integer.toString(range.min());
        return range.min() + "–" + (range.max() == Integer.MAX_VALUE ? "∞" : range.max());
    }
}
