package com.pp.shaderlod;

import org.junit.jupiter.api.Test;
import com.pp.shaderlod.mixin.ShadowTraversalViewportAccessor;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import org.joml.Vector3d;

import static com.pp.shaderlod.ShadowTraversalInputs.*;
import static org.junit.jupiter.api.Assertions.*;

class ShadowTraversalInputsTest {
    private final Object[] owners = {new Object(), new Object(), new Object(), new Object(),
            new Object(), new Object(), new Object()};

    private CameraState camera() { return CameraState.stored(-1.25, 64.5, 0, -2, 64, 0); }
    private StoredTransform transform() {
        return StoredTransform.stored(-1, 64, 0, -.25f, .5f, 0, -1.25, 64.5, 0);
    }
    private ViewportState viewport() { return new ViewportState(transform(), -1, 4, 0, -2, 64, 0); }
    private TraversalState traversal() {
        return TraversalState.effective(192, false, false, -4, 20, -1, 4, 0, true, false, false);
    }
    private Lifecycle lifecycle() {
        return lifecycle(owners, 1, true, ProjectionSelection.VANILLA);
    }
    private Lifecycle lifecycle(Object[] values, long serial, boolean known, ProjectionSelection projection) {
        return new Lifecycle(Identity.of(values[0]), Identity.of(values[1]), Identity.of(values[2]),
                Identity.of(values[3]), Identity.of(values[4]), Identity.of(values[5]), Identity.of(values[6]),
                serial, known, projection);
    }
    private FrustumState frustum() {
        return frustum(new float[][]{{1, 0, -0.0f, 2}}, false, BoxState.absent());
    }
    private FrustumState frustum(float[][] planes, boolean fma, BoxState box) {
        return FrustumState.stored(FrustumKind.ADVANCED, -1.25, 64.5, 0, fma, planes,
                planes.length, box, BoxState.absent());
    }
    private Capture capture(CameraState camera, ViewportState viewport, TraversalState traversal,
                            Lifecycle lifecycle, FrustumState frustum) {
        return ShadowTraversalInputs.capture(camera, viewport, traversal, lifecycle, frustum);
    }
    private Capture capture(FrustumState frustum) {
        return capture(camera(), viewport(), traversal(), lifecycle(), frustum);
    }
    private Key key() { return capture(frustum()).key(); }
    private void refused(Capture capture, Reason reason) {
        assertEquals(Status.UNCERTIFIED, capture.status());
        assertEquals(reason, capture.reason());
        assertNull(capture.key());
    }

    @Test void separatelyCapturedStoredValuesCompareExactly() {
        Capture first = capture(frustum()), second = capture(frustum());
        assertEquals(Status.INPUTS_CAPTURED, first.status());
        assertEquals(first.key(), second.key());
        assertEquals(first.key().hashCode(), second.key().hashCode());
    }

    @Test void oneCoefficientBitAndSignedZeroChangeTheKey() {
        assertNotEquals(key(), capture(frustum(new float[][]{{Math.nextUp(1f), 0, -0.0f, 2}},
                false, BoxState.absent())).key());
        assertNotEquals(key(), capture(frustum(new float[][]{{1, 0, 0.0f, 2}},
                false, BoxState.absent())).key());
        CameraState negativeZero = CameraState.stored(-1.25, 64.5, -0.0, -2, 64, 0);
        assertNotEquals(key(), capture(negativeZero, viewport(), traversal(), lifecycle(), frustum()).key());
    }

    @Test void activePlanesAreDeepCopiedOnInputAndOutput() {
        float[][] source = {{1, 0, -0.0f, 2}, {0, 1, 0, 3}};
        FrustumState state = frustum(source, false, BoxState.absent());
        Key before = capture(state).key();
        source[0][0] = Float.NaN;
        source[1] = null;
        int[][] exported = state.planeBits();
        exported[0][0] = Float.floatToRawIntBits(55);
        exported[1] = null;
        assertEquals(before, capture(state).key());
        assertEquals(Float.floatToRawIntBits(1), state.planeBits()[0][0]);
    }

