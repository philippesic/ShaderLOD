package com.pp.shaderlod.dev.timing;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Development diagnostics only: never authorizes reuse or changes rendering. Client thread only. */
public final class ShadowInputObserver {
    private static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.shadowObserve");
    private static final Accumulator OBSERVER = new Accumulator();
    private static final Snapshot DISABLED = OBSERVER.snapshot(false);

    private ShadowInputObserver() {}
    public static boolean enabled() { return ENABLED; }

    /** Call once after obtaining the effective option map; missing/invalid feature flags remain unknown. */
    public static void configurePhotonFeatures(Map<String, String> effectiveOptions) {
        if (ENABLED) OBSERVER.configureFeatures(effectiveOptions);
    }

    public static void beginMeasurement() { if (ENABLED) OBSERVER.beginMeasurement(); }
    public static Snapshot snapshot() { return ENABLED ? OBSERVER.snapshot(true) : DISABLED; }

    public static void beginFrame(long frame, Object world, Object renderer, Object camera,
                                  double x, double y, double z, float yaw, float pitch,
                                  long dayTime, long gameTime, float frameTimeCounter,
                                  int resolution, boolean entities, boolean player,
                                  boolean blockEntities, boolean lightBlockEntities, boolean voxelization) {
        if (ENABLED) OBSERVER.beginFrame(frame, world, renderer, camera, x, y, z, yaw, pitch,
                dayTime, gameTime, frameTimeCounter, resolution, entities, player, blockEntities,
                lightBlockEntities, voxelization);
    }

    /** Observe at most one original supplier return per matrix kind/frame, never invoke it again. */
    public static boolean claimConsumerMatrix(long frame, boolean projection) {
        return ENABLED && OBSERVER.claimConsumerMatrix(frame, projection);
    }

    public static void matrix(long frame, boolean consumer, boolean projection,
                              float m00, float m01, float m02, float m03,
                              float m10, float m11, float m12, float m13,
                              float m20, float m21, float m22, float m23,
                              float m30, float m31, float m32, float m33) {
        if (ENABLED) OBSERVER.matrix(frame, consumer, projection, m00, m01, m02, m03,
                m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33);
    }

    public static void endRender(long frame, int entities, int blockEntities) {
        if (ENABLED) OBSERVER.endRender(frame, entities, blockEntities);
    }

    public enum Change {
        FIRST_COMPARABLE_FRAME, WORLD_OR_RENDERER_CHANGED, CAMERA_ENTITY_CHANGED,
        CAMERA_POSITION_CHANGED, CAMERA_POSE_CHANGED, WORLD_TIME_CHANGED, GAME_TICK_CHANGED,
        ANIMATION_CLOCK_CHANGED, RENDER_MATRIX_CHANGED, CONSUMER_MATRIX_CHANGED,
        LIGHT_ORIENTATION_MATRIX_CHANGED,
        CASTER_COUNTS_CHANGED, OBSERVED_CASTERS_PRESENT, VOXELIZATION_PRESENT,
        RENDER_CONSUMER_MATRIX_DISAGREEMENT,
        MISSING_RENDER_COMPLETION, MISSING_RENDER_MATRICES, MISSING_CONSUMER_MATRICES,
        NONFINITE_INPUT, NONCONSECUTIVE_FRAME
    }
    public enum Blocker {
        TERRAIN_AND_MESH_REVISIONS_UNOBSERVED, MATERIAL_AND_TEXTURE_REVISIONS_UNOBSERVED,
        SHADOW_CALLBACKS_UNAUDITED, VIEW_AND_CASTER_VISIBILITY_UNOBSERVED,
        SHADER_WEATHER_INPUTS_UNOBSERVED, CASTER_POSES_UNOBSERVED, FEATURE_FLAGS_UNKNOWN,
        WAVING_OR_WATER_DISPLACEMENT_ENABLED, WATER_CAUSTICS_ENABLED, COLORED_LIGHTS_ENABLED
    }

