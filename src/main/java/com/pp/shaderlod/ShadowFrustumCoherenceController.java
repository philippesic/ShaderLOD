package com.pp.shaderlod;

/** Independent default-off eligibility flag; no temporal reuse is enabled by this feature. */
public final class ShadowFrustumCoherenceController {
    private static volatile boolean active;
    private static volatile long flagsGeneration = -1;
    private static volatile long lifecycleGeneration = -1;
    private ShadowFrustumCoherenceController() {}
    public static void configure(boolean configuredAndEligible) { active = configuredAndEligible; flagsGeneration=com.pp.shaderlod.optimization.OptimizationConfig.generation(); lifecycleGeneration=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch(); }
    public static boolean isActive() { return active && lifecycleGeneration==com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch() && !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended() && flagsGeneration==com.pp.shaderlod.optimization.OptimizationConfig.generation() && com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.SHADOW_FRUSTUM_COHERENCE); }
}
