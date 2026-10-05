package com.pp.shaderlod;

import java.lang.reflect.Field;

/** Opt-in diagnostic counters only. Default-false final gate can fold out of the production AABB path. */
public final class ShadowFrustumCoherenceDiagnostics {
    private static final String HELPER_SOURCE_SHA256 = "f730516be75ca91ae915d548d5454cfb92507d478af4f3fba9262c3b476254bb";
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

    /** Single replay boundary: exact helper ownership and live owner fields must match, otherwise unknown. */
    public static ReplayContext captureReplayContext(Object frustum) {
        if (!ENABLED || !recording) return null;
        boolean association = false;
        try {
            if (!(lastHelper instanceof ShadowFrustumCoherence helper) || frustum == null || requests == 0) {
                return replayUnavailable(frustum, false, "no recorded helper request/owner");
            }
            Field helperField = field(frustum.getClass(), "shaderlod$coherence");
            if (helperField.get(frustum) != helper) {
                return replayUnavailable(frustum, false, "last helper is not this frustum's injected helper");
            }
            float[][] ownerPlanes = (float[][]) field(frustum.getClass(), "planes").get(frustum);
            int ownerCount = field(frustum.getClass(), "planeCount").getInt(frustum);
            boolean ownerFma = field(frustum.getClass(), "FMA_SUPPORT").getBoolean(null);
            Object primaryBox = field(frustum.getClass(), "boxCuller").get(frustum);
            double ownerLimit = primaryBox == null ? Double.POSITIVE_INFINITY
                    : field(primaryBox.getClass(), "maxDistance").getDouble(primaryBox);
            if (!helper.diagnosticMatchesContext(ownerPlanes, ownerCount, ownerFma, ownerLimit)) {
                return replayUnavailable(frustum, false, "helper context differs from current base owner fields");
            }
            association = true;
            PlaneContext context = helper.diagnosticPlaneContext();
            if (context == null || !context.available()) {
                return replayUnavailable(frustum, true, context == null ? "context capture disabled" : context.availability());
            }
            double limit = Double.longBitsToDouble(context.rawDistanceLimitBits());
            return new ReplayContext(true, "verified base owner/helper/context association", true,
                    System.identityHashCode(helper), System.identityHashCode(frustum), frustum.getClass().getName(),
                    HELPER_SOURCE_SHA256, context.planeCount(), context.fma(), context.rawDistanceLimitBits(),
                    Long.toHexString(context.rawDistanceLimitBits()), Double.isFinite(limit) ? limit : null,
                    Double.isNaN(limit) ? "nan" : limit == Double.POSITIVE_INFINITY ? "positive_infinity"
                            : limit == Double.NEGATIVE_INFINITY ? "negative_infinity" : "finite",
                    context.rawPlaneBits(), requests, invalidations, lastOutcome.name());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return replayUnavailable(frustum, association, "owner context unavailable: " + failure.getClass().getSimpleName());
        }
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (int depth = 0; type != null && depth < 8; depth++, type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) { /* Only this audited name, bounded ancestry search. */ }
        }
        throw new NoSuchFieldException(name);
    }
    private static ReplayContext replayUnavailable(Object frustum, boolean association, String reason) {
        return new ReplayContext(false, reason, association, lastHelper == null ? null : System.identityHashCode(lastHelper),
                frustum == null ? null : System.identityHashCode(frustum), frustum == null ? null : frustum.getClass().getName(),
                HELPER_SOURCE_SHA256, 0, false, 0, null, null, "unavailable", null, requests, invalidations, lastOutcome.name());
    }
    public record PlaneContext(boolean available, String availability, int planeCount, boolean fma,
                               long rawDistanceLimitBits, int[][] rawPlaneBits) {}
    public record ReplayContext(boolean available, String availability, boolean ownerAssociationVerified,
                                Integer helperIdentity, Integer frustumIdentity, String frustumClass,
                                String helperSourceSha256, int planeCount, boolean fma, long rawDistanceLimitBits,
                                String distanceLimitBitsHex, Double distanceLimit, String distanceLimitKind,
                                int[][] rawPlaneBits, long globalRequestSequence, long globalInvalidationSequence,
                                String lastHelperOutcome) {}

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
