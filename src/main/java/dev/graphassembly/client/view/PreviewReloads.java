package dev.graphassembly.client.view;

import java.util.function.Consumer;

/** Synchronous client-thread lifecycle signals without linking optional JEI classes. */
public final class PreviewReloads {
    public enum Change { RESOURCES, RECIPES, SESSION_ENDED }
    private static long generation;
    private static long recipes;
    private static Consumer<Change> lifecycle;
    private PreviewReloads() {}
    public static void installLifecycle(Consumer<Change> listener) { lifecycle = java.util.Objects.requireNonNull(listener); }
    public static void removeLifecycle(Consumer<Change> listener) { if (lifecycle == listener) lifecycle = null; }
    public static long generation() { return generation; }
    public static long recipes() { return recipes; }
    public static void resourcesChanged() { generation++; if (lifecycle != null) lifecycle.accept(Change.RESOURCES); }
    public static void recipesChanged() { recipes++; generation++; if (lifecycle != null) lifecycle.accept(Change.RECIPES); }
    public static void sessionEnded() {
        recipes++; generation++;
        var listener = lifecycle;
        lifecycle = null;
        if (listener != null) listener.accept(Change.SESSION_ENDED);
    }
}
