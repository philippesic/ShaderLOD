package com.pp.shaderlod;

/** Independent default-off eligibility flag; no temporal reuse is enabled by this feature. */
public final class ShadowFrustumCoherenceController {
    private static volatile boolean active;
    private ShadowFrustumCoherenceController() {}
    public static void configure(boolean configuredAndEligible) { active = configuredAndEligible; }
    public static boolean isActive() { return active; }
}
