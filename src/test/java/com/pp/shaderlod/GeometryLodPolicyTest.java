package com.pp.shaderlod;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GeometryLodPolicyTest {
    private GeometryLodPolicy.Inputs ready() {
        return new GeometryLodPolicy.Inputs(true, true, true, true, true, true, 600, 600, 16);
    }

    @Test void readyPolicyCapsOnlyEffectiveDistanceAndNeverRaisesServerOrManualLimit() {
        var decision = GeometryLodPolicy.decide(ready());
        assertTrue(decision.active());
        assertEquals(16, decision.apply(24));
        assertEquals(12, decision.apply(12));
        assertEquals(8, decision.apply(8));
    }

    @Test void everyMissingReadinessConditionFailsOpen() {
        var cases = new GeometryLodPolicy.Inputs[] {
                new GeometryLodPolicy.Inputs(false, true, true, true, true, true, 600, 600, 16),
                new GeometryLodPolicy.Inputs(true, false, true, true, true, true, 600, 600, 16),
                new GeometryLodPolicy.Inputs(true, true, false, true, true, true, 600, 600, 16),
                new GeometryLodPolicy.Inputs(true, true, true, false, true, true, 600, 600, 16),
                new GeometryLodPolicy.Inputs(true, true, true, true, false, true, 600, 600, 16),
                new GeometryLodPolicy.Inputs(true, true, true, true, true, false, 600, 600, 16),
                new GeometryLodPolicy.Inputs(true, true, true, true, true, true, 599, 600, 16)
        };
        for (var input : cases) {
            var decision = GeometryLodPolicy.decide(input);
            assertFalse(decision.active(), decision.reason());
            assertEquals(24, decision.apply(24), decision.reason());
        }
    }

    @Test void disabledPolicyRestoresTheCurrentPreferenceRatherThanAnOldCapturedValue() {
        var active = GeometryLodPolicy.decide(ready());
        assertEquals(16, active.apply(24));
        assertEquals(16, active.apply(32)); // User raises requested radius while cap is active.
        var disabled = GeometryLodPolicy.decide(new GeometryLodPolicy.Inputs(
                true, false, true, true, true, true, 600, 600, 16));
        assertEquals(32, disabled.apply(32));
        assertEquals(10, disabled.apply(10)); // A new lower preference is also preserved.
    }

    @Test void coverageAcknowledgementCannotLeakAcrossWorldsOrDimensions() {
        var guard = new GeometryCoverageGuard();
        Object firstWorld = new Object();
        Object nextDimension = new Object();
        assertFalse(guard.confirmed(null, true));
        assertTrue(guard.confirmed(firstWorld, true));
        assertTrue(guard.confirmed(firstWorld, true));
        assertFalse(guard.confirmed(null, true));
        assertFalse(guard.confirmed(nextDimension, true));
        assertFalse(guard.confirmed(nextDimension, false));
        assertTrue(guard.confirmed(nextDimension, true));
    }

    @Test void invalidCapsAreBoundedAndWarmupBoundaryIsExact() {
        assertEquals(8, GeometryLodPolicy.decide(new GeometryLodPolicy.Inputs(
                true, true, true, true, true, true, 600, 600, -1)).apply(24));
        assertEquals(24, GeometryLodPolicy.decide(new GeometryLodPolicy.Inputs(
                true, true, true, true, true, true, 600, 600, 100)).apply(24));
        assertFalse(GeometryLodPolicy.decide(new GeometryLodPolicy.Inputs(
                true, true, true, true, true, true, 0, 100, 16)).active());
        assertTrue(GeometryLodPolicy.decide(ready()).active());
    }
}
