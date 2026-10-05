package com.pp.shaderlod;

/** Opt-in diagnostic counters only. Default-false final gate can fold out of the production AABB path. */
public final class ShadowFrustumCoherenceDiagnostics {
    public static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.shadowCoherenceOracle");
    public enum Outcome { NONE, INVALID_AABB, OUTSIDE_COARSE_BOX, CACHE_ACCEPT, CACHE_FALLBACK, NEW_PROOF_ACCEPT, NEW_PROOF_FALLBACK }
    private static boolean recording;
    private static long requests, cachedAccepts, cachedFallbacks, newAccepts, newFallbacks,
            invalidAabbs, outsideCoarseBoxes, resets, invalidations;
    private static Object lastHelper;
    private static int lastPlaneCount;
    private static boolean lastFma;
    private static double lastDistanceLimit;
    private static Outcome lastOutcome = Outcome.NONE;

    private ShadowFrustumCoherenceDiagnostics() {}

    public static void beginMeasurement() {
        if (!ENABLED) return;
        requests = cachedAccepts = cachedFallbacks = newAccepts = newFallbacks = 0;
        invalidAabbs = outsideCoarseBoxes = resets = invalidations = 0;
        lastHelper = null; lastPlaneCount = 0; lastFma = false; lastDistanceLimit = Double.NaN;
        lastOutcome = Outcome.NONE; recording = true;
    }
    public static void endMeasurement() { if (ENABLED) { recording = false; lastHelper = null; } }

    static void request(Object helper, int planes, boolean fma, double limit) {
        if (!recording) return;
        requests++;
        lastHelper = helper; lastPlaneCount = planes; lastFma = fma; lastDistanceLimit = limit;
        lastOutcome = Outcome.NONE;
    }
    static void result(Outcome outcome) {
        if (!recording) return;
        lastOutcome = outcome;
        switch (outcome) {
            case INVALID_AABB -> invalidAabbs++;
            case OUTSIDE_COARSE_BOX -> outsideCoarseBoxes++;
            case CACHE_ACCEPT -> cachedAccepts++;
            case CACHE_FALLBACK -> cachedFallbacks++;
            case NEW_PROOF_ACCEPT -> newAccepts++;
            case NEW_PROOF_FALLBACK -> newFallbacks++;
            case NONE -> { }
        }
    }
    static void contextReset() { if (recording) resets++; }
    static void invalidated() { if (recording) invalidations++; }

    /** Snapshot allocation/identity lookup occurs only at the explicit report boundary. */
    public static Snapshot snapshot() {
        return new Snapshot(ENABLED, recording, requests, cachedAccepts, cachedFallbacks, newAccepts, newFallbacks,
                invalidAabbs, outsideCoarseBoxes, resets, invalidations,
                cachedAccepts + newAccepts, cachedFallbacks + newFallbacks + invalidAabbs + outsideCoarseBoxes,
                requests, invalidations, lastHelper == null ? null : System.identityHashCode(lastHelper),
                lastPlaneCount, lastFma, Double.isFinite(lastDistanceLimit) ? lastDistanceLimit : null,
                lastHelper == null ? "unobserved" : Double.isNaN(lastDistanceLimit) ? "nan"
                        : lastDistanceLimit == Double.POSITIVE_INFINITY ? "positive_infinity"
                        : lastDistanceLimit == Double.NEGATIVE_INFINITY ? "negative_infinity" : "finite", lastOutcome.name(),
                "Client-thread aggregate counters; request/invalidation sequences are global, not per-frustum generations");
    }
    public record Snapshot(boolean enabled, boolean recording, long canAcceptRequests,
                           long cachedProofAccepts, long cachedProofFallbacks, long newProofAccepts, long newProofFallbacks,
                           long invalidAabbFallbacks, long coarseContainmentFallbacks, long contextResets, long invalidations,
                           long totalAccepts, long totalFallbacks, long globalRequestSequence, long globalInvalidationSequence,
                           Integer lastHelperIdentity, int lastPlaneCount, boolean lastFma, Double lastDistanceLimit,
                           String lastDistanceLimitKind, String lastOutcome, String scope) {}
}
