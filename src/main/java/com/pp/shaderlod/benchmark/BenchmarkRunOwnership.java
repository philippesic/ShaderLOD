package com.pp.shaderlod.benchmark;

/** Explicit recovery ownership: failure to restore never silently permits a second run. */
public final class BenchmarkRunOwnership {
    private Object owner;
    private boolean recovery;
    public synchronized void acquire(Object candidate) {
        java.util.Objects.requireNonNull(candidate);
        if (owner != null) throw new IllegalStateException("Benchmark already owns the session or its recovery");
        owner = candidate;
        recovery = false;
    }
    public synchronized void recovering(Object candidate) { require(candidate); recovery = true; }
    public synchronized void release(Object candidate, boolean verified) {
        require(candidate);
        if (!verified) { recovery = true; throw new IllegalStateException("Benchmark restoration is unverified; recovery owner retained"); }
        owner = null;
        recovery = false;
    }
    public synchronized boolean active() { return owner != null && !recovery; }
    public synchronized boolean restorationPending() { return owner != null && recovery; }
    private void require(Object candidate) {
        if (owner != candidate) throw new IllegalStateException("Benchmark owner mismatch");
    }
}
