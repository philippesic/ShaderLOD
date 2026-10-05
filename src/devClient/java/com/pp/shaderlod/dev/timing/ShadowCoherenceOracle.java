package com.pp.shaderlod.dev.timing;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Development correctness oracle only; every observation returns the untouched rendering decision. */
public final class ShadowCoherenceOracle {
    private static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.shadowCoherenceOracle");
    private static final int MAX_EXAMPLES = 8;
    private static final long[] observed = new long[2], mismatches = new long[2], trueTrue = new long[2],
            trueFalse = new long[2], falseTrue = new long[2], falseFalse = new long[2], unavailable = new long[2];
    private static final Object[] lastFrustum = new Object[2];
    private static final long[] lastFrame = new long[2], lastGeneration = new long[2];
    private static final int[] lastPlaneCount = new int[2];
    private static final List<Mismatch> EXAMPLES = new ArrayList<>(MAX_EXAMPLES);
    private static final List<String> ERRORS = new ArrayList<>(MAX_EXAMPLES);
    private static boolean recording, mainChecked;
    private static Method mainBegin, mainEnd, mainSnapshot, mainReplayContext;
    private static ReplayCapture replay;
    private static String mainAvailability = "not inspected";

    private ShadowCoherenceOracle() {}
    public static boolean enabled() { return ENABLED; }
    public static boolean recording() { return ENABLED && recording; }

    public static void beginMeasurement() {
        if (!ENABLED) return;
        Arrays.fill(observed, 0); Arrays.fill(mismatches, 0); Arrays.fill(trueTrue, 0); Arrays.fill(trueFalse, 0);
        Arrays.fill(falseTrue, 0); Arrays.fill(falseFalse, 0); Arrays.fill(unavailable, 0);
        Arrays.fill(lastFrustum, null); Arrays.fill(lastFrame, 0); Arrays.fill(lastGeneration, 0); Arrays.fill(lastPlaneCount, 0);
        EXAMPLES.clear(); ERRORS.clear();
        bindMainDiagnostics();
        if (mainBegin != null) invokeBoundary(mainBegin);
        replay = new ReplayCapture(); // Fixed storage only at the enabled diagnostic boundary.
        recording = true;
    }

    public static void endMeasurement() {
        if (!ENABLED) return;
        recording = false;
        if (replay != null && replay.started && !replay.ended) replay.end("measurement ended before next query established sequence boundary", false);
        if (replay != null) replay.owner = null;
        Arrays.fill(lastFrustum, null);
        if (mainEnd != null) invokeBoundary(mainEnd);
    }

    /** Scalar recording only; example/class-name allocations happen on at most eight mismatches. */
    public static void observe(boolean reversed, Object frustum, long frame, OriginalAccess metadata,
                               boolean actual, boolean original, String originalBranch,
                               float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (!recording()) return;
        int index = reversed ? 1 : 0;
        observed[index]++;
        if (actual && original) trueTrue[index]++;
        else if (actual) trueFalse[index]++;
        else if (original) falseTrue[index]++;
        else falseFalse[index]++;
        lastFrustum[index] = frustum; lastFrame[index] = frame;
        lastGeneration[index] = metadata.shaderlod$oracleGeneration();
        lastPlaneCount[index] = metadata.shaderlod$oraclePlaneCount();
        if (replay != null) replay.observe(reversed, frustum, frame, metadata, actual, original,
                minX, minY, minZ, maxX, maxY, maxZ);
        if (actual == original) return;
        mismatches[index]++;
        if (EXAMPLES.size() < MAX_EXAMPLES) {
            EXAMPLES.add(new Mismatch(reversed ? "REVERSED" : "BASE", frustum.getClass().getName(),
                    System.identityHashCode(frustum), frame, metadata.shaderlod$oracleGeneration(),
                    metadata.shaderlod$oraclePlaneCount(), metadata.shaderlod$oracleFma(),
                    finite(metadata.shaderlod$oracleX()), finite(metadata.shaderlod$oracleY()), finite(metadata.shaderlod$oracleZ()),
                    actual, original, originalBranch, finite(minX), finite(minY), finite(minZ), finite(maxX), finite(maxY), finite(maxZ),
                    Float.floatToRawIntBits(minX), Float.floatToRawIntBits(minY), Float.floatToRawIntBits(minZ),
                    Float.floatToRawIntBits(maxX), Float.floatToRawIntBits(maxY), Float.floatToRawIntBits(maxZ)));
        }
    }

