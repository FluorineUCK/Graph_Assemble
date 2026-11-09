package dev.graphassembly.core.io;

import java.util.*;
/** An ephemeral deterministic preflight snapshot; never retain across definition or state changes. */
public record PreflightResult(String incompatibility,List<String> allowed,Map<String,Problem> blocked) {
    public enum BlockedReason { PRIMARY_CONDITIONS, MARK_CAPACITY }
    public record Problem(BlockedReason reason, String message) {}
    public PreflightResult {
        allowed=List.copyOf(allowed);
        blocked=Collections.unmodifiableMap(new LinkedHashMap<>(blocked));
    }
}
