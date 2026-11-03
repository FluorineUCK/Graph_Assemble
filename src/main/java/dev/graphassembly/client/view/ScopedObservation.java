package dev.graphassembly.client.view;

import java.util.ArrayDeque;
import java.util.Objects;

/** Short-lived observation only. Closing the outer scope removes the thread-local entirely. */
public final class ScopedObservation<T> {
    private final ThreadLocal<ArrayDeque<T>> owners = new ThreadLocal<>();
    public T current() { var stack = owners.get(); return stack == null ? null : stack.peek(); }
    public Scope open(T owner) {
        var stack = owners.get();
        if (stack == null) { stack = new ArrayDeque<>(); owners.set(stack); }
        stack.push(Objects.requireNonNull(owner));
        return new Scope(owner);
    }
    public final class Scope implements AutoCloseable {
        private final T owner;
        private boolean closed;
        private Scope(T owner) { this.owner = owner; }
        @Override public void close() {
            if (closed) return;
            var stack = owners.get();
            if (stack == null || stack.peek() != owner) throw new IllegalStateException("Observation closed out of order");
            stack.pop();
            if (stack.isEmpty()) owners.remove();
            closed = true;
        }
    }
}
