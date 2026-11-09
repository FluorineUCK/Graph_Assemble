package dev.graphassembly.core.condition.mark;

import dev.graphassembly.core.condition.ProgressFacts;
import java.util.Map;

/** Pure matching of mark values and inclusive ranges; never grants or clears marks. */
public final class MarkMatcher {
    private MarkMatcher() {}

    public static boolean matches(MarkRequirement requirement, ProgressFacts facts) {
        Map<String, Integer> actual = facts.marks();
        var match = requirement.match();
        boolean valuesMatch = match == null || (match.mode() == MarkRequirement.Mode.EXACT
            ? match.values().equals(actual)
            : match.values().entrySet().stream().allMatch(e -> actual.getOrDefault(e.getKey(), 0) >= e.getValue()));
        return valuesMatch && requirement.ranges().entrySet().stream()
            .allMatch(e -> e.getValue().contains(actual.getOrDefault(e.getKey(), 0)));
    }
}
