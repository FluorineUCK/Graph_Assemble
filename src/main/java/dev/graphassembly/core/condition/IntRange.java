package dev.graphassembly.core.condition;

/** Inclusive nonnegative count range. Integer.MAX_VALUE represents an omitted upper bound. */
public record IntRange(int min, int max) {
    public IntRange {
        if (min < 0 || max < min) throw new IllegalArgumentException("Invalid count range: " + min + ".." + max);
    }

    public boolean contains(int value) { return value >= min && value <= max; }
}