    /** Pure engine enables standalone policy tests without Minecraft, Iris, JOML or OpenGL. */
    public static final class Accumulator {
        private final float[] model = new float[16], projection = new float[16];
        private final float[] consumerModel = new float[16], consumerProjection = new float[16];
        private final float[] previousModel = new float[16], previousProjection = new float[16];
        private final float[] previousConsumerModel = new float[16], previousConsumerProjection = new float[16];
        private final float[] clip = new float[16], previousClip = new float[16];
        private final long[] changes = new long[Change.values().length], blockers = new long[Blocker.values().length];
        private final Change[] changeNames = Change.values();
        private final Blocker[] blockerNames = Blocker.values();
        private boolean pending, inWindow = true, previous, rendered, haveModel, haveProjection,
                haveConsumerModel, haveConsumerProjection, claimedConsumerModel, claimedConsumerProjection;
        private Object world, renderer, camera, previousWorld, previousRenderer, previousCamera;
        private double x, y, z, previousX, previousY, previousZ;
        private float yaw, pitch, animation, previousYaw, previousPitch, previousAnimation;
        private long frame, dayTime, gameTime, previousFrame, previousDayTime, previousGameTime;
        private int resolution, casterEntities, casterBlockEntities, previousEntities, previousBlockEntities;
        private boolean renderEntities, renderPlayer, renderBlockEntities, renderLightBlockEntities, voxelization;
        private boolean featureFlagsKnown, waving, waterCaustics, coloredLights;
        private long frames, comparisons, observedEqual, supplierCalls, matrixSamples, proxySamples,
                belowQuarterTexel, belowOneTexel, possibleTwoFrameCadenceFrames;
        private double proxySum, proxyMax;
        private long frameIntervalNanos, previousBeginNanos;
        private long engineCalls, engineNs, engineMaxNs;

        public void configureFeatures(Map<String, String> options) {
            Boolean plants = flag(options, "WAVING_PLANTS"), leaves = flag(options, "WAVING_LEAVES"),
                    water = flag(options, "WATER_DISPLACEMENT"), caustics = flag(options, "WATER_CAUSTICS"),
                    colored = flag(options, "COLORED_LIGHTS");
            featureFlagsKnown = plants != null && leaves != null && water != null && caustics != null && colored != null;
            waving = Boolean.TRUE.equals(plants) || Boolean.TRUE.equals(leaves) || Boolean.TRUE.equals(water);
            waterCaustics = Boolean.TRUE.equals(caustics);
            coloredLights = Boolean.TRUE.equals(colored);
        }

        private static Boolean flag(Map<String, String> values, String key) {
            String value = values.get(key);
            return "true".equals(value) ? Boolean.TRUE : "false".equals(value) ? Boolean.FALSE : null;
        }

        public void beginMeasurement() {
            Arrays.fill(changes, 0); Arrays.fill(blockers, 0);
            frames = comparisons = observedEqual = supplierCalls = matrixSamples = proxySamples = 0;
            belowQuarterTexel = belowOneTexel = possibleTwoFrameCadenceFrames = 0;
            proxySum = proxyMax = 0; engineCalls = engineNs = engineMaxNs = 0;
            inWindow = false; previous = false;
        }

        public void beginFrame(long frame, Object world, Object renderer, Object camera,
                               double x, double y, double z, float yaw, float pitch,
                               long dayTime, long gameTime, float animation, int resolution,
                               boolean entities, boolean player, boolean blockEntities,
                               boolean lightBlockEntities, boolean voxelization) {
            long started = System.nanoTime();
            if (pending) finish();
            this.frameIntervalNanos = previousBeginNanos == 0 ? 0 : started - previousBeginNanos;
            this.previousBeginNanos = started;
            this.frame = frame; this.world = world; this.renderer = renderer; this.camera = camera;
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
            this.dayTime = dayTime; this.gameTime = gameTime; this.animation = animation;
            this.resolution = resolution; this.renderEntities = entities; this.renderPlayer = player;
            this.renderBlockEntities = blockEntities; this.renderLightBlockEntities = lightBlockEntities;
            this.voxelization = voxelization;
            pending = inWindow = true; rendered = haveModel = haveProjection = false;
            haveConsumerModel = haveConsumerProjection = claimedConsumerModel = claimedConsumerProjection = false;
            casterEntities = casterBlockEntities = 0;
            recordEngineCost(started);
        }