    @Test void inactiveBackingPlanesAreNotAnActiveInput() {
        FrustumState state = FrustumState.stored(FrustumKind.ADVANCED, -1.25, 64.5, 0, false,
                new float[][]{{1, 0, -0.0f, 2}, {Float.NaN}}, 1, BoxState.absent(), BoxState.absent());
        assertEquals(key(), capture(state).key());
    }

    @Test void planeOrderCountAndFmaAreInputs() {
        FrustumState first = frustum(new float[][]{{1, 0, 0, 2}, {0, 1, 0, 3}}, false, BoxState.absent());
        FrustumState reordered = frustum(new float[][]{{0, 1, 0, 3}, {1, 0, 0, 2}}, false, BoxState.absent());
        assertNotEquals(capture(first).key(), capture(reordered).key());
        assertNotEquals(key(), capture(first).key());
        assertNotEquals(key(), capture(frustum(new float[][]{{1, 0, -0.0f, 2}}, true, BoxState.absent())).key());
    }

    @Test void storedTransformIsComparedWithoutReconstructionOrFlooring() {
        StoredTransform original = transform();
        // Same xyz, deliberately different stored integral and rounded fraction: must differ.
        StoredTransform floorVariant = StoredTransform.stored(-2, 64, 0, .75f, .5f, 0, -1.25, 64.5, 0);
        assertNotEquals(original, floorVariant);
        assertNotEquals(key(), capture(camera(), new ViewportState(floorVariant, -1, 4, 0, -2, 64, 0),
                traversal(), lifecycle(), frustum()).key());
        StoredTransform fractionBit = StoredTransform.stored(-1, 64, 0, Math.nextDown(-.25f), .5f, 0,
                -1.25, 64.5, 0);
        assertNotEquals(original, fractionBit);
        StoredTransform doubleBit = StoredTransform.stored(-1, 64, 0, -.25f, .5f, 0,
                Math.nextUp(-1.25), 64.5, 0);
        assertNotEquals(original, doubleBit);
    }

    @Test void typedViewportAdapterCopiesTheRealRoundedStoredFields() {
        // The small negative fraction is rounded by Sodium's signed 128 trick.
        Viewport actual = new Viewport(new UnknownFrustum(), new Vector3d(-1.0000001, 64.123456, -0.0));
        var nativeTransform = actual.getTransform();
        ViewportState copied = ShadowTraversalViewportAccessor.captureViewport(actual);
        assertEquals(nativeTransform.intX, copied.transform().intX());
        assertEquals(nativeTransform.intY, copied.transform().intY());
        assertEquals(nativeTransform.intZ, copied.transform().intZ());
        assertEquals(Float.floatToRawIntBits(nativeTransform.fracX), copied.transform().fracXBits());
        assertEquals(Float.floatToRawIntBits(nativeTransform.fracY), copied.transform().fracYBits());
        assertEquals(Float.floatToRawIntBits(nativeTransform.fracZ), copied.transform().fracZBits());
        assertEquals(Double.doubleToRawLongBits(nativeTransform.x), copied.transform().xBits());
        assertEquals(Double.doubleToRawLongBits(nativeTransform.y), copied.transform().yBits());
        assertEquals(Double.doubleToRawLongBits(nativeTransform.z), copied.transform().zBits());
        assertEquals(-1, copied.transform().intX()); // Java truncation, not floor -2.
        assertNotEquals(Float.floatToRawIntBits((float) (nativeTransform.x - nativeTransform.intX)),
                copied.transform().fracXBits()); // Must retain stored rounding.
        assertEquals(actual.getChunkCoord().getX(), copied.sectionX());
        assertEquals(actual.getBlockCoord().getX(), copied.blockX());
    }

    private static final class UnknownFrustum implements Frustum {
        @Override public boolean testAab(float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ) {
            fail("Input capture must not probe frustum behavior");
            return false;
        }
    }