    /** Unknown/classifier errors are reported and never alter the optimized result. */
    public static void unavailable(boolean reversed, Throwable failure) {
        if (!recording()) return;
        unavailable[reversed ? 1 : 0]++;
        if (!reversed && replay != null) replay.originalUnavailable = true;
        if (ERRORS.size() < MAX_EXAMPLES) ERRORS.add("Original classifier unavailable: "
                + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    private static void bindMainDiagnostics() {
        if (mainChecked) return;
        mainChecked = true;
        try {
            Class<?> type = Class.forName("com.pp.shaderlod.ShadowFrustumCoherenceDiagnostics", false,
                    ShadowCoherenceOracle.class.getClassLoader());
            mainBegin = type.getMethod("beginMeasurement");
            mainEnd = type.getMethod("endMeasurement");
            mainSnapshot = type.getMethod("snapshot");
            try { mainReplayContext = type.getMethod("captureReplayContext", Object.class); }
            catch (NoSuchMethodException oldCounters) { mainReplayContext = null; }
            mainAvailability = "main diagnostic class available; availability alone does not establish mod/mixin loading";
        } catch (ClassNotFoundException absentMainMod) {
            mainAvailability = "main diagnostic class absent; original-only dev oracle remains available";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            mainAvailability = "main diagnostics unavailable: " + failure.getClass().getSimpleName();
            mainBegin = mainEnd = mainSnapshot = mainReplayContext = null;
        }
    }
    private static Object invokeBoundary(Method method) {
        try { return method.invoke(null); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (ERRORS.size() < MAX_EXAMPLES) ERRORS.add("Main counter boundary failed: " + failure.getClass().getSimpleName());
            mainAvailability = "main counter boundary failed: " + failure.getClass().getSimpleName();
            return null;
        }
    }

    /** Report-only allocations/reflection; never called per AABB. Snapshot before end to retain last identity. */
    public static Snapshot snapshot() {
        Object main = ENABLED && mainSnapshot != null ? invokeBoundary(mainSnapshot) : null;
        return new Snapshot(ENABLED, recording, classSnapshot(0, "BASE"), classSnapshot(1, "REVERSED"),
                List.copyOf(EXAMPLES), List.copyOf(ERRORS), mainAvailability, main,
                replay == null ? null : replay.snapshot(),
                "All armed testAab RETURN observations; original classifier reevaluated. Diagnostic overhead invalidates timing use.");
    }
    private static Double finite(double value) { return Double.isFinite(value) ? value : null; }
    private static Float finite(float value) { return Float.isFinite(value) ? value : null; }

    private static ClassSnapshot classSnapshot(int index, String kind) {
        Object object = lastFrustum[index];
        return new ClassSnapshot(kind, observed[index], mismatches[index], trueTrue[index], trueFalse[index],
                falseTrue[index], falseFalse[index], unavailable[index], object == null ? null : object.getClass().getName(),
                object == null ? null : System.identityHashCode(object), lastFrame[index], lastGeneration[index], lastPlaneCount[index]);
    }

    private static final class ReplayCapture {
        static final int CAPACITY = 8192;
        final int[] bounds = new int[CAPACITY * 6];
        final byte[] outputs = new byte[CAPACITY];
        boolean started, ended, sequenceBoundaryObserved, truncated, originalUnavailable, revisited;
        boolean contextAvailable;
        Object owner, context;
        String frustumClass, contextAvailability = "no BASE observation", endReason = "not started";
        int frustumIdentity, planeCount, recorded;
        long frame, generation, total, preparedXBits, preparedYBits, preparedZBits;
        boolean fma;
        Double preparedX, preparedY, preparedZ;

        void observe(boolean reversed, Object frustum, long frame, OriginalAccess metadata,
                     boolean actual, boolean original, float minX, float minY, float minZ,
                     float maxX, float maxY, float maxZ) {
            if (!started) {
                if (reversed) return;
                started = true; owner = frustum; this.frame = frame;
                generation = metadata.shaderlod$oracleGeneration();
                planeCount = metadata.shaderlod$oraclePlaneCount(); fma = metadata.shaderlod$oracleFma();
                preparedXBits = Double.doubleToRawLongBits(metadata.shaderlod$oracleX());
                preparedYBits = Double.doubleToRawLongBits(metadata.shaderlod$oracleY());
                preparedZBits = Double.doubleToRawLongBits(metadata.shaderlod$oracleZ());
                preparedX = finite(metadata.shaderlod$oracleX()); preparedY = finite(metadata.shaderlod$oracleY());
                preparedZ = finite(metadata.shaderlod$oracleZ());
                frustumClass = frustum.getClass().getName(); frustumIdentity = System.identityHashCode(frustum);
                context(); // Exactly one optional owner-context reflection boundary; never retried per query.
            }
            if (ended) {
                if (!reversed && frustum == owner && frame == this.frame) { total++; revisited = true; }
                return;
            }
            if (reversed || frustum != owner || frame != this.frame) {
                end(reversed ? "next non-BASE frustum" : frame != this.frame ? "next BASE render frame" : "next BASE frustum", true);
                return;
            }
            if (metadata.shaderlod$oracleGeneration() != generation || metadata.shaderlod$oraclePlaneCount() != planeCount
                    || metadata.shaderlod$oracleFma() != fma
                    || preparedXBits != Double.doubleToRawLongBits(metadata.shaderlod$oracleX())
                    || preparedYBits != Double.doubleToRawLongBits(metadata.shaderlod$oracleY())
                    || preparedZBits != Double.doubleToRawLongBits(metadata.shaderlod$oracleZ())) {
                total++;
                end("same-frame frustum context/generation changed", false);
                return;
            }
            total++;
            if (recorded == CAPACITY) { truncated = true; return; }
            int offset = recorded * 6;
            bounds[offset] = Float.floatToRawIntBits(minX); bounds[offset + 1] = Float.floatToRawIntBits(minY);
            bounds[offset + 2] = Float.floatToRawIntBits(minZ); bounds[offset + 3] = Float.floatToRawIntBits(maxX);
            bounds[offset + 4] = Float.floatToRawIntBits(maxY); bounds[offset + 5] = Float.floatToRawIntBits(maxZ);
            outputs[recorded++] = (byte) ((actual ? 1 : 0) | (original ? 2 : 0));
        }
        private void context() {
            if (mainReplayContext == null) { contextAvailability = "main owner-context seam unavailable"; return; }
            try {
                context = mainReplayContext.invoke(null, owner);
                if (context == null) { contextAvailability = "main context capture unavailable/disabled"; return; }
                Class<?> type = context.getClass();
                boolean available = Boolean.TRUE.equals(type.getMethod("available").invoke(context));
                boolean association = Boolean.TRUE.equals(type.getMethod("ownerAssociationVerified").invoke(context));
                Object identity = type.getMethod("frustumIdentity").invoke(context);
                Object className = type.getMethod("frustumClass").invoke(context);
                Object count = type.getMethod("planeCount").invoke(context);
                Object arithmetic = type.getMethod("fma").invoke(context);
                contextAvailable = available && association && Integer.valueOf(frustumIdentity).equals(identity)
                        && frustumClass.equals(className) && Integer.valueOf(planeCount).equals(count)
                        && Boolean.valueOf(fma).equals(arithmetic);
                contextAvailability = contextAvailable ? "verified first BASE owner/frame context"
                        : "main context unknown or identity/count/FMA association failed";
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                contextAvailability = "context boundary failed: " + failure.getClass().getSimpleName();
            }
        }
        void end(String reason, boolean observedBoundary) {
            ended = true; sequenceBoundaryObserved = observedBoundary; endReason = reason;
        }
        ReplayTrace snapshot() {
            List<ReplayQuery> queries = new ArrayList<>(recorded);
            for (int i = 0; i < recorded; i++) {
                queries.add(new ReplayQuery(i, Arrays.copyOfRange(bounds, i * 6, i * 6 + 6),
                        (outputs[i] & 1) != 0, (outputs[i] & 2) != 0));
            }
            return new ReplayTrace("BASE", started, frustumClass, started ? frustumIdentity : null,
                    started ? frame : null, started ? generation : null, planeCount, fma,
                    preparedX, preparedY, preparedZ, contextAvailable, contextAvailability, context,
                    ended, endReason, sequenceBoundaryObserved, total, recorded, truncated,
                    contextAvailable && ended && sequenceBoundaryObserved && !truncated && !originalUnavailable && !revisited,
                    originalUnavailable, revisited, false, List.copyOf(queries),
                    "First observed BASE testAab RETURN sequence in one frustum/frame; existing output reused, no helper/GL replay; "
                            + "proof cache contents not captured; frame IDs are existing GpuTimers runTick IDs");
        }
    }
    public record ReplayQuery(int index, int[] rawBoundsBits, boolean actual, boolean original) {}
    public record ReplayTrace(String kind, boolean started, String frustumClass, Integer frustumIdentity,
                              Long renderFrame, Long prepareOrPlaneGeneration, int planeCount, boolean fma,
                              Double preparedX, Double preparedY, Double preparedZ,
                              boolean contextAvailable, String contextAvailability, Object context,
                              boolean sequenceEnded, String endReason, boolean sequenceBoundaryObserved,
                              long totalObservedQueries, int recordedQueries, boolean truncated, boolean complete,
                              boolean originalUnavailable, boolean revisitedAfterClose, boolean proofCacheStateCaptured,
                              List<ReplayQuery> queries, String scope) {}

    /** Injected by the BASE dev mixin and inherited by reversed; no dependency on a main-mod accessor. */
    public interface OriginalAccess {
        boolean shaderlod$oracleOriginalCorners(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);
        boolean shaderlod$oraclePrimaryAccept(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);
        long shaderlod$oracleGeneration();
        int shaderlod$oraclePlaneCount();
        boolean shaderlod$oracleFma();
        double shaderlod$oracleX();
        double shaderlod$oracleY();
        double shaderlod$oracleZ();
    }
    public record ClassSnapshot(String kind, long comparedAabbs, long mismatches,
                                long actualTrueOriginalTrue, long actualTrueOriginalFalse,
                                long actualFalseOriginalTrue, long actualFalseOriginalFalse,
                                long originalClassifierUnavailable, String lastFrustumClass, Integer lastFrustumIdentity,
                                long lastRenderFrame, long lastPrepareOrPlaneGeneration, int lastPlaneCount) {}
    public record Mismatch(String kind, String frustumClass, int frustumIdentity, long renderFrame,
                           long prepareOrPlaneGeneration, int planeCount, boolean fma,
                           Double preparedX, Double preparedY, Double preparedZ, boolean actual, boolean original,
                           String originalBranch, Float minX, Float minY, Float minZ, Float maxX, Float maxY, Float maxZ,
                           int rawMinXBits, int rawMinYBits, int rawMinZBits, int rawMaxXBits, int rawMaxYBits, int rawMaxZBits) {}
    public record Snapshot(boolean enabled, boolean recording, ClassSnapshot base, ClassSnapshot reversed,
                           List<Mismatch> mismatchExamples, List<String> errors,
                           String mainCounterAvailability, Object mainCounters, ReplayTrace replayTrace, String scope) {}
}
