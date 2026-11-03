package com.pp.shaderlod.integration.makeupquad;
/** Render-thread counters for actual copied submissions; no timing or performance claim. */
public final class SubmissionCopyEvidence {
    private static long batches,nativeCommands,submittedCommands;
    private SubmissionCopyEvidence() {}
    public static void record(int before,int after) { batches++;nativeCommands+=before;submittedCommands+=after; }
    public static long batches() { return batches; }
    public static long nativeCommands() { return nativeCommands; }
    public static long submittedCommands() { return submittedCommands; }
}
