package dev.graphassembly.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Read-only auxiliary participation; never widens the engine's primary execution preflight. */
public final class WorkpieceParticipation {
    private WorkpieceParticipation() {}

    public static Map<String, List<WorkpieceInput>> auxiliaries(GraphDefinition graph, Progress state,
                                                               GraphEngine.Preflight preflight) {
        if (state == null || preflight.incompatibility() != null) return Map.of();
        var candidates = new LinkedHashSet<>(preflight.allowed());
        candidates.addAll(preflight.blocked().keySet());
        var result = new LinkedHashMap<String, List<WorkpieceInput>>();
        for (var id : candidates) {
            var roles = graph.nodes().get(id).workpieceInputs().stream()
                .filter(role -> role.itemInput() > 0 && role.matches(state)).toList();
            if (!roles.isEmpty()) result.put(id, roles);
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
