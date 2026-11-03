package dev.graphassembly.core;

/** Settled workpieces have neither intermediate state nor an intermediate item. */
public record Completion(Progress state, String item, java.util.List<WeightedOutput> outputs, boolean settled) {
    public Completion { outputs = java.util.List.copyOf(outputs); }
    /** Single-result callers must never silently drop the rest of a simultaneous bundle. */
    public WeightedOutput output() {
        if (outputs.size() > 1) throw new IllegalStateException("Settlement has multiple outputs; use outputs()");
        return outputs.isEmpty() ? null : outputs.getFirst();
    }
}
