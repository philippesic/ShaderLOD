package com.pp.shaderlod.benchmark;

import java.util.concurrent.atomic.AtomicInteger;

/** Pinned DH beacon cooldown observation; never hides other file work or changes DH scheduling. */
public final class BenchmarkDhMaintenance {
    private static volatile AtomicInteger active;
    private static volatile boolean hookInstalled;
    private BenchmarkDhMaintenance() {}
    public static void hookInstalled() { hookInstalled = true; }
    public static void owner(boolean enabled) {
        if (enabled && active == null) active = new AtomicInteger();
        else if (!enabled) active = null;
    }
    /** Ticket keeps a late callback tied to its original benchmark owner. */
    public static AtomicInteger beginCooldown() {
        var ticket = active;
        if (ticket != null) ticket.incrementAndGet();
        return ticket;
    }
    public static void endCooldown(AtomicInteger ticket) { if (ticket != null) ticket.decrementAndGet(); }
    public static int cooldowns() { var counter = active; return counter == null ? 0 : counter.get(); }
    public static BenchmarkReadiness.Signal fileSignal(long queued, long running, int before, int after) {
        if (queued < 0 || running < 0) return BenchmarkReadiness.Signal.UNAVAILABLE;
        if (queued != 0 || before != after) return BenchmarkReadiness.Signal.BUSY;
        if (running == 0) return BenchmarkReadiness.Signal.READY;
        return hookInstalled && active != null && before > 0 && running == before
                ? BenchmarkReadiness.Signal.READY : BenchmarkReadiness.Signal.BUSY;
    }
}
