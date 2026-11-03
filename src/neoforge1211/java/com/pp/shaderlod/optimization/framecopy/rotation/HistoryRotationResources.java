package com.pp.shaderlod.optimization.framecopy.rotation;

import java.util.Objects;
import java.util.function.Consumer;

/** Explicit ownership of exactly two new FBOs, never either canonical FBO or any texture. */
public final class HistoryRotationResources<T> implements AutoCloseable {
    private final T twin, fromA;
    private final Consumer<T> destroy;
    private boolean twinDestroyed, fromADestroyed;
    public HistoryRotationResources(T twin, T fromA, Consumer<T> destroy) {
        this.twin = Objects.requireNonNull(twin); this.fromA = Objects.requireNonNull(fromA);
        if (twin == fromA) throw new IllegalArgumentException("DISTINCT_NEW_FBOS_REQUIRED");
        this.destroy = Objects.requireNonNull(destroy);
    }
    public T twin() { return twin; }
    public T fromA() { return fromA; }
    public boolean closed() { return twinDestroyed && fromADestroyed; }
    @Override public void close() {
        // Retain failed deletions for explicit retry; never delete a successful one twice.
        RuntimeException failure = null;
        try { if (!twinDestroyed) { destroy.accept(twin); twinDestroyed = true; } }
        catch (RuntimeException e) { failure = e; }
        try { if (!fromADestroyed) { destroy.accept(fromA); fromADestroyed = true; } }
        catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        if (failure != null) throw failure;
    }
}
