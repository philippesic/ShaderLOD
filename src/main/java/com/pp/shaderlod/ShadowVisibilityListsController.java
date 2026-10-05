package com.pp.shaderlod;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;

/** Tick-time Photon/config eligibility cache. This does not authorize traversal reuse. */
public final class ShadowVisibilityListsController {
    private static volatile boolean active;

    private ShadowVisibilityListsController() {}

    public static void configure(boolean configuredAndEligible) { active = configuredAndEligible; }
    public static boolean isActive() { return active; }

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
