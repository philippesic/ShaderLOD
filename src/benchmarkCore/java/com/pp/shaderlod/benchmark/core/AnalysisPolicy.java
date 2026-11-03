package com.pp.shaderlod.benchmark.core;

/** Predeclared analysis settings. Frames/blocks are never counted as independent repeats. */
public record AnalysisPolicy(int blockFrames, double minimumBlockDurationMs,
        int minimumBlocksPerWindow, int minimumMatchedBrackets, int minimumIndependentRepeats,
        String independenceEvidence, double stutterThresholdMs, InferenceScope inferenceScope) {
    public enum InferenceScope { WITHIN_SESSION, CROSS_SESSION }
    /** Compatible constructor: session-local evidence never implies a cross-session claim. */
    public AnalysisPolicy(int blockFrames, double minimumBlockDurationMs, int minimumBlocksPerWindow,
            int minimumMatchedBrackets, int minimumIndependentRepeats, String independenceEvidence, double stutterThresholdMs) {
        this(blockFrames, minimumBlockDurationMs, minimumBlocksPerWindow, minimumMatchedBrackets,
                minimumIndependentRepeats, independenceEvidence, stutterThresholdMs, InferenceScope.WITHIN_SESSION);
    }
    /** Only these settings affect descriptive statistics; calibration may be attached after capture. */
    public boolean sameBlockDefinition(AnalysisPolicy other) {
        return other != null && blockFrames == other.blockFrames
                && Double.compare(minimumBlockDurationMs, other.minimumBlockDurationMs) == 0
                && Double.compare(stutterThresholdMs, other.stutterThresholdMs) == 0;
    }
    public AnalysisPolicy {
        if (blockFrames < 2 || minimumBlocksPerWindow < 2 || minimumMatchedBrackets < 1
                || minimumIndependentRepeats < 1)
            throw new IllegalArgumentException("at least two frames/blocks and one matched bracket/repeat unit required");
        if (!Double.isFinite(minimumBlockDurationMs) || minimumBlockDurationMs <= 0
                || !Double.isFinite(stutterThresholdMs) || stutterThresholdMs <= 0)
            throw new IllegalArgumentException("duration and stutter definition must be finite and positive");
        independenceEvidence = independenceEvidence == null ? "" : independenceEvidence;
        java.util.Objects.requireNonNull(inferenceScope);
        if (inferenceScope == InferenceScope.CROSS_SESSION && minimumIndependentRepeats < 2)
            throw new IllegalArgumentException("cross-session claims require multiple independent session units");
    }
}
