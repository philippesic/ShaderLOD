package com.pp.shaderlod.benchmark;

/** A pending check is not observed light work. Retain only a fresh, actual server reading. */
public final class BenchmarkLightingHistory {
    public static final long MAXIMUM_AGE_NANOS = 500_000_000L;
    private long at;
    private boolean observed, ready, failed;
    public void reset() { observed = ready = failed = false; at = 0; }
    public void completed(long observedAt, boolean isReady) { at = observedAt; ready = isReady; observed = true; failed = false; }
    public void failed() { failed = true; observed = false; }
    public BenchmarkReadiness.Signal signal(long now) {
        if (failed) return BenchmarkReadiness.Signal.UNAVAILABLE;
        if (!observed || now - at > MAXIMUM_AGE_NANOS) return BenchmarkReadiness.Signal.BUSY;
        return ready ? BenchmarkReadiness.Signal.READY : BenchmarkReadiness.Signal.BUSY;
    }
}
