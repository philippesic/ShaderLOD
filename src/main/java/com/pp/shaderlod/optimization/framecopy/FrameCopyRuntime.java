package com.pp.shaderlod.optimization.framecopy;

import java.util.List;

/**
 * Opt-in diagnostic only. All mutators and snapshot must run on the render thread at control boundaries.
 * Never enable in low-overhead timing windows. No ownership mutation or copy elision is implemented.
 */
public final class FrameCopyRuntime {
    private FrameCopyRuntime() {}
    private static boolean observing;
    private static long epoch;
    private static FrameCopyCounters counters = new FrameCopyCounters();
    public static boolean observing() { return observing; }
    public static void setObserving(boolean enabled) {
        if (observing != enabled) { observing = enabled; epoch++; }
        if (enabled) FrameCopyPins.exactApi(); // initialize class-resource checks at the control boundary
    }
    public static void resetCounters() { counters = new FrameCopyCounters(); epoch++; }
    public static FrameCopyCounters.Snapshot snapshot() { return counters.snapshot(observing, FrameCopyPins.exactApi()); }
    public static boolean active() { return observing && FrameCopyPins.exactApi(); }
    public static long epoch() { return epoch; }
    public static void beginFinal(Object graph, List<FrameCopyCounters.Target> targets, String status) {
        if (active()) counters.begin(graph, targets, status);
    }
    public static void swapExecuted(int width, int height) { if (active()) counters.swapExecuted(width, height); }
    public static void fallbackExecuted(int width, int height) { if (active()) counters.fallbackExecuted(width, height); }
    public static void endFinal() { if (active()) counters.end(); }
}
