package com.pp.shaderlod;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;

/** Tick-time Photon/config eligibility cache. This does not authorize traversal reuse. */
public final class ShadowVisibilityListsController {
    private static volatile boolean active;
    private static volatile long flagsGeneration = -1;
    private static volatile long lifecycleGeneration = -1;

    private ShadowVisibilityListsController() {}

    public static void configure(boolean configuredAndEligible) { active = configuredAndEligible; flagsGeneration=com.pp.shaderlod.optimization.OptimizationConfig.generation(); lifecycleGeneration=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch(); }
    public static boolean isActive() { return active && lifecycleGeneration==com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.epoch() && !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended() && flagsGeneration==com.pp.shaderlod.optimization.OptimizationConfig.generation() && com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.SHADOW_VISIBILITY_LISTS); }

    /** Match the original shared-array sprite consumer without changing traversal stamps. */
    public static boolean useShadowSprites(boolean enabled, boolean shadowPhase,
                                            boolean shadowExists, int shadowGeneration, int currentGeneration) {
        return enabled && shadowPhase && shadowExists && shadowGeneration == currentGeneration;
    }

    public interface RegionAccess {
        ChunkRenderList shaderlod$originalRegionList();
        ChunkRenderList shaderlod$shadowRegionList();
    }
}