        public boolean claimConsumerMatrix(long frame, boolean projection) {
            if (!pending || this.frame != frame) return false;
            if (inWindow) supplierCalls++;
            if (projection ? claimedConsumerProjection : claimedConsumerModel) return false;
            if (projection) claimedConsumerProjection = true; else claimedConsumerModel = true;
            return true;
        }

        public void matrix(long frame, boolean consumer, boolean projection,
                           float m00, float m01, float m02, float m03,
                           float m10, float m11, float m12, float m13,
                           float m20, float m21, float m22, float m23,
                           float m30, float m31, float m32, float m33) {
            if (!pending || this.frame != frame) return;
            float[] target = consumer ? (projection ? consumerProjection : consumerModel) : (projection ? this.projection : model);
            target[0] = m00; target[1] = m01; target[2] = m02; target[3] = m03;
            target[4] = m10; target[5] = m11; target[6] = m12; target[7] = m13;
            target[8] = m20; target[9] = m21; target[10] = m22; target[11] = m23;
            target[12] = m30; target[13] = m31; target[14] = m32; target[15] = m33;
            if (consumer) {
                if (projection) haveConsumerProjection = true; else haveConsumerModel = true;
            } else {
                if (projection) haveProjection = true; else haveModel = true;
            }
            if (inWindow) matrixSamples++;
        }

        public void endRender(long frame, int entities, int blockEntities) {
            if (!pending || this.frame != frame) return;
            rendered = true; casterEntities = entities; casterBlockEntities = blockEntities;
        }

