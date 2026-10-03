package com.pp.shaderlod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LodSettingsTest {
    @Test void distancesUseBlockBoundaries() {
        var settings = new LodSettings(true, 32, 96, 8, false);
        assertEquals(0, settings.tier(31.99));
        assertEquals(1, settings.tier(32));
        assertEquals(1, settings.tier(95.99));
        assertEquals(2, settings.tier(96));
        assertEquals(0, new LodSettings(false, 32, 96, 8, false).tier(1000));
    }

    @Test void rejectsInvalidZonesAndOverlappingBands() {
        assertThrows(IllegalArgumentException.class, () -> new LodSettings(true, 96, 32, 8, false));
        assertThrows(IllegalArgumentException.class, () -> new LodSettings(true, 32, 96, 65, false));
        assertThrows(IllegalArgumentException.class, () -> new LodSettings(true, Double.NaN, 96, 8, false));
    }
}
