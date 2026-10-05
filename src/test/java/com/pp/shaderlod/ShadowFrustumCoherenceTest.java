package com.pp.shaderlod;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowFrustumCoherenceTest {
    /** Independent full pinned classifier, including its differently rounded negative expression. */
    private static int original(float[][] planes, int count, boolean fma, float[] b) {
        boolean fullyInside = true;
        for (int i = 0; i < count; i++) {
            float[] p = planes[i];
            float x = p[0] < 0 ? b[0] : b[3];
            float y = p[1] < 0 ? b[1] : b[4];
            float z = p[2] < 0 ? b[2] : b[5];
            if (fma) {
                if (Math.fma(p[0], x, Math.fma(p[1], y, p[2] * z)) >= -p[3]) {
                    fullyInside &= Math.fma(p[0], p[0] < 0 ? b[3] : b[0],
                            Math.fma(p[1], p[1] < 0 ? b[4] : b[1],
                                    Math.fma(p[2], p[2] < 0 ? b[5] : b[2], p[3]))) >= 0;
                } else return 0;
            } else {
                if (safe(p[0], x, safe(p[1], y, p[2] * z)) >= -p[3]) {
                    fullyInside &= safe(p[0], p[0] < 0 ? b[3] : b[0],
                            safe(p[1], p[1] < 0 ? b[4] : b[1],
                                    safe(p[2], p[2] < 0 ? b[5] : b[2], p[3]))) >= 0;
                } else return 0;
            }
        }
        return fullyInside ? 1 : 2;
    }

    private static float safe(float a, float b, float c) { return a * b + c; }
    private static boolean distanceReject(double distance, float[] b) {
        return b[3] < -distance || b[0] > distance || b[4] < -distance || b[1] > distance
                || b[5] < -distance || b[2] > distance;
    }
    private static boolean baseline(float[][] planes, int count, boolean fma, double limit, float[] box) {
        return !distanceReject(limit, box) && original(planes, count, fma, box) > 0;
    }
    private static boolean optimized(ShadowFrustumCoherence coherence, float[][] planes, int count,
                                     boolean fma, double limit, float[] b) {
        return coherence.canAccept(b[0], b[1], b[2], b[3], b[4], b[5])
                || baseline(planes, count, fma, limit, b);
    }

    @Test void containedChildrenCanShareProofWhileOtherCellsAndLargeBoxesFallBack() {
        float[][] planes = {{1, 0, 0, 200}, {-1, 0, 0, 200}, {0, 1, 0, 200}, {0, 0, 1, 200}};
        var cache = new ShadowFrustumCoherence(planes, 4, true, 256);
        assertTrue(cache.canAccept(1, 2, 3, 19.25f, 20.25f, 21.25f));
        assertTrue(cache.canAccept(17, 18, 19, 35.25f, 36.25f, 37.25f));
        assertFalse(cache.canAccept(1, 2, 3, 200, 20, 21));
        assertFalse(cache.canAccept(-250, 2, 3, -231.75f, 20, 21));
    }

    @Test void partialDistanceOverlapDoesNotProveEveryChild() {
        var cache = new ShadowFrustumCoherence(new float[0][], 0, true, 100);
        assertFalse(cache.canAccept(80, 0, 0, 90, 10, 10)); // 64..160 coarse domain crosses distance boundary.
        float[] rejected = {110, 0, 0, 120, 10, 10};
        assertFalse(optimized(cache, new float[0][], 0, true, 100, rejected));
        assertEquals(baseline(new float[0][], 0, true, 100, rejected),
                optimized(cache, new float[0][], 0, true, 100, rejected));
    }

    @Test void invalidateAndResetRemoveProofsAfterPlaneDistanceAndFmaChanges() {
        float[][] planes = {{1, 0, 0, 200}};
        var cache = new ShadowFrustumCoherence(planes, 1, true, 256);
        assertTrue(cache.canAccept(0, 0, 0, 18.25f, 18.25f, 18.25f));
        planes[0][3] = -1000;
        cache.invalidate();
        assertFalse(cache.canAccept(0, 0, 0, 18.25f, 18.25f, 18.25f));
        cache.reset(new float[][]{{1, 0, 0, 200}}, 1, false, 10);
        assertFalse(cache.canAccept(0, 0, 0, 18.25f, 18.25f, 18.25f));
        cache.reset(new float[0][], 0, false, Double.POSITIVE_INFINITY);
        assertTrue(cache.canAccept(0, 0, 0, 18.25f, 18.25f, 18.25f));
    }

    @Test void directMappedCollisionsCannotReuseAnotherCellProof() {
        float[][] planes = {{-1, 0, 0, 100}};
        var cache = new ShadowFrustumCoherence(planes, 1, false, Double.POSITIVE_INFINITY);
        assertTrue(cache.canAccept(0, 0, 0, 18.25f, 18.25f, 18.25f));
        // x cells 0 and64 map to the same low six-bit slot; the latter is outside.
        assertFalse(cache.canAccept(4096, 0, 0, 4114.25f, 18.25f, 18.25f));
        assertTrue(cache.canAccept(0, 0, 0, 18.25f, 18.25f, 18.25f));
    }

    @Test void nonfiniteInvertedSignedZeroAndExtremeInputsMatchOriginalFallback() {
        float[][] planes = {{1, -1, 0.5f, 200}};
        float[] special = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_VALUE, -Float.MIN_VALUE,
                Float.MIN_NORMAL, -0.0f, 0.0f};
        for (boolean fma : new boolean[]{false, true}) {
            var cache = new ShadowFrustumCoherence(planes, 1, fma, Double.POSITIVE_INFINITY);
            for (float value : special) {
                for (int component = 0; component < 6; component++) {
                    float[] b = {0, 0, 0, 18.25f, 18.25f, 18.25f};
                    b[component] = value;
                    assertEquals(baseline(planes, 1, fma, Double.POSITIVE_INFINITY, b),
                            optimized(cache, planes, 1, fma, Double.POSITIVE_INFINITY, b));
                }
            }
            float[] inverted = {20, 20, 20, 0, 0, 0};
            assertFalse(cache.canAccept(20, 20, 20, 0, 0, 0));
            assertEquals(baseline(planes, 1, fma, Double.POSITIVE_INFINITY, inverted),
                    optimized(cache, planes, 1, fma, Double.POSITIVE_INFINITY, inverted));
        }
    }

    @Test void nanPlaneAndNanOrNegativeDistanceNeverProduceFastAcceptance() {
        assertFalse(new ShadowFrustumCoherence(new float[][]{{Float.NaN, 0, 0, 100}}, 1, true,
                Double.POSITIVE_INFINITY).canAccept(0, 0, 0, 18, 18, 18));
        for (double limit : new double[]{Double.NaN, -1, Double.NEGATIVE_INFINITY}) {
            assertFalse(new ShadowFrustumCoherence(new float[0][], 0, false, limit).canAccept(0, 0, 0, 18, 18, 18));
        }
    }

    @Test void originalFullyInsideCodeIsNotAConservativeFloatContainmentProof() {
        float[][] planes = {{1.0e20f, -1.0e20f, -1, -0.5f}};
        float[] coarse = {1, 0, 0, 2, 1, 1};
        float[] containedPoint = {1, 1, 1, 1, 1, 1};
        for (boolean fma : new boolean[]{false, true}) {
            assertEquals(1, original(planes, 1, fma, coarse));
            assertEquals(0, original(planes, 1, fma, containedPoint));
            var cache = new ShadowFrustumCoherence(planes, 1, fma, Double.POSITIVE_INFINITY);
            assertFalse(cache.canAccept(1, 1, 1, 1, 1, 1));
            assertEquals(baseline(planes, 1, fma, Double.POSITIVE_INFINITY, containedPoint),
                    optimized(cache, planes, 1, fma, Double.POSITIVE_INFINITY, containedPoint));
        }
    }

    @Test void boundedRandomizedFiniteAndRawBitPropertyChecksMatchOriginal() {
        Random random = new Random(0xC011E2E1L);
        int fastAccepts = 0;
        for (boolean fma : new boolean[]{false, true}) {
            for (int trial = 0; trial < 5_000; trial++) {
                int count = 1 + random.nextInt(13);
                float[][] planes = new float[count][4];
                for (float[] p : planes) {
                    for (int i = 0; i < 4; i++) {
                        p[i] = trial % 4 == 0 ? Float.intBitsToFloat(random.nextInt())
                                : i == 3 ? 1000.0f : (random.nextFloat() - 0.5f) * 2.0f;
                    }
                }
                double distance = trial % 4 == 0 ? Double.POSITIVE_INFINITY : 1024;
                var cache = new ShadowFrustumCoherence(planes, count, fma, distance);
                for (int child = 0; child < 8; child++) {
                    float[] box = new float[6];
                    for (int axis = 0; axis < 3; axis++) {
                        float low = trial % 4 == 0 ? Float.intBitsToFloat(random.nextInt())
                                : random.nextInt(16) * 16.0f - 128.0f;
                        box[axis] = low;
                        box[axis + 3] = low + 18.25f;
                    }
                    boolean fast = cache.canAccept(box[0], box[1], box[2], box[3], box[4], box[5]);
                    if (fast) fastAccepts++;
                    assertFalse(fast && !baseline(planes, count, fma, distance, box));
                    assertEquals(baseline(planes, count, fma, distance, box),
                            optimized(cache, planes, count, fma, distance, box));
                }
            }
        }
        assertTrue(fastAccepts > 1_000, "Property exercise must include real fast accepts, not only fallback.");
    }

    @Test void reversedDistanceAndAlternateBoxAcceptancePreserveOriginalOrdering() {
        float[][] planes = {{1, 0, 0, -1000}}; // Plane alone rejects the test box.
        float[] insideAlternate = {0, 0, 0, 18, 18, 18};
        var cache = new ShadowFrustumCoherence(planes, 1, true, 256);
        boolean expected = !distanceReject(256, insideAlternate)
                && (!distanceReject(128, insideAlternate) || original(planes, 1, true, insideAlternate) > 0);
        boolean actual = cache.canAccept(0, 0, 0, 18, 18, 18)
                || !distanceReject(256, insideAlternate)
                && (!distanceReject(128, insideAlternate) || original(planes, 1, true, insideAlternate) > 0);
        assertTrue(expected);
        assertEquals(expected, actual);
        float[] outsideDistance = {300, 0, 0, 318, 18, 18};
        assertFalse(cache.canAccept(300, 0, 0, 318, 18, 18));
        assertTrue(distanceReject(256, outsideDistance));
    }
}