        private void finish() {
            if (!inWindow) return;
            frames++;
            boolean finite = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Float.isFinite(yaw) && Float.isFinite(pitch) && Float.isFinite(animation)
                    && finite(model, haveModel) && finite(projection, haveProjection)
                    && finite(consumerModel, haveConsumerModel) && finite(consumerProjection, haveConsumerProjection);
            boolean complete = rendered && haveModel && haveProjection && haveConsumerModel && haveConsumerProjection;
            count(Change.MISSING_RENDER_COMPLETION, !rendered);
            count(Change.MISSING_RENDER_MATRICES, !haveModel || !haveProjection);
            count(Change.MISSING_CONSUMER_MATRICES, !haveConsumerModel || !haveConsumerProjection);
            count(Change.NONFINITE_INPUT, !finite);
            count(Change.OBSERVED_CASTERS_PRESENT, casterEntities > 0 || casterBlockEntities > 0);
            count(Change.VOXELIZATION_PRESENT, voxelization);
            count(Change.RENDER_CONSUMER_MATRIX_DISAGREEMENT, haveModel && haveProjection
                    && haveConsumerModel && haveConsumerProjection && (!Arrays.equals(model, consumerModel)
                    || !Arrays.equals(projection, consumerProjection)));
            // No observer API can accidentally certify unknown terrain/texture/callback inputs.
            block(Blocker.TERRAIN_AND_MESH_REVISIONS_UNOBSERVED, true);
            block(Blocker.MATERIAL_AND_TEXTURE_REVISIONS_UNOBSERVED, true);
            block(Blocker.SHADOW_CALLBACKS_UNAUDITED, true);
            block(Blocker.VIEW_AND_CASTER_VISIBILITY_UNOBSERVED, true);
            block(Blocker.SHADER_WEATHER_INPUTS_UNOBSERVED, !featureFlagsKnown || waving || waterCaustics);
            block(Blocker.CASTER_POSES_UNOBSERVED, renderEntities || renderPlayer || renderBlockEntities || renderLightBlockEntities);
            block(Blocker.FEATURE_FLAGS_UNKNOWN, !featureFlagsKnown);
            block(Blocker.WAVING_OR_WATER_DISPLACEMENT_ENABLED, waving);
            block(Blocker.WATER_CAUSTICS_ENABLED, waterCaustics);
            block(Blocker.COLORED_LIGHTS_ENABLED, coloredLights || voxelization);
            boolean sameIdentity = previous && world == previousWorld && renderer == previousRenderer;
            boolean consecutive = previous && frame == previousFrame + 1;
            count(Change.FIRST_COMPARABLE_FRAME, !previous);
            count(Change.WORLD_OR_RENDERER_CHANGED, previous && !sameIdentity);
            count(Change.CAMERA_ENTITY_CHANGED, previous && camera != previousCamera);
            count(Change.NONCONSECUTIVE_FRAME, previous && !consecutive);
            if (previous) {
                boolean samePosition = x == previousX && y == previousY && z == previousZ;
                boolean samePose = yaw == previousYaw && pitch == previousPitch;
                boolean sameTime = dayTime == previousDayTime;
                boolean sameTick = gameTime == previousGameTime;
                boolean sameAnimation = animation == previousAnimation;
                boolean sameRenderMatrix = Arrays.equals(model, previousModel) && Arrays.equals(projection, previousProjection);
                boolean sameConsumerMatrix = Arrays.equals(consumerModel, previousConsumerModel)
                        && Arrays.equals(consumerProjection, previousConsumerProjection);
                boolean sameCasters = casterEntities == previousEntities && casterBlockEntities == previousBlockEntities;
                count(Change.CAMERA_POSITION_CHANGED, !samePosition); count(Change.CAMERA_POSE_CHANGED, !samePose);
                count(Change.WORLD_TIME_CHANGED, !sameTime); count(Change.GAME_TICK_CHANGED, !sameTick);
                count(Change.ANIMATION_CLOCK_CHANGED, !sameAnimation); count(Change.RENDER_MATRIX_CHANGED, !sameRenderMatrix);
                count(Change.CONSUMER_MATRIX_CHANGED, !sameConsumerMatrix); count(Change.CASTER_COUNTS_CHANGED, !sameCasters);
                count(Change.LIGHT_ORIENTATION_MATRIX_CHANGED, !sameRotation(model, previousModel));
                if (sameIdentity && consecutive && complete && finite) {
                    comparisons++;
                    if (camera == previousCamera && samePosition && samePose && sameTime && sameTick
                            && sameAnimation && sameRenderMatrix && sameConsumerMatrix && sameCasters) observedEqual++;
                    double delta = projectedDelta();
                    if (Double.isFinite(delta)) {
                        proxySamples++; proxySum += delta; proxyMax = Math.max(proxyMax, delta);
                        if (delta <= 0.25) belowQuarterTexel++;
                        if (delta <= 1.0) belowOneTexel++;
                        if (delta <= 0.25 && frameIntervalNanos > 0 && frameIntervalNanos <= 8_333_333L)
                            possibleTwoFrameCadenceFrames++;
                    }
                }
            }
            previous = complete && finite && world != null && renderer != null && camera != null;
            previousWorld = world; previousRenderer = renderer; previousCamera = camera;
            previousX = x; previousY = y; previousZ = z; previousYaw = yaw; previousPitch = pitch;
            previousDayTime = dayTime; previousGameTime = gameTime; previousAnimation = animation; previousFrame = frame;
            previousEntities = casterEntities; previousBlockEntities = casterBlockEntities;
            System.arraycopy(model, 0, previousModel, 0, 16); System.arraycopy(projection, 0, previousProjection, 0, 16);
            System.arraycopy(consumerModel, 0, previousConsumerModel, 0, 16);
            System.arraycopy(consumerProjection, 0, previousConsumerProjection, 0, 16);
        }

        private void count(Change name, boolean yes) { if (yes) changes[name.ordinal()]++; }
        private void block(Blocker name, boolean yes) { if (yes) blockers[name.ordinal()]++; }
        private static boolean sameRotation(float[] first, float[] second) {
            for (int column = 0; column < 3; column++) for (int row = 0; row < 3; row++) {
                if (first[column * 4 + row] != second[column * 4 + row]) return false;
            }
            return true;
        }
        private static boolean finite(float[] values, boolean present) {
            if (!present) return true;
            for (int i = 0; i < 16; i++) if (!Float.isFinite(values[i])) return false;
            return true;
        }

