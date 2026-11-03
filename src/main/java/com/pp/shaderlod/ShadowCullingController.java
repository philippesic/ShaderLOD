package com.pp.shaderlod;

/** Client-tick eligibility/configuration cache; the AABB path only reads active. */
public final class ShadowCullingController {
    private static boolean configured;
    private static boolean eligible;
    private static volatile boolean active;
    private static volatile long flagsGeneration = -1;
    private static volatile long lifecycleGeneration = -1;

    private ShadowCullingController() {}

    /** Call on the client thread when global/feature configuration changes. */
    public static void configure(boolean enabled) {
        configured = enabled;
        active = configured && eligible; flagsGeneration=com.pp.shaderlod.optimization.OptimizationConfig.generation(); lifecycleGeneration=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch();
    }

    /** Caller owns world/pipeline/Photon eligibility and clears this on invalidation. */
    public static void setEligible(boolean value) {
        eligible = value;
        active = configured && eligible; flagsGeneration=com.pp.shaderlod.optimization.OptimizationConfig.generation(); lifecycleGeneration=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch();
    }

    /** No config, Iris, GL, reflection, allocation or per-call world inspection. */
    public static boolean isActive() { return active && lifecycleGeneration==com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch() && !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended() && flagsGeneration==com.pp.shaderlod.optimization.OptimizationConfig.generation() && com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.SHADOW_POSITIVE_VERTEX); }

    /** Implemented on the base Iris frustum; inherited by its reversed subclass. */
    public interface PlaneAccess {
        boolean shaderlod$coherentAccept(boolean reversed, double distanceLimit,
                                        float minX, float minY, float minZ, float maxX, float maxY, float maxZ);
        boolean shaderlod$testPositiveVertices(float minX, float minY, float minZ,
                                              float maxX, float maxY, float maxZ);
        boolean shaderlod$testOriginalVertices(float minX, float minY, float minZ,
                                              float maxX, float maxY, float maxZ);
        boolean shaderlod$acceptReversedBox(float minX, float minY, float minZ,
                                           float maxX, float maxY, float maxZ);
    }
}
