package com.pp.shaderlod.optimization.framecopy.exchange;

/** Read permission survives session removal and exactly one replacement construction. */
public final class FreshDefinitionGate {
    public enum Phase { IDLE, RELOAD_REQUIRED, DEFINITION_REQUIRED, DEFINED, STOPPED }
    private Phase phase = Phase.IDLE;
    private Object abandoned, replacement;
    private long epoch;
    public Phase phase() { return phase; }
    public long epoch() { return epoch; }
    public boolean readAllowed() { return phase == Phase.IDLE || phase == Phase.DEFINED; }
    public boolean needsDefinition() { return phase == Phase.DEFINITION_REQUIRED; }
    public void discard(Object allocation) {
        if (!readAllowed()) { stop(); throw new IllegalStateException("REPLACEMENT_DEFINITION_FAILED"); }
        abandoned = allocation; replacement = null; phase = Phase.RELOAD_REQUIRED;
    }
    public void reconstructionReturned() {
        require(phase == Phase.RELOAD_REQUIRED, "ONE_RECONSTRUCTION_ONLY");
        phase = Phase.DEFINITION_REQUIRED;
    }
    public void bind(Object allocation, long allocationEpoch) {
        require(needsDefinition() && allocation != null && allocation != abandoned, "FRESH_ALLOCATION_REQUIRED");
        require(replacement == null || replacement == allocation && epoch == allocationEpoch, "REPLACEMENT_CHANGED");
        replacement = allocation; epoch = allocationEpoch;
    }
    public void positivelyDefined(Object allocation, long allocationEpoch) {
        require(needsDefinition() && replacement == allocation && epoch == allocationEpoch, "BOUND_DEFINITION_REQUIRED");
        phase = Phase.DEFINED; abandoned = null;
    }
    public void stop() { phase = Phase.STOPPED; }
    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
    }
}
