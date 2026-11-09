package dev.graphassembly.core.condition.mark;

import dev.graphassembly.core.condition.IntRange;
import java.util.Map;

/** A null match omits value matching, not range checks. An empty match retains its mode. */
public record MarkRequirement(Match match, Map<String, IntRange> ranges) {
    public static final MarkRequirement NONE = new MarkRequirement(null, Map.of());
    public enum Mode { EXACT, PARTIAL }
    public record Match(Mode mode, Map<String, Integer> values) {
        public Match { values = Map.copyOf(values); }
    }
    public MarkRequirement { ranges = Map.copyOf(ranges); }
}