    @Test void identityTokensIgnoreValueEqualityAndEachOwnerMatters() {
        assertNotEquals(Identity.of(new String("same")), Identity.of(new String("same")));
        for (int i = 0; i < owners.length; i++) {
            Object[] changed = owners.clone(); changed[i] = new Object();
            assertNotEquals(key(), capture(camera(), viewport(), traversal(),
                    lifecycle(changed, 1, true, ProjectionSelection.VANILLA), frustum()).key(), "owner " + i);
        }
        assertNotEquals(key(), capture(camera(), viewport(), traversal(),
                lifecycle(owners, 2, true, ProjectionSelection.VANILLA), frustum()).key());
    }

    @Test void searchModeSpectatorAndWorldRangeChangeTheKey() {
        TraversalState[] changes = {
                TraversalState.effective(Math.nextUp(192f), false, false, -4, 20, -1, 4, 0, true, false, false),
                TraversalState.effective(192, true, false, -4, 20, -1, 4, 0, true, false, false),
                TraversalState.effective(192, false, true, -4, 20, -1, 4, 0, true, false, false),
                TraversalState.effective(192, false, false, -5, 20, -1, 4, 0, true, false, false),
                TraversalState.effective(192, false, false, -4, 21, -1, 4, 0, true, false, false),
                TraversalState.effective(192, false, false, -4, 20, -1, 4, 0, true, true, false),
                TraversalState.effective(192, false, false, -4, 20, -1, 4, 0, true, false, true)
        };
        for (TraversalState changed : changes)
            assertNotEquals(key(), capture(camera(), viewport(), changed, lifecycle(), frustum()).key());
    }

    @Test void sortOriginAndViewportBlockCoordinatesAreCompared() {
        ViewportState moved = new ViewportState(transform(), 0, 4, 0, -2, 64, 0);
        TraversalState matchingSort = TraversalState.effective(192, false, false, -4, 20,
                0, 4, 0, true, false, false);
        assertNotEquals(key(), capture(camera(), moved, matchingSort, lifecycle(), frustum()).key());
        refused(capture(camera(), moved, traversal(), lifecycle(), frustum()), Reason.SORT_ORIGIN_MISMATCH);
        assertNotEquals(key(), capture(camera(), new ViewportState(transform(), -1, 4, 0, -3, 64, 0),
                traversal(), lifecycle(), frustum()).key());
    }

    @Test void primaryAndReversedDistanceConstraintsRemainDistinct() {
        BoxState box = BoxState.stored(192, -193.25, 190.75, -127.5, 256.5, -192, 192);
        FrustumState ordinary = frustum(new float[][]{{1, 0, -0.0f, 2}}, false, box);
        FrustumState reversed = FrustumState.stored(FrustumKind.REVERSED_ADVANCED, -1.25, 64.5, 0,
                false, new float[][]{{1, 0, -0.0f, 2}}, 1, box, BoxState.absent());
        assertNotEquals(capture(ordinary).key(), capture(reversed).key());
        FrustumState reversedDistance = FrustumState.stored(FrustumKind.REVERSED_ADVANCED, -1.25, 64.5, 0,
                false, new float[][]{{1, 0, -0.0f, 2}}, 1, box, box);
        assertNotEquals(capture(reversed).key(), capture(reversedDistance).key());
        assertNotEquals(capture(ordinary).key(), capture(frustum(new float[][]{{1, 0, -0.0f, 2}}, false,
                BoxState.stored(Math.nextUp(192.0), -193.25, 190.75, -127.5, 256.5, -192, 192))).key());
        assertNotEquals(capture(ordinary).key(), capture(frustum(new float[][]{{1, 0, -0.0f, 2}}, false,
                BoxState.stored(192, Math.nextUp(-193.25), 190.75, -127.5, 256.5, -192, 192))).key());
    }

