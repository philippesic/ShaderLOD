package com.pp.shaderlod;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ShaderLodConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue NEAR;
    public static final ModConfigSpec.DoubleValue FAR;
    public static final ModConfigSpec.DoubleValue TRANSITION;
    public static final ModConfigSpec.BooleanValue DEBUG;
    public static final ModConfigSpec.EnumValue<LodPreset> PRESET;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("lod");
        ENABLED = builder.comment("Enable distance quality changes in the patched Photon pack.")
                .define("enabled", true);
        PRESET = builder.comment("Distance quality budgets: BALANCED, PERFORMANCE, or AGGRESSIVE.")
                .defineEnum("preset", LodPreset.PERFORMANCE);
        NEAR = builder.comment("End of full quality zone, in blocks.")
                .defineInRange("near", 32.0, 1.0, 4095.0);
        FAR = builder.comment("Start of low quality zone; must exceed near.")
                .defineInRange("far", 96.0, 2.0, 4096.0);
        TRANSITION = builder.comment("Total width of the dithered transition band, in blocks.")
                .defineInRange("transition", 8.0, 0.0, 512.0);
        DEBUG = builder.comment("Tint full/medium/low zones green/yellow/red.")
                .define("debug", false);
        builder.pop();
        SPEC = builder.build();
    }

    private ShaderLodConfig() {}

    public static LodSettings snapshot() {
        double near = NEAR.get();
        // Recover invalid cross-field edits without crashing the render loop.
        double far = Math.max(FAR.get(), near + 1.0);
        double width = Math.min(TRANSITION.get(), Math.min(near * 2, far - near));
        return new LodSettings(ENABLED.get(), near, far, width, DEBUG.get());
    }
}
