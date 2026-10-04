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

    private GeometryCoverageGuard.Scene scene(Object world, Object pipeline, Object camera,
                                               int x, int y, int z, int requested, int cap, int dh) {
        return new GeometryCoverageGuard.Scene(world, pipeline, camera, x, y, z, requested, cap, dh);
    }

    @Test void startupAcknowledgementWaitsForEligibleSceneWithoutNeedingAnotherToggle() {
        var guard = new GeometryCoverageGuard();
        var initial = scene(new Object(), new Object(), new Object(), 0, 5, 0, 24, 16, 128);
        assertFalse(guard.confirmed(null, true)); // Startup: no player/pipeline/stable scene yet.
        assertFalse(guard.confirmed(null, true));
        assertTrue(guard.confirmed(initial, true));
        assertTrue(guard.confirmed(initial, true));
    }

    @Test void everyAcknowledgedSceneChangeLatchesRestorationUntilExplicitReconfirmation() {
        Object world = new Object(), pipeline = new Object(), camera = new Object();
        var initial = scene(world, pipeline, camera, 0, 5, 0, 24, 16, 128);
        var changed = new GeometryCoverageGuard.Scene[] {
                null, // Unload, disabled policy, DH readiness loss, or unstable renderer.
                scene(new Object(), pipeline, camera, 0, 5, 0, 24, 16, 128),
                scene(world, new Object(), camera, 0, 5, 0, 24, 16, 128),
                scene(world, pipeline, new Object(), 0, 5, 0, 24, 16, 128),
                scene(world, pipeline, camera, 1, 5, 0, 24, 16, 128),
                scene(world, pipeline, camera, 0, 6, 0, 24, 16, 128),
                scene(world, pipeline, camera, 0, 5, -1, 24, 16, 128),
                scene(world, pipeline, camera, 0, 5, 0, 32, 16, 128),
                scene(world, pipeline, camera, 0, 5, 0, 24, 12, 128),
                scene(world, pipeline, camera, 0, 5, 0, 24, 16, 64)
        };
        for (var next : changed) {
            var guard = new GeometryCoverageGuard();
            assertTrue(guard.confirmed(initial, true));
            assertFalse(guard.confirmed(next, true));
            assertFalse(guard.confirmed(initial, true)); // Returning cannot silently re-arm.
            assertFalse(guard.confirmed(initial, false));
            assertTrue(guard.confirmed(initial, true));
        }
    }

    @Test void hotGetterSceneCheckRestoresOnTravelWithCorrectNegativeCoordinateBoundaries() {
        Object world = new Object(), camera = new Object();
        var origin = scene(world, new Object(), camera, 0, 5, 0, 24, 16, 128);
        assertTrue(origin.contains(world, camera, 0.0, 80.0, 15.999));
        assertFalse(origin.contains(world, camera, -0.001, 80.0, 0.0));
        assertFalse(origin.contains(world, camera, 16.0, 80.0, 0.0));
        assertFalse(origin.contains(world, camera, 0.0, 96.0, 0.0));
        assertFalse(origin.contains(new Object(), camera, 0.0, 80.0, 0.0));
        assertFalse(origin.contains(world, new Object(), 0.0, 80.0, 0.0));
        assertEquals(-1, GeometryCoverageGuard.cell(-16.0));
        assertEquals(-2, GeometryCoverageGuard.cell(-16.001));
    }

    @Test void getterObservedExcursionCannotReactivateBetweenTicks() {
        var guard = new GeometryCoverageGuard();
        var initial = scene(new Object(), new Object(), new Object(), 0, 5, 0, 24, 16, 128);
        assertTrue(guard.confirmed(initial, true));
        guard.invalidate();
        assertFalse(guard.isConfirmed(initial));
        assertFalse(guard.confirmed(initial, true));
        assertFalse(guard.confirmed(initial, false));
        assertTrue(guard.confirmed(initial, true));
    }

    @Test void explicitConfirmationRearmsRevokedCoverageButStillWaitsForEligibleScene() {
        var guard = new GeometryCoverageGuard();
        var initial = scene(new Object(), new Object(), new Object(), 0, 5, 0, 24, 16, 128);
        assertTrue(guard.confirmed(initial, true));
        guard.invalidate();
        assertFalse(guard.confirmed(initial, true));
        guard.rearm();
        assertFalse(guard.confirmed(null, true));
        assertFalse(guard.isConfirmed(initial));
        assertTrue(guard.confirmed(initial, true));
    }

    @Test void dhConfiguredReachMustCoverEntireOriginalRequestedRing() {
        assertFalse(GeometryLodPolicy.reachesRequestedRadius(16, 24));
        assertFalse(GeometryLodPolicy.reachesRequestedRadius(0, 24));
        assertFalse(GeometryLodPolicy.reachesRequestedRadius(128, 0));
        assertTrue(GeometryLodPolicy.reachesRequestedRadius(24, 24));
        assertTrue(GeometryLodPolicy.reachesRequestedRadius(128, 24));
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
