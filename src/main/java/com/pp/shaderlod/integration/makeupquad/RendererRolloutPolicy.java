package com.pp.shaderlod.integration.makeupquad;

/** Manual product preview permission; every route still requires its existing safety gates. */
public final class RendererRolloutPolicy {
    /** Preserved diagnostic property; ordinary product preview does not require it. */
    public static final String RESEARCH_ROUTES = "shaderlod.experimentalRendererResearch";
    public enum Route { MAKEUP, SILDUR, UNBOUND, REIMAGINED, BSL, PHOTON, BLISS, ASTRA, SOLAS }
    private RendererRolloutPolicy() {}

    /** Pure route permission, never a source certificate or a performance/support claim. */
    public static boolean allows(Route route, boolean researchOptIn) {
        return route != null;
    }
}
