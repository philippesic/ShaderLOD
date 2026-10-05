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
    public static final ModConfigSpec.BooleanValue TERRAIN_ENABLED;
    public static final ModConfigSpec.IntValue TERRAIN_VANILLA_CHUNK_CAP;
    public static final ModConfigSpec.BooleanValue TERRAIN_WARM_SOURCE_CONFIRMED;
    public static final ModConfigSpec.IntValue TERRAIN_WARMUP_SECONDS;
    public static final ModConfigSpec.BooleanValue OPTIMIZE_SHADOW_CULLING;
    public static final ModConfigSpec.BooleanValue SEPARATE_SHADOW_VISIBILITY_LISTS;

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
        builder.push("terrain");
        TERRAIN_ENABLED = builder.comment("Opt-in effective vanilla terrain radius cap when Iris/DH are ready. Preserves the saved render-distance option.")
                .define("enabled", false);
        TERRAIN_VANILLA_CHUNK_CAP = builder.comment("Detailed vanilla chunk radius while opted in; DH reach is unchanged.")
                .defineInRange("vanillaChunkCap", 16, 8, 32);
        TERRAIN_WARM_SOURCE_CONFIRMED = builder.comment("Explicitly confirm this world/camera area has warmed DH terrain covering the replacement ring. Renderer readiness cannot prove cache coverage.")
                .define("warmSourceConfirmed", false);
        TERRAIN_WARMUP_SECONDS = builder.comment("Minimum stable Iris/DH world/pipeline warmup before capping; mesh/upload settling is still the user's responsibility.")
                .defineInRange("warmupSeconds", 30, 5, 120);
        builder.pop();
        builder.push("rendering");
        OPTIMIZE_SHADOW_CULLING = builder.comment("Use the equivalent positive-vertex-only boolean shadow visibility test on the pinned Iris/Photon runtime. Does not remove casters or change shadow quality.")
                .define("optimizeShadowCulling", false);
        SEPARATE_SHADOW_VISIBILITY_LISTS = builder.comment("Experimental separate shadow-owned region membership arrays. Keeps all existing visibility traversals; not a frame-rate optimization.")
                .define("separateShadowVisibilityLists", false);
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
