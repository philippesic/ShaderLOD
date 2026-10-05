package com.pp.shaderlod.lod;

/** Stable effect identities for explicit shader adapters; ordinals are not a protocol. */
public enum Effect {
    SHADOW_FILTERING("ShadowFiltering", true),
    AMBIENT_OCCLUSION("AmbientOcclusion", true),
    REFLECTIONS("Reflections", false),
    VOLUMETRICS("Volumetrics", false),
    WATER_SURFACE("WaterSurface", false),
    MATERIALS("Materials", false);

    private final String uniformKeySuffix;
    private final boolean salientLighting;

    Effect(String uniformKeySuffix, boolean salientLighting) {
        this.uniformKeySuffix = uniformKeySuffix;
        this.salientLighting = salientLighting;
    }

    /** Suffix after an adapter's versioned uniform prefix, for example ShadowFiltering. */
    public String uniformKeySuffix() {
        return uniformKeySuffix;
    }

    public boolean salientLighting() {
        return salientLighting;
    }
}
