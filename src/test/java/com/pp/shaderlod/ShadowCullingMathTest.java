package com.pp.shaderlod;

import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowCullingMathTest {
    @AfterEach void resetController() {
        ShadowCullingController.configure(false);
        ShadowCullingController.setEligible(false);
    }

    /** Independent pinned Iris tri-state oracle, including its otherwise unused negative test. */
    private static int original(float[][] planes, int count, boolean fma,
                                float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ) {
        boolean fullyInside = true;
        for (int i = 0; i < count; i++) {
            float[] plane = planes[i];
            float pX = plane[0] < 0.0f ? minX : maxX;
            float pY = plane[1] < 0.0f ? minY : maxY;
            float pZ = plane[2] < 0.0f ? minZ : maxZ;
            if (fma) {
                if (Math.fma(plane[0], pX, Math.fma(plane[1], pY, plane[2] * pZ)) >= -plane[3]) {
                    fullyInside &= Math.fma(plane[0], plane[0] < 0.0f ? maxX : minX,
                            Math.fma(plane[1], plane[1] < 0.0f ? maxY : minY,
                                    Math.fma(plane[2], plane[2] < 0.0f ? maxZ : minZ, plane[3]))) >= 0.0f;
                } else {
                    return 0;
                }
            } else {
                if (safeFma(plane[0], pX, safeFma(plane[1], pY, plane[2] * pZ)) >= -plane[3]) {
                    fullyInside &= safeFma(plane[0], plane[0] < 0.0f ? maxX : minX,
                            safeFma(plane[1], plane[1] < 0.0f ? maxY : minY,
                                    safeFma(plane[2], plane[2] < 0.0f ? maxZ : minZ, plane[3]))) >= 0.0f;
                } else {
                    return 0;
                }
            }
        }
        return fullyInside ? 1 : 2;
    }

    private static float safeFma(float a, float b, float c) { return a * b + c; }

    private static void compare(float[][] planes, int count, float[] box) {
        for (boolean fma : new boolean[]{false, true}) {
            assertEquals(original(planes, count, fma, box[0], box[1], box[2], box[3], box[4], box[5]) > 0,
                    ShadowCullingMath.visible(planes, count, fma,
                            box[0], box[1], box[2], box[3], box[4], box[5]),
                    "Pinned classifier differs with FMA=" + fma);
        }
    }

    @Test void allThreeOriginalClassificationsAndTangencyRemainExact() {
        float[][] planes = {{1, 0, 0, 0}, {-1, 0, 0, 2}};
        assertEquals(1, original(planes, 2, true, 0.5f, 0, 0, 1.5f, 1, 1));
        assertEquals(2, original(planes, 2, true, -1, 0, 0, 1, 1, 1));
        assertEquals(0, original(planes, 2, true, -2, 0, 0, -1, 1, 1));
        compare(planes, 2, new float[]{0.5f, 0, 0, 1.5f, 1, 1});
        compare(planes, 2, new float[]{-1, 0, 0, 1, 1, 1});
        compare(planes, 2, new float[]{-2, 0, 0, -1, 1, 1});
        compare(planes, 2, new float[]{-1, 0, 0, 0, 1, 1});
        compare(planes, 2, new float[]{2, 0, 0, 3, 1, 1});
        compare(new float[0][], 0, new float[6]);
    }

    @Test void planeCountDoesNotReadUnusedStorage() {
        compare(new float[][]{{0, 0, 0, 0}, null}, 1, new float[6]);
        assertTrue(ShadowCullingMath.visible(new float[][]{null}, 0, true, 0, 0, 0, 0, 0, 0));
    }

    @Test void ulpBoundaryAndNegativeCoefficientCases() {
        for (float sign : new float[]{-1.0f, 1.0f}) {
            for (float boundary : new float[]{0.0f, -0.0f, Float.MIN_VALUE, -Float.MIN_VALUE,
                    1.0f, -1.0f, 1000.0f, -1000.0f, Float.MAX_VALUE, -Float.MAX_VALUE}) {
                float[][] planes = {{sign, 0, 0, -boundary * sign}};
                for (float endpoint : new float[]{Math.nextDown(boundary), boundary, Math.nextUp(boundary)}) {
                    compare(planes, 1, new float[]{endpoint, -1, -1, endpoint, 1, 1});
                    compare(planes, 1, new float[]{endpoint - 1, -1, -1, endpoint + 1, 1, 1});
                }
            }
        }
    }

    @Test void signedZeroUsesLessThanRatherThanSignBit() {
        float[][] planes = {{-0.0f, 1.0f, 0.0f, 0.0f}};
        float[] box = {0, 1, 0, Float.POSITIVE_INFINITY, 1, 0};
        compare(planes, 1, box);
        assertFalse(ShadowCullingMath.visible(planes, 1, true, 0, 1, 0, Float.POSITIVE_INFINITY, 1, 0));
        compare(new float[][]{{0.0f, -0.0f, 0.0f, -0.0f}}, 1,
                new float[]{-0.0f, 0.0f, -0.0f, 0.0f, -0.0f, 0.0f});
    }

    @Test void nanInfinityOverflowAndInvertedEndpointsRetainOriginalBehavior() {
        float[] special = {Float.NaN, Float.intBitsToFloat(0x7f800001), Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.MIN_NORMAL, Float.MIN_VALUE, -0.0f, 0.0f};
        for (float value : special) {
            for (int coordinate = 0; coordinate < 6; coordinate++) {
                float[] box = {-1, -2, -3, 4, 5, 6};
                box[coordinate] = value;
                compare(new float[][]{{1, -1, 0.5f, -1}, {-1, 0.25f, 1, 2}}, 2, box);
            }
            for (int coefficient = 0; coefficient < 4; coefficient++) {
                float[] plane = {1, -1, 0.5f, 1};
                plane[coefficient] = value;
                compare(new float[][]{plane}, 1, new float[]{4, 5, 6, -1, -2, -3});
            }
        }
        assertFalse(ShadowCullingMath.visible(new float[][]{{Float.NaN, 0, 0, 1}}, 1,
                true, 0, 0, 0, 1, 1, 1));
        compare(new float[][]{{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}},
                1, new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                        -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE});
    }

    @Test void fusedAndSeparateArithmeticMustNotBeInterchanged() {
        float a = 1.0f + Math.ulp(1.0f);
        float x = 1.0f - Math.ulp(1.0f);
        float[][] planes = {{a, -1, 0, 0}};
        compare(planes, 1, new float[]{x, 1, 0, x, 1, 0});
        assertTrue(ShadowCullingMath.visible(planes, 1, false, x, 1, 0, x, 1, 0));
        assertFalse(ShadowCullingMath.visible(planes, 1, true, x, 1, 0, x, 1, 0));
    }

    @Test void randomizedFinitePlanesAndMixedRawFloatBitsMatchPinnedOracle() {
        Random random = new Random(0x51AD0C11L);
        for (int sample = 0; sample < 50_000; sample++) {
            int count = random.nextInt(14);
            float[][] planes = new float[count][4];
            float[] box = new float[6];
            for (float[] plane : planes) {
                for (int c = 0; c < 4; c++) {
                    plane[c] = sample % 3 == 0 ? Float.intBitsToFloat(random.nextInt())
                            : (random.nextFloat() - 0.5f) * (c == 3 ? 1000.0f : 2.0f);
                }
            }
            for (int c = 0; c < 6; c++) {
                box[c] = sample % 3 == 0 ? Float.intBitsToFloat(random.nextInt())
                        : (random.nextFloat() - 0.5f) * 1000.0f;
            }
            compare(planes, count, box);
        }
    }

    @Test void controllerRequiresConfigurationAndEligibilityAndInvalidatesImmediately() throws Exception {
        var stateMethod=com.pp.shaderlod.optimization.OptimizationConfig.class.getDeclaredMethod("state");
        stateMethod.setAccessible(true);Object previous=stateMethod.invoke(null);
        var restoreMethod=com.pp.shaderlod.optimization.OptimizationConfig.class.getDeclaredMethod("restoreState",previous.getClass());
        restoreMethod.setAccessible(true);
        try {
        com.pp.shaderlod.optimization.OptimizationConfig.load(java.nio.file.Files.createTempDirectory("shadow-controller-").resolve("requests.properties"));
        var feature=com.pp.shaderlod.optimization.OptimizationId.SHADOW_POSITIVE_VERTEX;
        com.pp.shaderlod.optimization.OptimizationConfig.replace(
            com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested().with(feature,true));
        assertFalse(ShadowCullingController.isActive());
        ShadowCullingController.configure(true);
        assertFalse(ShadowCullingController.isActive());
        ShadowCullingController.setEligible(true);
        assertTrue(ShadowCullingController.isActive());
        ShadowCullingController.configure(false);
        assertFalse(ShadowCullingController.isActive());
        ShadowCullingController.configure(true);
        assertTrue(ShadowCullingController.isActive());
        ShadowCullingController.setEligible(false);
        assertFalse(ShadowCullingController.isActive());
        ShadowCullingController.setEligible(true);
        assertTrue(ShadowCullingController.isActive());
        com.pp.shaderlod.optimization.OptimizationConfig.replace(
            com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested());
        assertFalse(ShadowCullingController.isActive(),"Independent request Off invalidates an otherwise eligible controller immediately");
        } finally {restoreMethod.invoke(null,previous);}
    }
}
