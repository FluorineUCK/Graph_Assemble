package dev.graphassembly.core;

import java.util.Map;

public record MarkRequirement(Mode mode, Map<String, Integer> values) {
    public enum Mode { EXACT, PARTIAL }
    public MarkRequirement { values = Map.copyOf(values); }
}
