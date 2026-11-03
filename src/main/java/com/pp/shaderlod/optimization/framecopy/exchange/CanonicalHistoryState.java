package com.pp.shaderlod.optimization.framecopy.exchange;

/**
 * One allocation's two-bank recurrence. GL witnesses belong to the controller;
 * this class cannot turn an unknown submission/copy into a valid history.
 */
public final class CanonicalHistoryState {
    public enum Phase { READY, PRODUCING, PRODUCED, DISCARDED, CLOSED }
    public record Banks(int committed, int write) {}
    public record Repair(int source, int destination) {}
    private Phase phase = Phase.READY;
    private int committed;
    private long exchanges;

    /** Caller must have witnessed the returned native full clear or known-valid two-bank seed of BOTH banks. */
    public CanonicalHistoryState() {}
    public Phase phase() { return phase; }
    public long exchanges() { return exchanges; }
    public Banks banks() {
        require(phase == Phase.READY || phase == Phase.PRODUCING || phase == Phase.PRODUCED);
        return new Banks(committed, 1 - committed);
    }
    /** Called immediately before the qualified terminal native submission. */
    public Banks producerStarted() {
        require(phase == Phase.READY);
        phase = Phase.PRODUCING;
        return banks();
    }
    /** A returned Java call alone is insufficient; caller supplies its GL success witness. */
    public void producerCompleted() {
        require(phase == Phase.PRODUCING);
        phase = Phase.PRODUCED;
    }
    /**
     * Publish the producer bank only at the final RT3 copy seam. In shadow mode
     * the retained copy must have written OLD C, never the bank published next.
     * Checking physical provenance prevents equality after a copy hiding a bad recurrence.
     */
    public void exchange(int observedReadBank, int observedWriteBank, Integer retainedCopyDestination) {
        require(phase == Phase.PRODUCED);
        require(observedReadBank == committed && observedWriteBank == 1 - committed);
        require(retainedCopyDestination == null || retainedCopyDestination == committed);
        committed = observedWriteBank;
        exchanges++;
        phase = Phase.READY;
    }
    /**
     * Before a rejected draw or Off/native return, one known-C→other copy makes
     * BOTH banks native-equivalent. The other bank may contain H(t-2); restoring
     * only the committed read bank would be wrong for an ensuing partial draw.
     */
    public Repair planNativeReturn() {
        require(phase == Phase.READY);
        return new Repair(committed, 1 - committed);
    }
    /** Only after the repair copy returned successfully and aliases were canonicalized. */
    public void nativeReturnCompleted() {
        require(phase == Phase.READY);
        committed = 0;
        phase = Phase.CLOSED;
    }
    /** Partial/unknown producer, copy or repair result: no further bank can be read/published. */
    public void discard() { phase = Phase.DISCARDED; }
    private static void require(boolean valid) {
        if (!valid) throw new IllegalStateException("HISTORY_WITNESS_OR_PHASE_MISMATCH");
    }
}
