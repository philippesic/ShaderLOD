package com.pp.shaderlod.lod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistanceBandsTest {
    @Test void fullWidthBandsAreCenteredOnBlockBoundaries() {
        var bands = new DistanceBands(32, 96, 8);
        assertEquals(0, bands.mediumBlend(28));
        assertEquals(0.15625, bands.mediumBlend(30));
        assertEquals(0.5, bands.mediumBlend(32));
        assertEquals(1, bands.mediumBlend(36));
        assertEquals(0, bands.farBlend(92));
        assertEquals(0.5, bands.farBlend(96));
        assertEquals(1, bands.farBlend(100));
    }

    @Test void zeroWidthUsesStableExactBoundaryConvention() {
        var bands = new DistanceBands(32, 96, 0);
        assertEquals(0, bands.mediumBlend(Math.nextDown(32.0)));
        assertEquals(1, bands.mediumBlend(32));
        assertEquals(0, bands.farBlend(Math.nextDown(96.0)));
        assertEquals(1, bands.farBlend(96));
    }

    @Test void touchingBandsAreValidAndMonotonic() {
        var bands = new DistanceBands(32, 96, 64);
        assertEquals(0, bands.mediumBlend(0));
        assertEquals(1, bands.mediumBlend(64));
        assertEquals(0, bands.farBlend(64));
        double previousMedium = 0;
        double previousFar = 0;
        for (double distance = 0; distance <= 160; distance += 0.125) {
            double medium = bands.mediumBlend(distance);
            double far = bands.farBlend(distance);
            assertTrue(medium >= previousMedium && medium <= 1);
            assertTrue(far >= previousFar && far <= medium);
            previousMedium = medium;
            previousFar = far;
        }
    }

    @Test void invalidRuntimeDistancesGiveFullWorkWeights() {
        var bands = new DistanceBands(32, 96, 8);
        for (double distance : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(0, bands.mediumBlend(distance));
            assertEquals(0, bands.farBlend(distance));
        }
        assertEquals(1, bands.farBlend(Double.MAX_VALUE));
    }

    @Test void rejectsInvalidConfigurationAndOverflowingEndpoints() {
        for (double[] values : new double[][]{
                {0, 96, 8}, {96, 32, 8}, {32, 32, 0}, {32, 96, -1},
                {32, 96, 65}, {2, 96, 5}, {Double.NaN, 96, 8},
                {32, Double.POSITIVE_INFINITY, 8}, {32, 96, Double.NaN},
                {Double.MAX_VALUE / 4, Double.MAX_VALUE, Double.MAX_VALUE / 4}}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new DistanceBands(values[0], values[1], values[2]));
        }
    }
}
