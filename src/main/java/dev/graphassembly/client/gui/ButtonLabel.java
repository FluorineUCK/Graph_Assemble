package dev.graphassembly.client.gui;

import java.util.function.ToIntFunction;

/** Atomic button labels: a count is either complete or explicitly elided, never a false prefix. */
public final class ButtonLabel {
    private ButtonLabel() {}
    public static String fit(String full, int availableWidth, ToIntFunction<String> measure) {
        if (measure.applyAsInt(full) <= availableWidth) return full;
        return measure.applyAsInt("…") <= availableWidth ? "…" : "";
    }
}
