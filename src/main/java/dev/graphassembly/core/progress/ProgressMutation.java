package dev.graphassembly.core.progress;

/** Ordered edits to mark values and retained checkpoints. */
public sealed interface ProgressMutation {
    record ClearMark(String mark,Integer count) implements ProgressMutation {}
    record ClearCheckpoint(String node) implements ProgressMutation {}
    record PopCheckpoint() implements ProgressMutation {}
}
