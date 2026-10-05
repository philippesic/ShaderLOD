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
    private static Method mainBegin, mainEnd, mainSnapshot;
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
        recording = true;
    }

    public static void endMeasurement() {
        if (!ENABLED) return;
        recording = false;
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
            mainAvailability = "main diagnostic class available; availability alone does not establish mod/mixin loading";
        } catch (ClassNotFoundException absentMainMod) {
            mainAvailability = "main diagnostic class absent; original-only dev oracle remains available";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            mainAvailability = "main diagnostics unavailable: " + failure.getClass().getSimpleName();
            mainBegin = mainEnd = mainSnapshot = null;
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
                           String mainCounterAvailability, Object mainCounters, String scope) {}
}
