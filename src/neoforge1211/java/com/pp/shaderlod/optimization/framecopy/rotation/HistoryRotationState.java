package com.pp.shaderlod.optimization.framecopy.rotation;

/**
 * Render-thread, GL-free history protocol, created only after native initialization is proven.
 * Initial native equality does not qualify omission: a completed warm shadow frame is required.
 * A returned call proves submission, never GPU success.
 */
public final class HistoryRotationState {
    public enum Phase { IDLE, FRAME, BOUND, DRAWING, DRAWN, FINAL, COPIED, SUSPENDED, CLOSED }
    public enum Direction { A_TO_B, B_TO_A }
    public record Snapshot(Phase phase, int parity, boolean warm, boolean equal,
                           boolean rotating, boolean seeded, boolean omit, long completed, long omitted) {}
    private Phase phase = Phase.IDLE;
    private int parity;
    private boolean warm, equal = true, rotating, seeded, coverage, omit;
    private long completed, omitted;

    public Snapshot snapshot() {
        return new Snapshot(phase, parity, warm, equal, rotating, seeded, omit, completed, omitted);
    }
    public Phase phase() { return phase; }
    public int parity() { return parity; }
    public boolean warm() { return warm; }
    public boolean omit() { return omit; }
    public boolean rotating() { return rotating; }
    public int readTexture(int a, int b) { return parity == 0 ? a : b; }
    public int writeTexture(int a, int b) { return parity == 0 ? b : a; }
    /** Idle Off/abort/pre-quad seed: committed history -> next write. */
    public Direction seedDirection() { return parity == 0 ? Direction.A_TO_B : Direction.B_TO_A; }
    /** Post-draw drain: completed output -> history. Deliberately opposite to seedDirection. */
    public Direction drainDirection() { return parity == 0 ? Direction.B_TO_A : Direction.A_TO_B; }
    public boolean inFrame() { return phase != Phase.IDLE && phase != Phase.CLOSED && phase != Phase.SUSPENDED; }
    public boolean selecting() { return inFrame() && rotating; }
    public void begin(boolean rotate) {
        require(phase == Phase.IDLE, "FRAME_REENTRY");
        require(!rotate || warm, "NO_COMPLETED_CANONICAL_WARM_FRAME");
        require(rotate || parity == 0 && equal || !warm, "NATIVE_WITH_UNREPAIRED_PARITY");
        rotating = rotate; seeded = coverage = omit = false; phase = Phase.FRAME;
    }
    public void bound() { require(phase == Phase.FRAME, "MISSING_OR_REPEATED_C2_BIND"); phase = Phase.BOUND; }
    /** Called after FBO/sampler binding. The parity MUST remain latched, including on a veto. */
    public boolean beforeDraw(boolean fullCoverage) {
        require(phase == Phase.BOUND, "MISSING_OR_REPEATED_C2_DRAW");
        coverage = fullCoverage;
        return rotating && !fullCoverage;
    }
    public void seeded() {
        require(phase == Phase.BOUND && rotating && !coverage, "SEED_PHASE");
        seeded = true; equal = true;
    }
    public void drawStarted() {
        require(phase == Phase.BOUND, "MISSING_OR_REPEATED_C2_DRAW");
        require(!rotating || coverage || seeded, "PARTIAL_WRITE_WITHOUT_SEED");
        equal = false; phase = Phase.DRAWING;
    }
    public void drawReturned() {
        require(phase == Phase.DRAWING, "UNEXPECTED_DRAW_RETURN");
        phase = Phase.DRAWN;
    }
    public boolean beginFinal(boolean stillAdmitted) {
        require(phase == Phase.DRAWN, "FINAL_WITHOUT_EXACTLY_ONE_DRAW");
        omit = rotating && coverage && stillAdmitted;
        phase = Phase.FINAL; return omit;
    }
    public void nativeCopyReturned() {
        require(phase == Phase.FINAL && !omit, "UNEXPECTED_RT3_NATIVE_COPY");
        equal = true; phase = Phase.COPIED;
    }
    public void finalReturned() {
        require(omit && phase == Phase.FINAL || !omit && phase == Phase.COPIED, "FINAL_COPY_STREAM_INCOMPLETE");
        if (omit) { parity ^= 1; omitted++; }
        else { parity = 0; warm = true; }
        completed++; rotating = false; phase = Phase.IDLE;
    }
    /** An abort never commits W. If native copy returned already, equality is authoritative. */
    public boolean needsRepair() { return !equal; }
    public void repaired() {
        // Caller must have submitted R->W, or already witnessed the returned native W->R.
        equal = true; parity = 0; rotating = omit = seeded = false; phase = Phase.IDLE;
    }
    public void suspend() { phase = Phase.SUSPENDED; }
    public void close() {
        require(phase == Phase.IDLE && (!warm || equal) && parity == 0, "CLOSE_WITHOUT_EQUALITY");
        warm = false; phase = Phase.CLOSED;
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }
}
