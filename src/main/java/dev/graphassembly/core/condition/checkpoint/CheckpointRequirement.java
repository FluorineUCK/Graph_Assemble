package dev.graphassembly.core.condition.checkpoint;

import dev.graphassembly.core.condition.IntRange;
import java.util.List;
import java.util.Map;

/** A null match omits sequence matching, not count checks. An empty match retains its mode. */
public record CheckpointRequirement(Match match, Map<String, IntRange> counts) {
    public static final CheckpointRequirement NONE = new CheckpointRequirement(null, Map.of());
    public enum CheckpointMode { EXACT, SUBSEQUENCE, UNORDERED }
    public record Match(CheckpointMode mode, List<String> nodes) {
        public Match { nodes = List.copyOf(nodes); }
    }
    public CheckpointRequirement { counts = Map.copyOf(counts); }
}
