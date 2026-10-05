package com.pp.shaderlod.dev.timing;

import java.util.Map;

/** Standalone observer checks: no Minecraft/Iris/JOML/OpenGL required. */
public final class ShadowObservationPolicyTest {
    private static final Object WORLD = new Object(), RENDERER = new Object(), CAMERA = new Object();
    private static final Map<String, String> STATIC_FEATURES = Map.of(
            "WAVING_PLANTS", "false", "WAVING_LEAVES", "false", "WATER_DISPLACEMENT", "false",
            "WATER_CAUSTICS", "false", "COLORED_LIGHTS", "false");

    private static ShadowInputObserver.Accumulator engine() {
        var engine = new ShadowInputObserver.Accumulator();
        engine.configureFeatures(STATIC_FEATURES);
        return engine;
    }
    private static void begin(ShadowInputObserver.Accumulator engine, long frame, Object world,
                              double cameraX, float yaw, long time, long tick, float animation) {
        engine.beginFrame(frame, world, RENDERER, CAMERA, cameraX, 95, 0, yaw, 9,
                time, tick, animation, 2048, false, false, false, false, false);
    }
    private static void matrix(ShadowInputObserver.Accumulator engine, long frame,
                               boolean consumer, boolean projection, float translation) {
        engine.matrix(frame, consumer, projection,
                1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, translation, 0, 0, 1);
    }
    private static void complete(ShadowInputObserver.Accumulator engine, long frame, float translation) {
        matrix(engine, frame, false, false, translation); matrix(engine, frame, false, true, 0);
        matrix(engine, frame, true, false, translation); matrix(engine, frame, true, true, 0);
        engine.endRender(frame, 0, 0);
    }
    private static long change(ShadowInputObserver.Snapshot snapshot, ShadowInputObserver.Change reason) {
        return snapshot.changeCounts().get(reason.name());
    }
    private static long block(ShadowInputObserver.Snapshot snapshot, ShadowInputObserver.Blocker reason) {
        return snapshot.blockerCounts().get(reason.name());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-6, message + ": " + actual);
    }

    public static void main(String[] arguments) {
        check(!ShadowInputObserver.enabled(), "observer must default off");
        ShadowInputObserver.beginFrame(1, WORLD, RENDERER, CAMERA, 0, 95, 0, 135, 9,
                7000, 42, 10, 2048, false, false, false, false, false);
        check(!ShadowInputObserver.claimConsumerMatrix(1, false)
                && ShadowInputObserver.snapshot().completedFrames() == 0, "disabled entrypoints recorded work");
        check(ShadowInputObserver.snapshot() == ShadowInputObserver.snapshot(), "disabled snapshot allocated per call");
        var exact = engine();
        begin(exact, 1, WORLD, 0, 135, 7000, 42, 10); complete(exact, 1, 0);
        begin(exact, 2, WORLD, 0, 135, 7000, 42, 10); complete(exact, 2, 0);
        begin(exact, 3, WORLD, 0, 135, 7000, 42, 10);
        var snapshot = exact.snapshot(true);
        check(snapshot.completedFrames() == 2 && snapshot.comparablePairs() == 1
                && snapshot.observedEqualPairs() == 1, "identical observed inputs not counted");
        check(snapshot.exactReusableFrames() == 0, "matching inputs incorrectly certified reuse");
        check(block(snapshot, ShadowInputObserver.Blocker.TERRAIN_AND_MESH_REVISIONS_UNOBSERVED) == 2
                && block(snapshot, ShadowInputObserver.Blocker.SHADOW_CALLBACKS_UNAUDITED) == 2,
                "unknown input blockers must remain even for exact observed equality");
        near(snapshot.projectedReferenceDeltaTexelsMean(), 0, "stationary reference delta");

        var moving = engine();
        begin(moving, 1, WORLD, 0, 135, 7000, 42, 10); complete(moving, 1, 0);
        // Correct camera-relative translation cancels a one-block camera origin shift.
        begin(moving, 2, WORLD, 1, 135, 7000, 42, 10); complete(moving, 2, 1);
        begin(moving, 3, WORLD, 1, 135, 7000, 42, 10);
        snapshot = moving.snapshot(true);
        near(snapshot.projectedReferenceDeltaTexelsMax(), 0, "world-anchored projection comparison");
        check(change(snapshot, ShadowInputObserver.Change.CAMERA_POSITION_CHANGED) == 1
                && change(snapshot, ShadowInputObserver.Change.RENDER_MATRIX_CHANGED) == 1
                && snapshot.observedEqualPairs() == 0, "moving inputs falsely equal");

        var changed = engine();
        begin(changed, 1, WORLD, 0, 135, 7000, 42, 10); complete(changed, 1, 0);
        begin(changed, 2, WORLD, 0, 136, 7001, 43, 10.01f); complete(changed, 2, 1);
        changed.endRender(2, 2, 1);
        matrix(changed, 2, true, false, 0); // Independently regenerated consumer mismatch.
        begin(changed, 3, WORLD, 0, 136, 7001, 43, 10.01f);
        snapshot = changed.snapshot(true);
        for (var reason : new ShadowInputObserver.Change[] {
                ShadowInputObserver.Change.CAMERA_POSE_CHANGED, ShadowInputObserver.Change.WORLD_TIME_CHANGED,
                ShadowInputObserver.Change.GAME_TICK_CHANGED, ShadowInputObserver.Change.ANIMATION_CLOCK_CHANGED,
                ShadowInputObserver.Change.CASTER_COUNTS_CHANGED, ShadowInputObserver.Change.OBSERVED_CASTERS_PRESENT,
                ShadowInputObserver.Change.RENDER_CONSUMER_MATRIX_DISAGREEMENT })
            check(change(snapshot, reason) == 1, "missing independent input reason: " + reason);
        near(snapshot.projectedReferenceDeltaTexelsMax(), 1024, "projection-to-texel scale");

        var rotating = engine();
        begin(rotating, 1, WORLD, 0, 135, 7000, 42, 10); complete(rotating, 1, 0);
        begin(rotating, 2, WORLD, 0, 135, 7000, 42, 10); complete(rotating, 2, 0);
        rotating.matrix(2, false, false, 0.999f, 0.001f, 0, 0, -0.001f, 0.999f, 0, 0,
                0, 0, 1, 0, 0, 0, 0, 1);
        begin(rotating, 3, WORLD, 0, 135, 7000, 42, 10);
        check(change(rotating.snapshot(true), ShadowInputObserver.Change.LIGHT_ORIENTATION_MATRIX_CHANGED) == 1,
                "light rotation change must be separate from camera translation");

        var reset = engine();
        begin(reset, 1, WORLD, 0, 135, 7000, 42, 10); complete(reset, 1, 0);
        begin(reset, 2, new Object(), 0, 135, 7000, 42, 10); complete(reset, 2, 0);
        begin(reset, 4, WORLD, 0, 135, 7000, 42, 10); complete(reset, 4, 0);
        begin(reset, 5, WORLD, 0, 135, 7000, 42, 10);
        snapshot = reset.snapshot(true);
        check(snapshot.comparablePairs() == 0 && change(snapshot, ShadowInputObserver.Change.NONCONSECUTIVE_FRAME) == 1,
                "world change/gap was compared as consecutive valid data");
        reset.beginMeasurement();
        begin(reset, 6, WORLD, 0, 135, 7000, 42, 10); complete(reset, 6, 0);
        begin(reset, 7, WORLD, 0, 135, 7000, 42, 10); complete(reset, 7, 0);
        begin(reset, 8, WORLD, 0, 135, 7000, 42, 10);
        snapshot = reset.snapshot(true);
        check(snapshot.completedFrames() == 2 && snapshot.comparablePairs() == 1,
                "measurement included pending warmup frame or lost new samples");

        var missing = engine();
        begin(missing, 1, WORLD, 0, 135, 7000, 42, 10);
        // Renderer early-return: no completed geometry, so old Java static matrices cannot certify it.
        begin(missing, 2, WORLD, 0, 135, 7000, 42, 10); complete(missing, 2, Float.NaN);
        begin(missing, 3, WORLD, 0, 135, 7000, 42, 10);
        snapshot = missing.snapshot(true);
        check(change(snapshot, ShadowInputObserver.Change.MISSING_RENDER_COMPLETION) == 1
                && change(snapshot, ShadowInputObserver.Change.NONFINITE_INPUT) == 1
                && snapshot.comparablePairs() == 0, "missing/nonfinite data failed open");

        var features = new ShadowInputObserver.Accumulator();
        features.configureFeatures(Map.of("WAVING_PLANTS", "true", "COLORED_LIGHTS", "true"));
        features.beginFrame(1, WORLD, RENDERER, CAMERA, 0, 95, 0, 135, 9, 7000, 42, 10,
                2048, false, true, false, false, true);
        check(features.claimConsumerMatrix(1, false) && !features.claimConsumerMatrix(1, false)
                && features.claimConsumerMatrix(1, true) && !features.claimConsumerMatrix(1, true)
                && !features.claimConsumerMatrix(99, false), "supplier return gating incorrect");
        complete(features, 1, 0);
        begin(features, 2, WORLD, 0, 135, 7000, 42, 10);
        snapshot = features.snapshot(true);
        check(snapshot.originalSupplierReturnCalls() == 4 && !snapshot.featureFlagsKnown(), "missing flags treated as known");
        check(block(snapshot, ShadowInputObserver.Blocker.CASTER_POSES_UNOBSERVED) == 1
                && block(snapshot, ShadowInputObserver.Blocker.WAVING_OR_WATER_DISPLACEMENT_ENABLED) == 1
                && block(snapshot, ShadowInputObserver.Blocker.COLORED_LIGHTS_ENABLED) == 1,
                "player/waving/voxel blockers not retained");
        check(snapshot.pendingFrameExcluded() == 2, "snapshot should not finalize or mutate pending frame");
        System.out.println("Shadow observation equality, projection, isolation and fail-closed checks passed");
    }
}
