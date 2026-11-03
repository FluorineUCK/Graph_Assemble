package dev.graphassembly.core;

/** Weight belongs to the immediately enclosing choice, not to a flattened global leaf pool. */
public sealed interface OutputNode permits WeightedOutput, OutputGroup {
    double weight();
    double chance();
}