    @Test void nonfiniteValuesNeverSupplyAKey() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            refused(capture(CameraState.stored(value, 0, 0, 0, 0, 0), viewport(), traversal(),
                    lifecycle(), frustum()), Reason.NONFINITE_CAMERA);
            refused(capture(FrustumState.stored(FrustumKind.ADVANCED, value, 0, 0, false,
                    new float[][]{{1, 0, 0, 2}}, 1, BoxState.absent(), BoxState.absent())), Reason.NONFINITE_FRUSTUM);
        }
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            refused(capture(frustum(new float[][]{{value, 0, 0, 2}}, false, BoxState.absent())),
                    Reason.NONFINITE_FRUSTUM);
            refused(capture(camera(), viewport(), TraversalState.effective(value, false, false, -4,
                    20, -1, 4, 0, true, false, false), lifecycle(), frustum()), Reason.INVALID_SEARCH_DISTANCE);
            refused(capture(camera(), new ViewportState(StoredTransform.stored(0, 0, 0, value, 0, 0,
                    0, 0, 0), -1, 4, 0, 0, 0, 0), traversal(), lifecycle(), frustum()), Reason.NONFINITE_TRANSFORM);
        }
    }

    @Test void unsupportedAndMalformedFrustaRemainUncertified() {
        refused(capture(ShadowTraversalViewportAccessor.captureFrustum(new UnknownFrustum())),
                Reason.UNSUPPORTED_FRUSTUM);
        refused(capture(FrustumState.unknown(Object.class.getName())), Reason.UNSUPPORTED_FRUSTUM);
        refused(capture(FrustumState.unknown("net.irisshaders.iris.shadows.frustum.NonCullingFrustum")),
                Reason.UNSUPPORTED_FRUSTUM);
        refused(capture(FrustumState.stored(FrustumKind.ADVANCED, 0, 0, 0, false, new float[][]{{1}},
                1, BoxState.absent(), BoxState.absent())), Reason.INCOMPLETE_FRUSTUM);
        refused(capture(FrustumState.stored(FrustumKind.ADVANCED, 0, 0, 0, false, new float[14][4],
                14, BoxState.absent(), BoxState.absent())), Reason.INCOMPLETE_FRUSTUM);
        refused(capture(FrustumState.stored(FrustumKind.ADVANCED, 0, 0, 0, false, null,
                0, BoxState.absent(), BoxState.absent())), Reason.INCOMPLETE_FRUSTUM);
        refused(capture(frustum(new float[][]{{1, 0, 0, 2}}, false,
                BoxState.stored(Double.POSITIVE_INFINITY, 0, 0, 0, 0, 0, 0))), Reason.INVALID_BOX);
        refused(capture(frustum(new float[][]{{1, 0, 0, 2}}, false,
                BoxState.stored(1, 2, 1, 0, 0, 0, 0))), Reason.INVALID_BOX);
    }

    @Test void missingLifecycleProjectionAndMainPassCannotBeCertified() {
        refused(capture(camera(), viewport(), traversal(), lifecycle(owners, 1, false,
                ProjectionSelection.VANILLA), frustum()), Reason.UNKNOWN_LIFECYCLE);
        Object[] missing = owners.clone(); missing[5] = null;
        refused(capture(camera(), viewport(), traversal(), lifecycle(missing, 1, true,
                ProjectionSelection.VANILLA), frustum()), Reason.UNKNOWN_LIFECYCLE);
        refused(capture(camera(), viewport(), traversal(), lifecycle(owners, 1, true,
                ProjectionSelection.UNKNOWN), frustum()), Reason.UNKNOWN_PROJECTION);
        assertNotEquals(key(), capture(camera(), viewport(), traversal(), lifecycle(owners, 1, true,
                ProjectionSelection.DISTANT_HORIZONS), frustum()).key());
        refused(capture(camera(), viewport(), TraversalState.effective(192, false, false, -4, 20,
                -1, 4, 0, false, false, false), lifecycle(), frustum()), Reason.NOT_SHADOW_PASS);
        refused(capture(null, viewport(), traversal(), lifecycle(), frustum()), Reason.MISSING_INPUT);
    }
}
