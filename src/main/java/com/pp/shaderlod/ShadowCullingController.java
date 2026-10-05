package com.pp.shaderlod;

/** Client-tick eligibility/configuration cache; the AABB path only reads active. */
public final class ShadowCullingController {
    private static boolean configured;
    private static boolean eligible;
    private static volatile boolean active;

    private ShadowCullingController() {}

    /** Call on the client thread when global/feature configuration changes. */
    public static void configure(boolean enabled) {
        configured = enabled;
        active = configured && eligible;
    }

    /** Caller owns world/pipeline/Photon eligibility and clears this on invalidation. */
    public static void setEligible(boolean value) {
        eligible = value;
        active = configured && eligible;
    }

    /** No config, Iris, GL, reflection, allocation or per-call world inspection. */
    public static boolean isActive() { return active; }

    /** Implemented on the base Iris frustum; inherited by its reversed subclass. */
    public interface PlaneAccess {
        boolean shaderlod$testPositiveVertices(float minX, float minY, float minZ,
                                              float maxX, float maxY, float maxZ);
        boolean shaderlod$testOriginalVertices(float minX, float minY, float minZ,
                                              float maxX, float maxY, float maxZ);
        boolean shaderlod$acceptReversedBox(float minX, float minY, float minZ,
                                           float maxX, float maxY, float maxZ);
    }
}
