package dev.graphassembly.core.io;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
/** Shared scaled choice; preserves arithmetic order and random call boundaries. */
public final class WeightedChoice {
    private WeightedChoice() {}
    public static <T> T choose(List<T> pool, ToDoubleFunction<T> weight, DoubleSupplier random) {
        double scale = pool.stream().mapToDouble(weight).max().orElseThrow();
        double total = pool.stream().mapToDouble(value -> weight.applyAsDouble(value) / scale).sum();
        double draw = random.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0 || draw >= 1) throw new IllegalArgumentException("Random value must be in [0, 1)");
        // A positive first interval contains zero even when scaling underflows its width.
        if (draw == 0) return pool.getFirst();
        double cursor = draw * total;
        for (T value : pool) {
            cursor -= weight.applyAsDouble(value) / scale;
            if (cursor < 0) return value;
        }
        return pool.getLast();
    }
}
