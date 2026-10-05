package com.pp.shaderlod.lod;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class EffectPolicyTest {
    private static final DistanceBands BANDS = new DistanceBands(32, 96, 8);
    private static final EffectPolicy ACTIVE = new EffectPolicy(true, true, BANDS, 0.5, 0.25);

    @Test void defaultsRetainOriginalWorkAndExposeStableKeys() {
        var suffixes = new HashSet<String>();
        for (Effect effect : Effect.values()) {
            var policy = EffectPolicy.defaultsFor(effect);
            assertFalse(policy.enabled());
            assertTrue(policy.preserveSalientLighting());
            assertEquals(1, policy.mediumWorkRatio());
            assertEquals(1, policy.farWorkRatio());
            assertEquals(64, policy.decide(true, effect, 1000, 64, 1).workLimit());
            assertTrue(suffixes.add(effect.uniformKeySuffix()));
        }
        assertEquals("ShadowFiltering", Effect.SHADOW_FILTERING.uniformKeySuffix());
        assertEquals("AmbientOcclusion", Effect.AMBIENT_OCCLUSION.uniformKeySuffix());
        assertEquals("Reflections", Effect.REFLECTIONS.uniformKeySuffix());
        assertEquals("Volumetrics", Effect.VOLUMETRICS.uniformKeySuffix());
        assertEquals("WaterSurface", Effect.WATER_SURFACE.uniformKeySuffix());
        assertEquals("Materials", Effect.MATERIALS.uniformKeySuffix());
        assertEquals(new DistanceBands(96, 256, 32), EffectPolicy.defaultsFor(Effect.WATER_SURFACE).bands());
        assertEquals(BANDS, EffectPolicy.defaultsFor(Effect.REFLECTIONS).bands());
    }

    @Test void globalAndEffectOffPreserveOriginalWork() {
        var globalOff = ACTIVE.decide(false, Effect.REFLECTIONS, 1000, 64, 1);
        assertEquals(64, globalOff.workLimit());
        assertEquals(1, globalOff.retentionRatio());
        assertEquals(WorkBudget.Reason.GLOBAL_DISABLED, globalOff.reason());
        var effectOff = new EffectPolicy(false, true, BANDS, 0, 0)
                .decide(true, Effect.REFLECTIONS, 1000, 64, 1);
        assertEquals(64, effectOff.workLimit());
        assertEquals(WorkBudget.Reason.EFFECT_DISABLED, effectOff.reason());
    }

    @Test void salientLightingGuardLivesInTheImmutableSnapshot() {
        for (Effect effect : new Effect[]{Effect.SHADOW_FILTERING, Effect.AMBIENT_OCCLUSION}) {
            var protectedBudget = ACTIVE.decide(true, effect, 1000, 64, 1);
            assertEquals(64, protectedBudget.workLimit());
            assertEquals(WorkBudget.Reason.PROTECTED_LIGHTING, protectedBudget.reason());
            var optedIn = new EffectPolicy(true, false, BANDS, 0.5, 0.25)
                    .decide(true, effect, 1000, 64, 1);
            assertEquals(16, optedIn.workLimit());
        }
    }

    @Test void continuousBudgetsUseCameraRelativeBlockBands() {
        double[] distances = {0, 28, 32, 36, 92, 96, 100, 1000};
        int[] expected = {64, 64, 48, 32, 32, 24, 16, 16};
        for (int i = 0; i < distances.length; i++) {
            var budget = ACTIVE.decide(true, Effect.REFLECTIONS, distances[i], 64, 1);
            assertEquals(expected[i], budget.workLimit());
            assertEquals(WorkBudget.Reason.POLICY, budget.reason());
        }
    }

    @Test void quantizationRoundsUpAndHonorsTheAdaptersLegalMinimum() {
        assertEquals(3, ACTIVE.decide(true, Effect.REFLECTIONS, 1000, 9, 0).workLimit());
        assertEquals(4, ACTIVE.decide(true, Effect.REFLECTIONS, 1000, 9, 4).workLimit());
        assertEquals(1, ACTIVE.decide(true, Effect.REFLECTIONS, 1000, 1, 0).workLimit());
        var skip = new EffectPolicy(true, true, BANDS, 0, 0);
        assertEquals(0, skip.decide(true, Effect.MATERIALS, 1000, 9, 0).workLimit());
        assertEquals(1, skip.decide(true, Effect.MATERIALS, 1000, 9, 1).workLimit());
        assertEquals(0, skip.decide(true, Effect.MATERIALS, 1000, 0, 0).workLimit());
    }

    @Test void representativeAdapterUsesItsOwnUpstreamCountAndNeverIncreasesWork() {
        // An explicit adapter supplies its selected pack option, not a hard-coded universal sample count.
        for (int upstream : new int[]{0, 1, 2, 9, 24, 64, 65535, Integer.MAX_VALUE}) {
            int minimum = upstream == 0 ? 0 : 1;
            int previous = upstream;
            for (double distance = 0; distance <= 128; distance += 0.125) {
                var budget = ACTIVE.decide(true, Effect.REFLECTIONS, distance, upstream, minimum);
                assertTrue(budget.workLimit() >= minimum && budget.workLimit() <= previous);
                double requested = upstream * budget.retentionRatio();
                if (requested >= minimum) {
                    assertTrue(budget.workLimit() >= requested);
                    assertTrue(budget.workLimit() - requested < 1);
                }
                previous = budget.workLimit();
            }
        }
    }

    @Test void invalidRuntimeInputsFailOpenEvenWithAZeroRequestedCap() {
        var policy = new EffectPolicy(true, false, BANDS, 0, 0);
        for (double distance : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertInvalidPreserved(policy.decide(true, Effect.MATERIALS, distance, 24, 1), 24);
        }
        assertInvalidPreserved(policy.decide(true, null, 1000, 24, 1), 24);
        assertInvalidPreserved(policy.decide(true, Effect.MATERIALS, 1000, -4, 0), -4);
        assertInvalidPreserved(policy.decide(true, Effect.MATERIALS, 1000, 24, -1), 24);
        assertInvalidPreserved(policy.decide(true, Effect.MATERIALS, 1000, 24, 25), 24);
    }

    @Test void rejectsUnboundedOrIncreasingDistanceBudgets() {
        for (double[] ratios : new double[][]{
                {-1, 0}, {0.5, -1}, {1.1, 1}, {0.25, 0.5},
                {Double.NaN, 0}, {1, Double.NaN}, {Double.POSITIVE_INFINITY, 0}}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new EffectPolicy(true, true, BANDS, ratios[0], ratios[1]));
        }
        assertThrows(NullPointerException.class, () -> new EffectPolicy(true, true, null, 1, 1));
        assertThrows(NullPointerException.class, () -> EffectPolicy.defaultsFor(null));
    }

    @Test void resultRejectsInvalidManualCaps() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkBudget(24, 25, 1, WorkBudget.Reason.POLICY));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkBudget(24, -1, 0, WorkBudget.Reason.POLICY));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkBudget(24, 0, Double.NaN, WorkBudget.Reason.POLICY));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkBudget(-1, -1, 1, WorkBudget.Reason.POLICY));
        assertThrows(IllegalArgumentException.class,
                () -> new WorkBudget(24, 12, 0.5, WorkBudget.Reason.GLOBAL_DISABLED));
        assertFalse(new WorkBudget(-1, -1, 1, WorkBudget.Reason.INVALID_RUNTIME_INPUT).reduced());
        assertTrue(ACTIVE.decide(true, Effect.MATERIALS, 1000, 24, 1).reduced());
    }

    private static void assertInvalidPreserved(WorkBudget budget, int upstream) {
        assertEquals(upstream, budget.upstreamWork());
        assertEquals(upstream, budget.workLimit());
        assertEquals(1, budget.retentionRatio());
        assertEquals(WorkBudget.Reason.INVALID_RUNTIME_INPUT, budget.reason());
    }
}