        /** Seven fixed world-space anchors; no Photon distortion or caster/coverage quality guarantee. */
        private double projectedDelta() {
            if (resolution <= 0) return Double.NaN;
            multiply(projection, model, clip); multiply(previousProjection, previousModel, previousClip);
            double max = 0;
            for (int i = 0; i < 7; i++) {
                double ax = i == 1 ? 32 : i == 2 ? -32 : 0;
                double ay = i == 3 ? 16 : i == 4 ? -16 : 0;
                double az = i == 5 ? 32 : i == 6 ? -32 : 0;
                double bx = ax + previousX - x, by = ay + previousY - y, bz = az + previousZ - z;
                double oldW = component(previousClip, 3, ax, ay, az), newW = component(clip, 3, bx, by, bz);
                if (!Double.isFinite(oldW) || !Double.isFinite(newW) || Math.abs(oldW) < 1e-9 || Math.abs(newW) < 1e-9)
                    return Double.NaN;
                for (int row = 0; row < 2; row++) {
                    double difference = Math.abs(component(previousClip, row, ax, ay, az) / oldW
                            - component(clip, row, bx, by, bz) / newW) * resolution * 0.5;
                    if (!Double.isFinite(difference)) return Double.NaN;
                    max = Math.max(max, difference);
                }
            }
            return max;
        }
        private static double component(float[] matrix, int row, double x, double y, double z) {
            return matrix[row] * x + matrix[4 + row] * y + matrix[8 + row] * z + matrix[12 + row];
        }
        private static void multiply(float[] left, float[] right, float[] result) {
            for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++) {
                result[column * 4 + row] = left[row] * right[column * 4] + left[4 + row] * right[column * 4 + 1]
                        + left[8 + row] * right[column * 4 + 2] + left[12 + row] * right[column * 4 + 3];
            }
        }
        private void recordEngineCost(long started) {
            if (!inWindow) return;
            long cost = Math.max(0, System.nanoTime() - started);
            engineCalls++; engineNs += cost; engineMaxNs = Math.max(engineMaxNs, cost);
        }

        /** Completed frames only; the pending frame is deliberately excluded rather than mutated. */
        public Snapshot snapshot(boolean enabled) {
            Map<String, Long> changeCounts = new LinkedHashMap<>(), blockerCounts = new LinkedHashMap<>();
            for (int i = 0; i < changes.length; i++) changeCounts.put(changeNames[i].name(), changes[i]);
            for (int i = 0; i < blockers.length; i++) blockerCounts.put(blockerNames[i].name(), blockers[i]);
            return new Snapshot(enabled, frames, comparisons, observedEqual, comparisons == 0 ? 0 : (double) observedEqual / comparisons,
                    0, "UNVERIFIED_INPUTS; observation never certifies map reuse", Map.copyOf(changeCounts), Map.copyOf(blockerCounts),
                    featureFlagsKnown, waving, waterCaustics, coloredLights, supplierCalls, matrixSamples,
                    proxySamples, proxySamples == 0 ? null : proxySum / proxySamples,
                    proxySamples == 0 ? null : proxyMax, belowQuarterTexel, belowOneTexel, possibleTwoFrameCadenceFrames,
                    "Seven static-world reference anchors before Photon distortion; no silhouette/coverage guarantee",
                    engineCalls == 0 ? null : engineNs / (double) engineCalls / 1_000_000.0,
                    engineCalls == 0 ? null : engineMaxNs / 1_000_000.0,
                    "Engine begin/finalize only; excludes mixin input reads and supplier/matrix-record overhead", pending ? frame : null);
        }
    }

    public record Snapshot(boolean enabled, long completedFrames, long comparablePairs, long observedEqualPairs,
                           double observedInputEqualityFraction, long exactReusableFrames, String exactEligibility,
                           Map<String, Long> changeCounts, Map<String, Long> blockerCounts,
                           boolean featureFlagsKnown, boolean wavingOrWaterDisplacement, boolean waterCaustics, boolean coloredLights,
                           long originalSupplierReturnCalls, long matrixSamples, long projectedProxySamples,
                           Double projectedReferenceDeltaTexelsMean, Double projectedReferenceDeltaTexelsMax,
                           long projectedProxyAtMostQuarterTexel, long projectedProxyAtMostOneTexel,
                           long potentialTwoFrameCadencePairs, String projectedProxyScope,
                           Double engineBeginFinalizeMeanMs, Double engineBeginFinalizeMaxMs,
                           String overheadScope, Long pendingFrameExcluded) {}
}
