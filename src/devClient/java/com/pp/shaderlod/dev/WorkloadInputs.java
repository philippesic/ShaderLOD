package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.timing.ShaderInputTracker;
import com.pp.shaderlod.dev.timing.WindowStateTracker;
import com.pp.shaderlod.ShaderLodEffectConfig;
import com.pp.shaderlod.integration.IrisAdapterRuntime;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.PhotonSpatialReferenceAdapter;
import com.pp.shaderlod.integration.PhotonReferenceAdapter;
import com.pp.shaderlod.integration.SourceBinding;
import com.pp.shaderlod.lod.adapter.AdapterDescriptor;
import com.pp.shaderlod.lod.adapter.WorkKey;
import com.pp.shaderlod.lod.DistanceBands;
import com.pp.shaderlod.integration.SourceFingerprint;
import com.pp.shaderlod.lod.Effect;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shared boundary evidence; no additional uniform/smoother evaluation or GL readback. */
public final class WorkloadInputs {
    public static final String ORIGINAL_NULL_SHA256 = "120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead";
    public static final String ORIGINAL_NULL_OPTIONS_SHA256 = "ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669";
    public static final String TERRAIN_PHOTON_SHA256 = "b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c";
    public static final String SSR_FULL_SHA256 = "41ecbee8b72039654fb0d25b16a841e3a2f4500025ec918687f94c0b8cba1555";
    public static final String SSR_REDUCED_SHA256 = "636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83";
    public static final int LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA = 2;
    public static final String LIVE_PIXEL_SHA256 = "0da5e4b446fca7ce300217781f3431f3e76ff0a5c3a2b0caafb2bb84f7563abb";
    public static final String LIVE_PIXEL_OPTIONS_SHA256 = "d5853a047a582b9ae2c5bb264438afa2510a76e5164fbf8b31976afc8788b8a0";
    public static final String LIVE_PIXEL_MEMBERS_SHA256 = "96bb6e5a4c08c140a6882a7a47b5d3872cebf6730fd1030b3d9000339d165888";
    private static final java.util.Map<String, String> LIVE_PIXEL_MEMBER_PINS = java.util.Map.ofEntries(
            java.util.Map.entry("LICENSE", "514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5"),
            java.util.Map.entry("shaderlod-live-pixel-budget.json", "38c5efce78c5d1240a36f7be0ace8b5857740d9aafb83bdf249efb3f1dd630c9"),
            java.util.Map.entry("shaders/include/shaderlod/live_pixel_budget.glsl", "de134110e033bfba65803dd44fb36058d7b92188b316eac24045e8c3538f02d8"),
            java.util.Map.entry("shaders/settings.glsl", "e6b1f78497ea66a0a8f23af4ca34ce94d1b6bca9986163b48233c66b0870e821"),
            java.util.Map.entry("shaders/include/global.glsl", "4200e4ef828e1ebf81823b23a630a195adffd917642d53c7c1122ce9217a41da"),
            java.util.Map.entry("shaders/program/gbuffers_all_solid.fsh", "33fdf18fb45cf639bb551b14a68d3d605025de378c01a5b30b282aff47832c2b"),
            java.util.Map.entry("shaders/program/gbuffers_all_translucent.fsh", "fefde9a047982ac3de75d23cc1b80ac2048e72e82d7058b9ac7b03dd0d086356"),
            java.util.Map.entry("shaders/program/gbuffers_damagedblock.fsh", "60036985ed0ed1856a22d7e4cdbecb2c0e74e9fee5c30e9b30bc24bb8c9573e0"),
            java.util.Map.entry("shaders/program/gbuffers_armor_glint.fsh", "57906c24b130e0b191df6920e4e076026462c21921a0598b2c0ddb4bc9f9c815"));
    public static final String WATER_SHADOW_ZERO_SHA256 = "eb411fa4a5ca13adb222a5e9e96b46b587d4f79538758c8035fca524156b4983";
    public static final String WATER_SHADOW_ZERO_OPTIONS_SHA256 = "16afc3e2270bd30aa1e440889e98b8550f6d4ecc345cb0e0009b117d4b933fae";
    public static final String WATER_SHADOW_ZERO_MEMBERS_SHA256 = "030d50c0c724bbf52812d4a8d55c9fc3fbaba1b814bc9eeb11e501866259fada";
    private static final java.util.Map<String, String> WATER_SHADOW_ZERO_MEMBER_PINS = java.util.Map.ofEntries(
            java.util.Map.entry("LICENSE", "514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5"),
            java.util.Map.entry("shaderlod-water-shadow-zero.json", "7efa561ba16b4f9835d2ce8650d4d6951ecdeed5ed1ab976d79146513076522a"),
            java.util.Map.entry("shaders/include/shaderlod/water_shadow_zero.glsl", "ba866764a6ff29558341e1ce5aa57bc9e8dc12bedededa5ca59965d76e01d3a9"),
            java.util.Map.entry("shaders/settings.glsl", "c4e0ea0c94946449638803f64a3d180d29a83fdc25332f8cf852ece6816637c1"),
            java.util.Map.entry("shaders/program/gbuffers_all_translucent.fsh", "cf5f5bddc2314b07d97cc1a76bccb90599bfd83cc0687fc36523a46ce6303e6a"));
    public static final String AIR_FOG_ABLATION_SHA256 = "e5f3c0605e5fd1e164c6996cbdf372e547fb964565c73d0deb5377e1e91f9c1b";
    public static final String AIR_FOG_ABLATION_OPTIONS_SHA256 = "c65d0f75579d75b86668d5e2ef607e37b2dbb824e4b3a18ad7dcb0ea2f078ab6";
    public static final String AIR_FOG_ABLATION_MEMBERS_SHA256 = "d9cc9a2b8799b5db5e55b9767623a6be7fae4a35966611762cff4d2aeea56bb2";
    private static final java.util.Map<String, String> AIR_FOG_ABLATION_MEMBER_PINS = java.util.Map.ofEntries(
            java.util.Map.entry("LICENSE", "514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5"),
            java.util.Map.entry("shaderlod-air-fog-cost-ablation.json", "cd683620a47b2418279c9e4e71611015b952b4b2e7d98594442aa0c33e11348f"),
            java.util.Map.entry("shaders/include/shaderlod/air_fog_cost_ablation.glsl", "aa534f38453c35101b3f66a7cc34bc9c671ede3bc38ddfdf76e4fd16f7c32269"),
            java.util.Map.entry("shaders/settings.glsl", "209c2eeade7f45b0c66b1aa15800adf041a1a26069c81faf002c22dfedb324e8"),
            java.util.Map.entry("shaders/program/c0_vl.fsh", "a17adb4b3c98a48d758116c2dae6f4cccf36a130823a1f01d14780a66797366c"));
    private final Path directory;
    private Path qualifiedSourcePath;
    private SourceFingerprint qualifiedSource;
    public WorkloadInputs(Path directory) { this.directory = directory; }

    public static JsonObject withoutEnabled(JsonObject state) {
        JsonObject copy = semanticState(state);
        if (copy.has("lod_settings")) copy.getAsJsonObject("lod_settings").remove("enabled");
        if (copy.has("loaded_semantic_config")) copy.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").remove("enabled");
        return copy;
    }

    public static JsonObject withoutFeature(JsonObject state, String feature) {
        if (feature.equals("global") || feature.equals("effects")) return withoutEnabled(state);
        if (feature.equals("ssr_cost")) return withoutEnabled(state);
        if (feature.equals("spatial") || feature.equals("volume")) return withoutEnabled(state);
        if (feature.equals("live_pixel") || feature.equals("water_shadow_zero") || feature.equals("air_fog_ablation")) {
            JsonObject copy = semanticState(state, feature);
            copy.getAsJsonObject("lod_settings").remove("enabled");
            copy.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").remove("enabled");
            return copy;
        }
        if (feature.equals("terrain")) {
            JsonObject copy = semanticState(state, feature);
            copy.remove("effective_chunks");
            copy.remove("terrain_configured");
            copy.remove("terrain_active");
            copy.getAsJsonObject("loaded_semantic_config").getAsJsonObject("terrain").remove("enabled");
            var policy = copy.getAsJsonObject("geometry_policy");
            for (String name : List.of("active", "effectiveChunks", "reason")) policy.remove(name);
            return copy;
        }
        if (!feature.equals("coherence")) throw new IllegalArgumentException("Unknown benchmark feature");
        JsonObject copy = semanticState(state);
        copy.remove("shadow_coherence_configured");
        copy.remove("shadow_coherence_active");
        copy.getAsJsonObject("loaded_semantic_config").getAsJsonObject("rendering").remove("shadowFrustumCoherence");
        return copy;
    }

    public static JsonObject semanticState(JsonObject state) {
        JsonObject copy = state.deepCopy();
        // Enabled may be persisted by config autosave. Full loaded fields replace
        // this raw-byte identity in the specialized phased control only.
        if (!copy.has("loaded_semantic_config")) throw new IllegalStateException("Complete semantic config unavailable");
        copy.remove("config/shaderlod-client.toml_sha256");
        if (copy.has("original_null_control") && copy.get("original_null_control").getAsBoolean()) {
            var normalized = originalNullConfigurationSemantics(state.getAsJsonObject("configuration_files"), state.get("shader_pack").getAsString());
            if (!normalized.equals(state.get("original_null_configuration_semantics"))) throw new IllegalStateException("Original null configuration normalization differs");
            copy.add("configuration_files", normalized);
            if (!state.get("config/iris.properties_sha256").equals(state.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/iris.properties").get("sha256"))) throw new IllegalStateException("Original null Iris hash alias differs");
            copy.remove("config/iris.properties_sha256");
        }
        return copy;
    }

    public static JsonObject semanticState(JsonObject state, String feature) {
        JsonObject copy = semanticState(state);
        if (feature.equals("terrain")) copy.getAsJsonObject("geometry_policy").remove("stableTicks");
        if (feature.equals("live_pixel")) {
            requireInteger(state, "live_pixel_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
            JsonObject normalized = livePixelConfigurationSemantics(state.getAsJsonObject("configuration_files"));
            if (!normalized.equals(state.get("live_pixel_configuration_semantics"))) throw new IllegalStateException("Live pixel captured configuration semantics differ");
            copy.add("configuration_files", normalized);
            // Raw Iris bytes are retained in the original input, while the complete
            // normalized known-file record replaces its redundant raw digest here.
            var irisRawHash = state.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/iris.properties").get("sha256");
            if (!irisRawHash.equals(state.get("config/iris.properties_sha256"))) throw new IllegalStateException("Live pixel Iris raw hash alias differs");
            copy.remove("config/iris.properties_sha256");
            for (String field : List.of("live_pixel_source_qualification", "live_pixel_effect_controls")) {
                if (!canonicalJsonHash(state.get(field)).equals(state.get(field + "_sha256").getAsString()))
                    throw new IllegalStateException("Live pixel canonical fingerprint differs: " + field);
            }
        }
        if (feature.equals("water_shadow_zero")) {
            requireInteger(state, "water_shadow_zero_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
            JsonObject normalized = waterShadowZeroConfigurationSemantics(state.getAsJsonObject("configuration_files"));
            if (!normalized.equals(state.get("water_shadow_zero_configuration_semantics"))) throw new IllegalStateException("Water shadow zero captured configuration semantics differ");
            copy.add("configuration_files", normalized);
            // Raw Iris bytes are retained in the original input, while the complete
            // normalized known-file record replaces its redundant raw digest here.
            var irisRawHash = state.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/iris.properties").get("sha256");
            if (!irisRawHash.equals(state.get("config/iris.properties_sha256"))) throw new IllegalStateException("Water shadow zero Iris raw hash alias differs");
            copy.remove("config/iris.properties_sha256");
            for (String field : List.of("water_shadow_zero_source_qualification", "water_shadow_zero_effect_controls")) {
                if (!canonicalJsonHash(state.get(field)).equals(state.get(field + "_sha256").getAsString()))
                    throw new IllegalStateException("Water shadow zero canonical fingerprint differs: " + field);
            }
        }
        if (feature.equals("air_fog_ablation")) {
            requireInteger(state, "air_fog_ablation_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
            JsonObject normalized = airFogAblationConfigurationSemantics(state.getAsJsonObject("configuration_files"));
            if (!normalized.equals(state.get("air_fog_ablation_configuration_semantics"))) throw new IllegalStateException("Air fog ablation captured configuration semantics differ");
            copy.add("configuration_files", normalized);
            // Raw Iris bytes are retained in the original input, while the complete
            // normalized known-file record replaces its redundant raw digest here.
            var irisRawHash = state.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/iris.properties").get("sha256");
            if (!irisRawHash.equals(state.get("config/iris.properties_sha256"))) throw new IllegalStateException("Air fog ablation Iris raw hash alias differs");
            copy.remove("config/iris.properties_sha256");
            for (String field : List.of("air_fog_ablation_source_qualification", "air_fog_ablation_effect_controls")) {
                if (!canonicalJsonHash(state.get(field)).equals(state.get(field + "_sha256").getAsString()))
                    throw new IllegalStateException("Air fog ablation canonical fingerprint differs: " + field);
            }
        }
        return copy;
    }

    public JsonObject benchmarkInputState() throws Exception {
        var state = inputState();
        var client = Minecraft.getInstance();
        boolean mod = ModList.get().isLoaded("shaderlod");
        state.addProperty("mod_present", mod);
        if (mod) {
            var semantic = new JsonObject();
            var lod = new JsonObject();
            lod.addProperty("enabled", com.pp.shaderlod.ShaderLodConfig.ENABLED.get());
            lod.addProperty("preset", com.pp.shaderlod.ShaderLodConfig.PRESET.get().name());
            lod.addProperty("near", com.pp.shaderlod.ShaderLodConfig.NEAR.get());
            lod.addProperty("far", com.pp.shaderlod.ShaderLodConfig.FAR.get());
            lod.addProperty("transition", com.pp.shaderlod.ShaderLodConfig.TRANSITION.get());
            lod.addProperty("debug", com.pp.shaderlod.ShaderLodConfig.DEBUG.get());
            semantic.add("lod", lod);
            var terrain = new JsonObject();
            terrain.addProperty("enabled", com.pp.shaderlod.ShaderLodConfig.TERRAIN_ENABLED.get());
            terrain.addProperty("vanillaChunkCap", com.pp.shaderlod.ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get());
            terrain.addProperty("warmSourceConfirmed", com.pp.shaderlod.ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get());
            terrain.addProperty("warmupSeconds", com.pp.shaderlod.ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get());
            semantic.add("terrain", terrain);
            var rendering = new JsonObject();
            rendering.addProperty("optimizeShadowCulling", com.pp.shaderlod.ShaderLodConfig.OPTIMIZE_SHADOW_CULLING.get());
            rendering.addProperty("separateShadowVisibilityLists", com.pp.shaderlod.ShaderLodConfig.SEPARATE_SHADOW_VISIBILITY_LISTS.get());
            rendering.addProperty("shadowFrustumCoherence", com.pp.shaderlod.ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.get());
            semantic.add("rendering", rendering);
            state.add("loaded_semantic_config", semantic);
            state.addProperty("terrain_configured", com.pp.shaderlod.ShaderLodConfig.TERRAIN_ENABLED.get());
            state.addProperty("terrain_confirmation", com.pp.shaderlod.ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get());
            state.addProperty("shadow_culling_configured", com.pp.shaderlod.ShaderLodConfig.OPTIMIZE_SHADOW_CULLING.get());
            state.addProperty("shadow_culling_active", com.pp.shaderlod.ShadowCullingController.isActive());
            state.addProperty("shadow_lists_configured", com.pp.shaderlod.ShaderLodConfig.SEPARATE_SHADOW_VISIBILITY_LISTS.get());
            state.addProperty("shadow_lists_active", com.pp.shaderlod.ShadowVisibilityListsController.isActive());
            state.addProperty("shadow_coherence_configured", com.pp.shaderlod.ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.get());
            state.addProperty("shadow_coherence_active", com.pp.shaderlod.ShadowFrustumCoherenceController.isActive());
        }
        Path archive = directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName());
        state.addProperty("actual_shader_sha256", hash(archive));
        return state;
    }

    public JsonObject originalNullInputState() throws Exception {
        var state = benchmarkInputState();
        var json = new GsonBuilder().disableHtmlEscaping().create();
        state.addProperty("original_null_control", true);
        var controls = json.toJsonTree(ShaderLodEffectConfig.snapshot()).getAsJsonObject();
        state.getAsJsonObject("loaded_semantic_config").add("effects", controls.deepCopy());
        state.add("original_null_effect_controls", controls);
        state.addProperty("original_null_effect_controls_sha256", canonicalJsonHash(controls));
        var configuration = ConfigurationFilesEvidence.capture(directory);
        state.add("configuration_files", configuration);
        state.add("original_null_configuration_semantics", originalNullConfigurationSemantics(configuration, state.get("shader_pack").getAsString()));
        state.addProperty("config/shaderlod-effects-client.toml_sha256", configuration.getAsJsonObject("files").getAsJsonObject("config/shaderlod-effects-client.toml").get("sha256").getAsString());
        var artifacts = LoadedArtifactEvidence.capture();
        LoadedArtifactEvidence.requireValid(artifacts);
        state.add("loaded_artifact_evidence", artifacts);
        var client = Minecraft.getInstance(); var window = client.getWindow();
        state.addProperty("original_null_dh_present", ModList.get().isLoaded("distanthorizons"));
        state.addProperty("original_null_camera_type", client.options.getCameraType().name());
        state.addProperty("original_null_fov", client.options.fov().get());
        state.add("original_null_window_state", json.toJsonTree(WindowStateTracker.readWindow(window.getWindow(), window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(), new WindowStateTracker.Pacing(client.options.pauseOnLostFocus, window.isFullscreen(), client.options.framerateLimit().get(), client.options.enableVsync().get()))));
        return state;
    }

    public static JsonObject originalNullConfigurationSemantics(JsonObject configuration, String pack) {
        var normalized = fixedCoreConfigurationSemantics(configuration, "shaderpacks/" + pack + ".txt", false);
        var files = configuration.getAsJsonObject("files");
        verifyUtf8Record(files.getAsJsonObject("config/shaderlod-effects-client.toml"));
        var iris = new java.util.Properties();
        try { iris.load(new java.io.StringReader(files.getAsJsonObject("config/iris.properties").get("utf8").getAsString())); }
        catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
        if (!pack.equals(iris.getProperty("shaderPack")) || !"true".equals(iris.getProperty("enableShaders"))) throw new IllegalStateException("Original null persisted Iris selection differs");
        return normalized;
    }

    static boolean isPinnedOriginalCase(JsonObject state) {
        return state.has("actual_shader_sha256") && ORIGINAL_NULL_SHA256.equals(state.get("actual_shader_sha256").getAsString());
    }

    static void validateOriginalNullOptions(JsonObject state) {
        var options = state.getAsJsonObject("effective_shader_options");
        if (!isPinnedOriginalCase(state) || options == null || options.size() != 366
                || !ORIGINAL_NULL_OPTIONS_SHA256.equals(canonicalJsonHash(options))
                || !ORIGINAL_NULL_OPTIONS_SHA256.equals(state.get("effective_shader_options_sha256").getAsString())) throw new IllegalStateException("Original null exact archive/complete loaded options differ");
        for (String name : options.keySet()) if (name.toLowerCase(java.util.Locale.ROOT).contains("shaderlod")) throw new IllegalStateException("Original null private option present");
        requireOption(options, "VL", "true"); requireOption(options, "TAA", "true"); requireOption(options, "TAAU", "false");
    }

    public static void validateOriginalNull(JsonObject state, boolean enabled) {
        validateBenchmarkControls(state, enabled);
        requireBoolean(state, "original_null_control", true);
        validateOriginalNullOptions(state);
        requireBoolean(state, "original_null_dh_present", false);
        for (var pin : java.util.Map.of("gui_scale", 2, "requested_chunks", 24, "effective_chunks", 24, "framebuffer_width", 2976, "framebuffer_height", 1672, "day_time", 12500, "original_null_fov", 70).entrySet()) requireInteger(state, pin.getKey(), pin.getValue());
        if (!"minecraft:overworld".equals(state.get("dimension").getAsString()) || !"FIRST_PERSON".equals(state.get("original_null_camera_type").getAsString())) throw new IllegalStateException("Original null world/camera differs");
        for (var pin : java.util.Map.of("x", 500.5, "y", 68.0, "z", 660.5, "yaw", (double)87.38f, "pitch", (double)3.735f, "rain", 1.0, "thunder", 0.0).entrySet()) if (state.get(pin.getKey()).getAsDouble() != pin.getValue()) throw new IllegalStateException("Original null fixed scene differs");
        var core = state.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod");
        if (!"PERFORMANCE".equals(core.get("preset").getAsString()) || core.get("near").getAsDouble() != 32 || core.get("far").getAsDouble() != 96 || core.get("transition").getAsDouble() != 8 || !"PERFORMANCE".equals(state.get("lod_preset").getAsString())) throw new IllegalStateException("Original null core settings differ");
        for (String field : List.of("near", "far", "transition")) if (!core.get(field).equals(state.getAsJsonObject("lod_settings").get(field))) throw new IllegalStateException("Original null core snapshot differs");
        var terrain = state.getAsJsonObject("loaded_semantic_config").getAsJsonObject("terrain");
        requireInteger(terrain, "vanillaChunkCap", 16); requireInteger(terrain, "warmupSeconds", 30);
        var controls = state.getAsJsonObject("original_null_effect_controls");
        if (controls.size() != Effect.values().length || !controls.equals(state.getAsJsonObject("loaded_semantic_config").get("effects")) || !canonicalJsonHash(controls).equals(state.get("original_null_effect_controls_sha256").getAsString())) throw new IllegalStateException("Original null complete controls differ");
        var json = new GsonBuilder().create();
        for (Effect effect : Effect.values()) {
            var control = json.fromJson(controls.get(effect.name()), ShaderLodEffectConfig.Control.class); var policy = control.policy();
            if (control.mode() != ShaderLodEffectConfig.Mode.INHERIT || control.repaired() || !control.requested().equals(policy) || policy.enabled() || !policy.preserveSalientLighting() || policy.mediumWorkRatio() != 1 || policy.farWorkRatio() != 1) throw new IllegalStateException("Original null unrelated effect active: " + effect);
        }
        LoadedArtifactEvidence.requireValid(state.getAsJsonObject("loaded_artifact_evidence"));
        if (!state.get("config/shaderlod-effects-client.toml_sha256").equals(state.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/shaderlod-effects-client.toml").get("sha256"))) throw new IllegalStateException("Original null effects config hash alias differs");
        semanticState(state);
        var window = state.getAsJsonObject("original_null_window_state");
        for (var pin : java.util.Map.of("availability", "available", "focused", "true", "visible", "true", "iconified", "false").entrySet()) if (!pin.getValue().equals(window.get(pin.getKey()).getAsString())) throw new IllegalStateException("Original null window unavailable");
        requireInteger(window, "framebuffer_width", 2976); requireInteger(window, "framebuffer_height", 1672); requireInteger(window, "window_width", 1488); requireInteger(window, "window_height", 836);
        var pacing = window.getAsJsonObject("pacing");
        requireBoolean(pacing, "vsync", false); requireBoolean(pacing, "fullscreen", false); requireBoolean(pacing, "pause_on_lost_focus", false); requireInteger(pacing, "configured_framerate_limit", 260);
    }

    /** Real boundary observations only; existing benchmark modes keep their old schema. */
    public JsonObject terrainInputState() throws Exception {
        var state = benchmarkInputState();
        state.add("geometry_policy", new GsonBuilder().create().toJsonTree(com.pp.shaderlod.GeometryLodController.status()));
        state.addProperty("dh_present", ModList.get().isLoaded("distanthorizons"));
        state.add("terrain_dh_state", terrainDhState());
        var client = Minecraft.getInstance(); var window = client.getWindow();
        state.addProperty("terrain_camera_type", client.options.getCameraType().name());
        state.addProperty("terrain_fov", client.options.fov().get());
        state.add("terrain_window_state", new GsonBuilder().create().toJsonTree(WindowStateTracker.readWindow(
                window.getWindow(), window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(),
                new WindowStateTracker.Pacing(client.options.pauseOnLostFocus, window.isFullscreen(),
                        client.options.framerateLimit().get(), client.options.enableVsync().get()))));
        return state;
    }

    public JsonObject ssrCostInputState() throws Exception {
        var state = benchmarkInputState();
        state.addProperty("ssr_cost_dh_present", ModList.get().isLoaded("distanthorizons"));
        var client = Minecraft.getInstance(); var window = client.getWindow();
        state.addProperty("ssr_cost_camera_type", client.options.getCameraType().name());
        state.addProperty("ssr_cost_fov", client.options.fov().get());
        state.add("ssr_cost_window_state", new GsonBuilder().create().toJsonTree(WindowStateTracker.readWindow(
                window.getWindow(), window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(),
                new WindowStateTracker.Pacing(client.options.pauseOnLostFocus, window.isFullscreen(),
                        client.options.framerateLimit().get(), client.options.enableVsync().get()))));
        return state;
    }

    /** Explicit reference-only boundary evidence; no uniform evaluation or synchronous GL readback. */
    public JsonObject spatialInputState() throws Exception {
        return referenceInputState("spatial", PhotonSpatialReferenceAdapter.sourceBindings());
    }

    public JsonObject volumeInputState() throws Exception {
        return referenceInputState("volume", PhotonReferenceAdapter.sourceBindings());
    }

    /** Explicit fixed-graph private diagnostic; core/global only, no capability inference. */
    public JsonObject livePixelInputState() throws Exception {
        var state = benchmarkInputState();
        state.addProperty("live_pixel_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var controls = json.toJsonTree(ShaderLodEffectConfig.snapshot()).getAsJsonObject();
        state.getAsJsonObject("loaded_semantic_config").add("effects", controls.deepCopy());
        state.add("live_pixel_effect_controls", controls);
        state.addProperty("live_pixel_effect_controls_sha256", canonicalJsonHash(controls));
        state.addProperty("config/shaderlod-effects-client.toml_sha256", hash(directory.resolve("config/shaderlod-effects-client.toml")));
        Path archive = directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName());
        if (qualifiedSource == null) {
            qualifiedSource = PackSourceReader.read(archive);
            qualifiedSourcePath = archive;
        } else if (!archive.equals(qualifiedSourcePath)) throw new IllegalStateException("Live pixel archive path changed");
        var source = qualifyLivePixelSource(qualifiedSource, loadedOptions(state));
        state.add("live_pixel_source_qualification", source);
        state.addProperty("live_pixel_source_qualification_sha256", canonicalJsonHash(source));
        state.addProperty("live_pixel_dh_present", ModList.get().isLoaded("distanthorizons"));
        var client = Minecraft.getInstance(); var window = client.getWindow();
        state.addProperty("live_pixel_camera_type", client.options.getCameraType().name());
        state.addProperty("live_pixel_fov", client.options.fov().get());
        state.add("live_pixel_window_state", json.toJsonTree(WindowStateTracker.readWindow(
                window.getWindow(), window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(),
                new WindowStateTracker.Pacing(client.options.pauseOnLostFocus, window.isFullscreen(),
                        client.options.framerateLimit().get(), client.options.enableVsync().get()))));
        // Complete disk snapshots and their normalization happen before the fresh capture clock.
        var configuration = ConfigurationFilesEvidence.capture(directory);
        state.add("configuration_files", configuration);
        state.add("live_pixel_configuration_semantics", livePixelConfigurationSemantics(configuration));
        return state;
    }

    static JsonObject qualifyLivePixelSource(SourceFingerprint source, java.util.Map<String, String> options) {
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var members = json.toJsonTree(new java.util.TreeMap<>(source.memberSha256())).getAsJsonObject();
        var allOptions = json.toJsonTree(new java.util.TreeMap<>(options)).getAsJsonObject();
        if (!source.sourceSha256().equals(LIVE_PIXEL_SHA256) || members.size() != 533
                || !jsonHash(members).equals(LIVE_PIXEL_MEMBERS_SHA256)
                || options.size() != 368 || !jsonHash(allOptions).equals(LIVE_PIXEL_OPTIONS_SHA256)) {
            throw new IllegalStateException("Live pixel exact archive/complete members/complete loaded Iris options differ");
        }
        for (var pin : LIVE_PIXEL_MEMBER_PINS.entrySet()) {
            if (!pin.getValue().equals(source.memberSha256().get(pin.getKey()))) throw new IllegalStateException("Live pixel member pin differs: " + pin.getKey());
        }
        for (String name : List.of("TAA", "TAAU", "SHADERLOD_LIVE_PIXEL_BUDGET", "SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED")) requireOption(allOptions, name, "true");
        var result = new JsonObject();
        result.addProperty("qualified", true);
        result.add("source_fingerprint", json.toJsonTree(source));
        result.addProperty("complete_member_count", members.size());
        result.addProperty("complete_member_sha256", jsonHash(members));
        result.addProperty("option_count", options.size());
        result.addProperty("complete_options_sha256", jsonHash(allOptions));
        return result;
    }

    /** Explicit fixed-graph private diagnostic; core/global only, no capability inference. */
    public JsonObject waterShadowZeroInputState() throws Exception {
        var state = benchmarkInputState();
        state.addProperty("water_shadow_zero_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var controls = json.toJsonTree(ShaderLodEffectConfig.snapshot()).getAsJsonObject();
        state.getAsJsonObject("loaded_semantic_config").add("effects", controls.deepCopy());
        state.add("water_shadow_zero_effect_controls", controls);
        state.addProperty("water_shadow_zero_effect_controls_sha256", canonicalJsonHash(controls));
        state.addProperty("config/shaderlod-effects-client.toml_sha256", hash(directory.resolve("config/shaderlod-effects-client.toml")));
        Path archive = directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName());
        if (qualifiedSource == null) {
            qualifiedSource = PackSourceReader.read(archive);
            qualifiedSourcePath = archive;
        } else if (!archive.equals(qualifiedSourcePath)) throw new IllegalStateException("Water shadow zero archive path changed");
        var source = qualifyWaterShadowZeroSource(qualifiedSource, loadedOptions(state));
        state.add("water_shadow_zero_source_qualification", source);
        state.addProperty("water_shadow_zero_source_qualification_sha256", canonicalJsonHash(source));
        state.addProperty("water_shadow_zero_dh_present", ModList.get().isLoaded("distanthorizons"));
        var client = Minecraft.getInstance(); var window = client.getWindow();
        state.addProperty("water_shadow_zero_camera_type", client.options.getCameraType().name());
        state.addProperty("water_shadow_zero_fov", client.options.fov().get());
        state.add("water_shadow_zero_window_state", json.toJsonTree(WindowStateTracker.readWindow(
                window.getWindow(), window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(),
                new WindowStateTracker.Pacing(client.options.pauseOnLostFocus, window.isFullscreen(),
                        client.options.framerateLimit().get(), client.options.enableVsync().get()))));
        // Complete disk snapshots and their normalization happen before the fresh capture clock.
        var configuration = ConfigurationFilesEvidence.capture(directory);
        state.add("configuration_files", configuration);
        state.add("water_shadow_zero_configuration_semantics", waterShadowZeroConfigurationSemantics(configuration));
        return state;
    }

    static JsonObject qualifyWaterShadowZeroSource(SourceFingerprint source, java.util.Map<String, String> options) {
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var members = json.toJsonTree(new java.util.TreeMap<>(source.memberSha256())).getAsJsonObject();
        var allOptions = json.toJsonTree(new java.util.TreeMap<>(options)).getAsJsonObject();
        if (!source.sourceSha256().equals(WATER_SHADOW_ZERO_SHA256) || members.size() != 533
                || !jsonHash(members).equals(WATER_SHADOW_ZERO_MEMBERS_SHA256)
                || options.size() != 368 || !jsonHash(allOptions).equals(WATER_SHADOW_ZERO_OPTIONS_SHA256)) {
            throw new IllegalStateException("Water shadow zero exact archive/complete members/complete loaded Iris options differ");
        }
        for (var pin : WATER_SHADOW_ZERO_MEMBER_PINS.entrySet()) {
            if (!pin.getValue().equals(source.memberSha256().get(pin.getKey()))) throw new IllegalStateException("Water shadow zero member pin differs: " + pin.getKey());
        }
        for (String name : List.of("TAA", "SHADOW", "SHADERLOD_WATER_SHADOW_ZERO", "SHADERLOD_WATER_SHADOW_ZERO_INCLUDED")) requireOption(allOptions, name, "true");
        requireOption(allOptions, "TAAU", "false"); requireOption(allOptions, "SHADOW_VPS", "false");
        requireOption(allOptions, "SHADOW_COLOR", "false"); requireOption(allOptions, "shadowMapResolution", "2048");
        var result = new JsonObject();
        result.addProperty("qualified", true);
        result.add("source_fingerprint", json.toJsonTree(source));
        result.addProperty("complete_member_count", members.size());
        result.addProperty("complete_member_sha256", jsonHash(members));
        result.addProperty("option_count", options.size());
        result.addProperty("complete_options_sha256", jsonHash(allOptions));
        return result;
    }

    /** Private fixed-graph fog-loss calibration; no GL or shader-stage claim. */
    public JsonObject airFogAblationInputState() throws Exception {
        var state = benchmarkInputState();
        var json = new GsonBuilder().disableHtmlEscaping().create();
        Path archive = directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName());
        if (qualifiedSource == null) {
            qualifiedSource = PackSourceReader.read(archive);
            qualifiedSourcePath = archive;
        } else if (!archive.equals(qualifiedSourcePath)) throw new IllegalStateException("Air fog ablation archive path changed");
        var client = Minecraft.getInstance(); var window = client.getWindow();
        state.add("air_fog_ablation_artifact_state", BenchmarkSession.airFogAblationArtifactState());
        return airFogAblationReceipt(state, qualifiedSource,
                json.toJsonTree(ShaderLodEffectConfig.snapshot()).getAsJsonObject(),
                ConfigurationFilesEvidence.capture(directory),
                json.toJsonTree(WindowStateTracker.readWindow(window.getWindow(), window.getScreenWidth(), window.getScreenHeight(),
                        window.getWidth(), window.getHeight(), new WindowStateTracker.Pacing(client.options.pauseOnLostFocus,
                                window.isFullscreen(), client.options.framerateLimit().get(), client.options.enableVsync().get()))).getAsJsonObject(),
                ModList.get().isLoaded("distanthorizons"), client.options.getCameraType().name(), client.options.fov().get());
    }

    /** Source-qualified producer schema; also exercised without initializing Minecraft. */
    static JsonObject airFogAblationReceipt(JsonObject state, SourceFingerprint fingerprint, JsonObject controls,
            JsonObject configuration, JsonObject window, boolean dhPresent, String cameraType, int fov) {
        state.addProperty("air_fog_ablation_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        state.getAsJsonObject("loaded_semantic_config").add("effects", controls.deepCopy());
        state.add("air_fog_ablation_effect_controls", controls.deepCopy());
        state.addProperty("air_fog_ablation_effect_controls_sha256", canonicalJsonHash(controls));
        state.addProperty("config/shaderlod-effects-client.toml_sha256", configuration.getAsJsonObject("files")
                .getAsJsonObject("config/shaderlod-effects-client.toml").get("sha256").getAsString());
        var source = qualifyAirFogAblationSource(fingerprint, loadedOptions(state));
        state.add("air_fog_ablation_source_qualification", source);
        state.addProperty("air_fog_ablation_source_qualification_sha256", canonicalJsonHash(source));
        state.addProperty("air_fog_ablation_dh_present", dhPresent);
        state.addProperty("air_fog_ablation_camera_type", cameraType);
        state.addProperty("air_fog_ablation_fov", fov);
        state.add("air_fog_ablation_window_state", window.deepCopy());
        state.add("configuration_files", configuration.deepCopy());
        state.add("air_fog_ablation_configuration_semantics", airFogAblationConfigurationSemantics(configuration));
        return state;
    }

    static JsonObject qualifyAirFogAblationSource(SourceFingerprint source, java.util.Map<String, String> options) {
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var members = json.toJsonTree(new java.util.TreeMap<>(source.memberSha256())).getAsJsonObject();
        var allOptions = json.toJsonTree(new java.util.TreeMap<>(options)).getAsJsonObject();
        if (!source.sourceSha256().equals(AIR_FOG_ABLATION_SHA256) || members.size() != 533
                || !jsonHash(members).equals(AIR_FOG_ABLATION_MEMBERS_SHA256)
                || options.size() != 367 || !jsonHash(allOptions).equals(AIR_FOG_ABLATION_OPTIONS_SHA256)) {
            throw new IllegalStateException("Air fog ablation exact archive/complete members/complete loaded Iris options differ");
        }
        for (var pin : AIR_FOG_ABLATION_MEMBER_PINS.entrySet()) {
            if (!pin.getValue().equals(source.memberSha256().get(pin.getKey()))) throw new IllegalStateException("Air fog ablation member pin differs: " + pin.getKey());
        }
        for (String name : List.of("TAA", "SHADOW", "VL", "LPV_VL", "SHADERLOD_AIR_FOG_COST_ABLATION")) requireOption(allOptions, name, "true");
        requireOption(allOptions, "AIR_FOG_COLORED_LIGHT_SHAFTS", "false"); requireOption(allOptions, "COLORED_LIGHTS", "false");
        requireOption(allOptions, "TAAU", "false"); requireOption(allOptions, "SHADOW_VPS", "false");
        requireOption(allOptions, "SHADOW_COLOR", "false"); requireOption(allOptions, "shadowMapResolution", "2048");
        var result = new JsonObject();
        result.addProperty("qualified", true);
        result.add("source_fingerprint", json.toJsonTree(source));
        result.addProperty("complete_member_count", members.size());
        result.addProperty("complete_member_sha256", jsonHash(members));
        result.addProperty("option_count", options.size());
        result.addProperty("complete_options_sha256", jsonHash(allOptions));
        return result;
    }

    /** Complete raw evidence stays in inputs; narrowly qualified startup metadata is normalized here. */
    public static JsonObject livePixelConfigurationSemantics(JsonObject captured) {
        return fixedCoreConfigurationSemantics(captured, "shaderpacks/live-pixel-budget-enabled.zip.txt", false);
    }

    public static JsonObject waterShadowZeroConfigurationSemantics(JsonObject captured) {
        return fixedCoreConfigurationSemantics(captured, "shaderpacks/water-shadow-zero-enabled.zip.txt", true);
    }

    private static JsonObject fixedCoreConfigurationSemantics(JsonObject captured, String exactSidecar, boolean checkEffectsBackup) {
        if (captured == null || !captured.has("complete") || !captured.get("complete").getAsBoolean()
                || !captured.has("errors") || !captured.getAsJsonArray("errors").isEmpty()) throw new IllegalStateException("Live pixel complete configuration unavailable");
        var normalized = captured.deepCopy();
        var files = normalized.getAsJsonObject("files");
        if (files == null || !files.has("options.txt") || !files.has("config/iris.properties")
                || !files.has("config/shaderlod-client.toml") || !files.has("config/shaderlod-effects-client.toml")) throw new IllegalStateException("Live pixel required configuration files unavailable");
        var main = files.getAsJsonObject("config/shaderlod-client.toml");
        verifyUtf8Record(main);
        files.add("config/shaderlod-client.toml", omitPersistedGlobal(main));
        String backupPath = "config/shaderlod-client-1.toml.bak";
        if (files.has(backupPath)) {
            var backup = files.getAsJsonObject(backupPath);
            verifyUtf8Record(backup);
            // NeoForge's first startup backup must derive from the complete main
            // settings; arbitrary backup contents are never omitted or trusted.
            var mainSettings = new com.electronwill.nightconfig.toml.TomlParser().parse(main.get("utf8").getAsString());
            var backupSettings = new com.electronwill.nightconfig.toml.TomlParser().parse(backup.get("utf8").getAsString());
            mainSettings.remove("lod.enabled"); backupSettings.remove("lod.enabled");
            if (!mainSettings.equals(backupSettings)) throw new IllegalStateException("Live pixel startup backup is not derived from main settings");
            files.add(backupPath, omitPersistedGlobal(backup));
        }
        if (checkEffectsBackup) {
            verifyUtf8Record(files.getAsJsonObject("config/shaderlod-effects-client.toml"));
            var irisProperties = new java.util.Properties();
            try { irisProperties.load(new java.io.StringReader(files.getAsJsonObject("config/iris.properties").get("utf8").getAsString())); }
            catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
            if (!"water-shadow-zero-enabled.zip".equals(irisProperties.getProperty("shaderPack"))
                    || !"true".equals(irisProperties.getProperty("enableShaders")))
                throw new IllegalStateException("Water shadow zero persisted Iris pack selection differs");
        }
        if (checkEffectsBackup && files.has("config/shaderlod-effects-client-1.toml.bak")) {
            var effects = files.getAsJsonObject("config/shaderlod-effects-client.toml");
            var backup = files.getAsJsonObject("config/shaderlod-effects-client-1.toml.bak");
            verifyUtf8Record(effects); verifyUtf8Record(backup);
            if (!new com.electronwill.nightconfig.toml.TomlParser().parse(effects.get("utf8").getAsString())
                    .equals(new com.electronwill.nightconfig.toml.TomlParser().parse(backup.get("utf8").getAsString())))
                throw new IllegalStateException("Water shadow zero effects startup backup is not derived from unchanged effects settings");
            // No effects feature delta is authorized: all effect backup bytes stay strict.
        }
        for (String known : List.of("config/iris.properties", exactSidecar)) {
            if (!files.has(known)) throw new IllegalStateException("Live pixel known Iris file unavailable: " + known);
            var raw = files.getAsJsonObject(known); verifyUtf8Record(raw);
            String text = raw.get("utf8").getAsString();
            var timestamp = java.util.regex.Pattern.compile("(?m)^#(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-3][0-9] [0-2][0-9]:[0-5][0-9]:[0-5][0-9] [A-Za-z0-9:+_-]+ [0-9]{4}(?=\\r?$)").matcher(text);
            if (!timestamp.find()) throw new IllegalStateException("Live pixel generated Iris timestamp unavailable: " + known);
            int start = timestamp.start(), end = timestamp.end();
            if (timestamp.find()) throw new IllegalStateException("Live pixel duplicate Iris timestamp: " + known);
            var item = raw.deepCopy(); item.remove("bytes"); item.remove("sha256"); item.remove("utf8");
            item.addProperty("utf8_generated_timestamp_omitted", text.substring(0, start) + "#<iris-startup-timestamp>" + text.substring(end));
            files.add(known, item);
        }
        return normalized;
    }


    public static JsonObject airFogAblationConfigurationSemantics(JsonObject captured) {
        String exactSidecar = "shaderpacks/air-fog-cost-ablation-enabled.zip.txt";
        boolean checkEffectsBackup = true;
        if (captured == null || !captured.has("complete") || !captured.get("complete").getAsBoolean()
                || !captured.has("errors") || !captured.getAsJsonArray("errors").isEmpty()) throw new IllegalStateException("Air fog ablation complete configuration unavailable");
        var normalized = captured.deepCopy();
        var files = normalized.getAsJsonObject("files");
        if (files == null || !files.has("options.txt") || !files.has("config/iris.properties")
                || !files.has("config/shaderlod-client.toml") || !files.has("config/shaderlod-effects-client.toml")) throw new IllegalStateException("Air fog ablation required configuration files unavailable");
        var main = files.getAsJsonObject("config/shaderlod-client.toml");
        verifyUtf8Record(main);
        files.add("config/shaderlod-client.toml", omitPersistedGlobal(main));
        String backupPath = "config/shaderlod-client-1.toml.bak";
        if (files.has(backupPath)) {
            var backup = files.getAsJsonObject(backupPath);
            verifyUtf8Record(backup);
            // NeoForge's first startup backup must derive from the complete main
            // settings; arbitrary backup contents are never omitted or trusted.
            var mainSettings = new com.electronwill.nightconfig.toml.TomlParser().parse(main.get("utf8").getAsString());
            var backupSettings = new com.electronwill.nightconfig.toml.TomlParser().parse(backup.get("utf8").getAsString());
            mainSettings.remove("lod.enabled"); backupSettings.remove("lod.enabled");
            if (!mainSettings.equals(backupSettings)) throw new IllegalStateException("Air fog ablation startup backup is not derived from main settings");
            files.add(backupPath, omitPersistedGlobal(backup));
        }
        if (checkEffectsBackup) {
            verifyUtf8Record(files.getAsJsonObject("config/shaderlod-effects-client.toml"));
            var irisProperties = new java.util.Properties();
            try { irisProperties.load(new java.io.StringReader(files.getAsJsonObject("config/iris.properties").get("utf8").getAsString())); }
            catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
            if (!"air-fog-cost-ablation-enabled.zip".equals(irisProperties.getProperty("shaderPack"))
                    || !"true".equals(irisProperties.getProperty("enableShaders")))
                throw new IllegalStateException("Air fog ablation persisted Iris pack selection differs");
        }
        if (checkEffectsBackup && files.has("config/shaderlod-effects-client-1.toml.bak")) {
            var effects = files.getAsJsonObject("config/shaderlod-effects-client.toml");
            var backup = files.getAsJsonObject("config/shaderlod-effects-client-1.toml.bak");
            verifyUtf8Record(effects); verifyUtf8Record(backup);
            if (!new com.electronwill.nightconfig.toml.TomlParser().parse(effects.get("utf8").getAsString())
                    .equals(new com.electronwill.nightconfig.toml.TomlParser().parse(backup.get("utf8").getAsString())))
                throw new IllegalStateException("Air fog ablation effects startup backup is not derived from unchanged effects settings");
            // No effects feature delta is authorized: all effect backup bytes stay strict.
        }
        for (String known : List.of("config/iris.properties", exactSidecar)) {
            if (!files.has(known)) throw new IllegalStateException("Air fog ablation known Iris file unavailable: " + known);
            var raw = files.getAsJsonObject(known); verifyUtf8Record(raw);
            String text = raw.get("utf8").getAsString();
            var timestamp = java.util.regex.Pattern.compile("(?m)^#(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-3][0-9] [0-2][0-9]:[0-5][0-9]:[0-5][0-9] [A-Za-z0-9:+_-]+ [0-9]{4}(?=\\r?$)").matcher(text);
            if (!timestamp.find()) throw new IllegalStateException("Air fog ablation generated Iris timestamp unavailable: " + known);
            int start = timestamp.start(), end = timestamp.end();
            if (timestamp.find()) throw new IllegalStateException("Air fog ablation duplicate Iris timestamp: " + known);
            var item = raw.deepCopy(); item.remove("bytes"); item.remove("sha256"); item.remove("utf8");
            item.addProperty("utf8_generated_timestamp_omitted", text.substring(0, start) + "#<iris-startup-timestamp>" + text.substring(end));
            files.add(known, item);
        }
        return normalized;
    }


    private static JsonObject omitPersistedGlobal(JsonObject main) {
        if (!main.has("utf8")) throw new IllegalStateException("Live pixel main config must be UTF-8");
        String text = main.get("utf8").getAsString();
        var sections = java.util.regex.Pattern.compile("(?m)^\\h*\\[([^\\r\\n]+)]\\h*(?:#[^\\r\\n]*)?\\r?$").matcher(text);
        int start = -1, end = text.length(), lodSections = 0;
        while (sections.find()) {
            if (start >= 0 && end == text.length()) end = sections.start();
            if (sections.group(1).equals("lod")) { lodSections++; start = sections.end(); end = text.length(); }
        }
        if (lodSections != 1) throw new IllegalStateException("Live pixel requires one exact [lod] table");
        String body = text.substring(start, end);
        var enabled = java.util.regex.Pattern.compile("(?m)^(\\h*enabled\\h*=\\h*)(true|false)(\\h*(?:#[^\\r\\n]*)?\\r?)$").matcher(body);
        if (!enabled.find()) throw new IllegalStateException("Live pixel persisted global boolean unavailable");
        int tokenStart = start + enabled.start(2), tokenEnd = start + enabled.end(2);
        if (enabled.find()) throw new IllegalStateException("Live pixel duplicate persisted global boolean");
        var item = main.deepCopy(); item.remove("bytes"); item.remove("sha256"); item.remove("utf8");
        item.addProperty("utf8_lod_enabled_omitted", text.substring(0, tokenStart) + "<live-global>" + text.substring(tokenEnd));
        return item;
    }

    private static void verifyUtf8Record(JsonObject record) {
        if (!record.has("utf8") || !record.has("bytes") || !record.has("sha256")) throw new IllegalStateException("Live pixel known UTF-8 evidence unavailable");
        byte[] bytes = record.get("utf8").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            if (record.get("bytes").getAsLong() != bytes.length || !record.get("sha256").getAsString().equals(digest))
                throw new IllegalStateException("Live pixel known UTF-8 raw evidence differs");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Sort object keys recursively; array order and primitive values remain strict. */
    public static com.google.gson.JsonElement canonicalJson(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            new java.util.TreeSet<>(value.getAsJsonObject().keySet()).forEach(key -> result.add(key, canonicalJson(value.getAsJsonObject().get(key))));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new com.google.gson.JsonArray(); value.getAsJsonArray().forEach(item -> result.add(canonicalJson(item))); return result;
        }
        return value.deepCopy();
    }

    public static String canonicalJsonHash(com.google.gson.JsonElement value) {
        return jsonHash(canonicalJson(value).getAsJsonObject());
    }

    public static void validateLivePixel(JsonObject state, boolean enabled) {
        validateBenchmarkControls(state, enabled);
        requireInteger(state, "live_pixel_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        requireBoolean(state, "live_pixel_dh_present", false);
        requireInteger(state, "gui_scale", 2);
        requireInteger(state, "requested_chunks", 24); requireInteger(state, "effective_chunks", 24);
        requireInteger(state, "framebuffer_width", 2976); requireInteger(state, "framebuffer_height", 1672);
        requireInteger(state, "day_time", 12500);
        requireInteger(state, "live_pixel_fov", 70);
        if (!state.get("dimension").getAsString().equals("minecraft:overworld")
                || !state.get("live_pixel_camera_type").getAsString().equals("FIRST_PERSON")) throw new IllegalStateException("Live pixel fixed world/camera differs");
        for (var expected : java.util.Map.of("x", 500.5, "y", 68.0, "z", 660.5, "yaw", (double) 87.38f, "pitch", (double) 3.735f, "rain", 0.0, "thunder", 0.0).entrySet()) {
            if (state.get(expected.getKey()).getAsDouble() != expected.getValue()) throw new IllegalStateException("Live pixel fixed scene differs: " + expected.getKey());
        }
        var semantic = state.getAsJsonObject("loaded_semantic_config");
        var lod = semantic.getAsJsonObject("lod");
        if (!lod.get("preset").getAsString().equals("PERFORMANCE") || lod.get("near").getAsDouble() != 32
                || lod.get("far").getAsDouble() != 96 || lod.get("transition").getAsDouble() != 8) throw new IllegalStateException("Live pixel fixed core config differs");
        var terrain = semantic.getAsJsonObject("terrain");
        requireInteger(terrain, "vanillaChunkCap", 16); requireInteger(terrain, "warmupSeconds", 30);
        var source = state.getAsJsonObject("live_pixel_source_qualification");
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var fingerprint = json.fromJson(source.get("source_fingerprint"), SourceFingerprint.class);
        if (!source.equals(qualifyLivePixelSource(fingerprint, loadedOptions(state)))
                || !state.get("actual_shader_sha256").getAsString().equals(LIVE_PIXEL_SHA256)
                || !state.get("effective_shader_options_sha256").getAsString().equals(LIVE_PIXEL_OPTIONS_SHA256)) throw new IllegalStateException("Live pixel source/options fingerprint differs");
        var controls = state.getAsJsonObject("live_pixel_effect_controls");
        if (controls.size() != Effect.values().length || !controls.equals(semantic.get("effects"))) throw new IllegalStateException("Live pixel incomplete inert effect controls");
        for (Effect effect : Effect.values()) {
            var control = json.fromJson(controls.get(effect.name()), ShaderLodEffectConfig.Control.class);
            var policy = control.policy();
            if (control.mode() != ShaderLodEffectConfig.Mode.INHERIT || control.repaired() || !control.requested().equals(policy)
                    || policy.enabled() || !policy.preserveSalientLighting() || policy.mediumWorkRatio() != 1 || policy.farWorkRatio() != 1) throw new IllegalStateException("Live pixel unrelated effect control active: " + effect);
        }
        for (String field : List.of("live_pixel_source_qualification", "live_pixel_effect_controls")) {
            if (!canonicalJsonHash(state.get(field)).equals(state.get(field + "_sha256").getAsString())) throw new IllegalStateException("Live pixel fingerprint differs: " + field);
        }
        if (!state.get("config/shaderlod-effects-client.toml_sha256").getAsString().matches("[0-9a-f]{64}")) throw new IllegalStateException("Live pixel effects config fingerprint unavailable");
        if (!livePixelConfigurationSemantics(state.getAsJsonObject("configuration_files")).equals(state.get("live_pixel_configuration_semantics"))) throw new IllegalStateException("Live pixel configuration normalization differs");
        var window = state.getAsJsonObject("live_pixel_window_state");
        if (!window.get("availability").getAsString().equals("available") || !window.get("focused").getAsString().equals("true")
                || !window.get("visible").getAsString().equals("true") || !window.get("iconified").getAsString().equals("false")) throw new IllegalStateException("Live pixel window unavailable/hidden/unfocused");
        requireInteger(window, "framebuffer_width", 2976); requireInteger(window, "framebuffer_height", 1672);
        requireInteger(window, "window_width", 1488); requireInteger(window, "window_height", 836);
        var pacing = window.getAsJsonObject("pacing");
        requireBoolean(pacing, "vsync", false); requireBoolean(pacing, "fullscreen", false); requireBoolean(pacing, "pause_on_lost_focus", false);
        requireInteger(pacing, "configured_framerate_limit", 260);
    }

    public static void validateWaterShadowZero(JsonObject state, boolean enabled) {
        validateBenchmarkControls(state, enabled);
        requireInteger(state, "water_shadow_zero_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        requireBoolean(state, "water_shadow_zero_dh_present", false);
        if (!state.has("shader_pack") || !state.get("shader_pack").getAsString().equals("water-shadow-zero-enabled.zip")) throw new IllegalStateException("Water shadow zero exact selected pack name differs");
        requireInteger(state, "gui_scale", 2);
        requireInteger(state, "requested_chunks", 24); requireInteger(state, "effective_chunks", 24);
        requireInteger(state, "framebuffer_width", 2976); requireInteger(state, "framebuffer_height", 1672);
        requireInteger(state, "day_time", 12500);
        requireInteger(state, "water_shadow_zero_fov", 70);
        if (!state.get("dimension").getAsString().equals("minecraft:overworld")
                || !state.get("water_shadow_zero_camera_type").getAsString().equals("FIRST_PERSON")) throw new IllegalStateException("Water shadow zero fixed world/camera differs");
        for (var expected : java.util.Map.of("x", 500.5, "y", 68.0, "z", 660.5, "yaw", (double) 87.38f, "pitch", (double) 3.735f, "rain", 0.0, "thunder", 0.0).entrySet()) {
            if (state.get(expected.getKey()).getAsDouble() != expected.getValue()) throw new IllegalStateException("Water shadow zero fixed scene differs: " + expected.getKey());
        }
        var semantic = state.getAsJsonObject("loaded_semantic_config");
        var lod = semantic.getAsJsonObject("lod");
        if (!lod.get("preset").getAsString().equals("PERFORMANCE") || lod.get("near").getAsDouble() != 32
                || lod.get("far").getAsDouble() != 96 || lod.get("transition").getAsDouble() != 8) throw new IllegalStateException("Water shadow zero fixed core config differs");
        var terrain = semantic.getAsJsonObject("terrain");
        requireInteger(terrain, "vanillaChunkCap", 16); requireInteger(terrain, "warmupSeconds", 30);
        var source = state.getAsJsonObject("water_shadow_zero_source_qualification");
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var fingerprint = json.fromJson(source.get("source_fingerprint"), SourceFingerprint.class);
        if (!source.equals(qualifyWaterShadowZeroSource(fingerprint, loadedOptions(state)))
                || !state.get("actual_shader_sha256").getAsString().equals(WATER_SHADOW_ZERO_SHA256)
                || !state.get("effective_shader_options_sha256").getAsString().equals(WATER_SHADOW_ZERO_OPTIONS_SHA256)) throw new IllegalStateException("Water shadow zero source/options fingerprint differs");
        var controls = state.getAsJsonObject("water_shadow_zero_effect_controls");
        if (controls.size() != Effect.values().length || !controls.equals(semantic.get("effects"))) throw new IllegalStateException("Water shadow zero incomplete inert effect controls");
        for (Effect effect : Effect.values()) {
            var control = json.fromJson(controls.get(effect.name()), ShaderLodEffectConfig.Control.class);
            var policy = control.policy();
            if (control.mode() != ShaderLodEffectConfig.Mode.INHERIT || control.repaired() || !control.requested().equals(policy)
                    || policy.enabled() || !policy.preserveSalientLighting() || policy.mediumWorkRatio() != 1 || policy.farWorkRatio() != 1) throw new IllegalStateException("Water shadow zero unrelated effect control active: " + effect);
        }
        for (String field : List.of("water_shadow_zero_source_qualification", "water_shadow_zero_effect_controls")) {
            if (!canonicalJsonHash(state.get(field)).equals(state.get(field + "_sha256").getAsString())) throw new IllegalStateException("Water shadow zero fingerprint differs: " + field);
        }
        if (!state.get("config/shaderlod-effects-client.toml_sha256").getAsString().matches("[0-9a-f]{64}")) throw new IllegalStateException("Water shadow zero effects config fingerprint unavailable");
        if (!state.get("config/shaderlod-effects-client.toml_sha256").equals(state.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/shaderlod-effects-client.toml").get("sha256"))) throw new IllegalStateException("Water shadow zero effects raw hash alias differs");
        if (!waterShadowZeroConfigurationSemantics(state.getAsJsonObject("configuration_files")).equals(state.get("water_shadow_zero_configuration_semantics"))) throw new IllegalStateException("Water shadow zero configuration normalization differs");
        var window = state.getAsJsonObject("water_shadow_zero_window_state");
        if (!window.get("availability").getAsString().equals("available") || !window.get("focused").getAsString().equals("true")
                || !window.get("visible").getAsString().equals("true") || !window.get("iconified").getAsString().equals("false")) throw new IllegalStateException("Water shadow zero window unavailable/hidden/unfocused");
        requireInteger(window, "framebuffer_width", 2976); requireInteger(window, "framebuffer_height", 1672);
        requireInteger(window, "window_width", 1488); requireInteger(window, "window_height", 836);
        var pacing = window.getAsJsonObject("pacing");
        requireBoolean(pacing, "vsync", false); requireBoolean(pacing, "fullscreen", false); requireBoolean(pacing, "pause_on_lost_focus", false);
        requireInteger(pacing, "configured_framerate_limit", 260);
    }

    public static void validateAirFogAblation(JsonObject state, boolean enabled) {
        validateBenchmarkControls(state, enabled);
        requireInteger(state, "air_fog_ablation_control_receipt_schema", LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        requireBoolean(state, "air_fog_ablation_dh_present", false);
        if (!state.has("shader_pack") || !state.get("shader_pack").getAsString().equals("air-fog-cost-ablation-enabled.zip")) throw new IllegalStateException("Air fog ablation exact selected pack name differs");
        requireInteger(state, "gui_scale", 2);
        requireInteger(state, "requested_chunks", 24); requireInteger(state, "effective_chunks", 24);
        requireInteger(state, "framebuffer_width", 2976); requireInteger(state, "framebuffer_height", 1672);
        requireInteger(state, "day_time", 12500);
        requireInteger(state, "air_fog_ablation_fov", 70);
        if (!state.get("dimension").getAsString().equals("minecraft:overworld")
                || !state.get("air_fog_ablation_camera_type").getAsString().equals("FIRST_PERSON")) throw new IllegalStateException("Air fog ablation fixed world/camera differs");
        for (var expected : java.util.Map.of("x", 500.5, "y", 68.0, "z", 660.5, "yaw", (double) 87.38f, "pitch", (double) 3.735f, "rain", 1.0, "thunder", 0.0).entrySet()) {
            if (state.get(expected.getKey()).getAsDouble() != expected.getValue()) throw new IllegalStateException("Air fog ablation fixed scene differs: " + expected.getKey());
        }
        var semantic = state.getAsJsonObject("loaded_semantic_config");
        var lod = semantic.getAsJsonObject("lod");
        if (!lod.get("preset").getAsString().equals("PERFORMANCE") || lod.get("near").getAsDouble() != 32
                || lod.get("far").getAsDouble() != 96 || lod.get("transition").getAsDouble() != 8) throw new IllegalStateException("Air fog ablation fixed core config differs");
        if (!"PERFORMANCE".equals(state.get("lod_preset").getAsString())) throw new IllegalStateException("Air fog actual preset differs");
        for (String field : List.of("near", "far", "transition")) {
            if (!state.getAsJsonObject("lod_settings").get(field).equals(lod.get(field))) throw new IllegalStateException("Air fog actual core snapshot differs: " + field);
        }
        var terrain = semantic.getAsJsonObject("terrain");
        requireInteger(terrain, "vanillaChunkCap", 16); requireInteger(terrain, "warmupSeconds", 30);
        var source = state.getAsJsonObject("air_fog_ablation_source_qualification");
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var fingerprint = json.fromJson(source.get("source_fingerprint"), SourceFingerprint.class);
        if (!source.equals(qualifyAirFogAblationSource(fingerprint, loadedOptions(state)))
                || !state.get("actual_shader_sha256").getAsString().equals(AIR_FOG_ABLATION_SHA256)
                || !state.get("effective_shader_options_sha256").getAsString().equals(AIR_FOG_ABLATION_OPTIONS_SHA256)) throw new IllegalStateException("Air fog ablation source/options fingerprint differs");
        BenchmarkSession.validateAirFogAblationArtifactState(state.getAsJsonObject("air_fog_ablation_artifact_state"));
        var controls = state.getAsJsonObject("air_fog_ablation_effect_controls");
        if (controls.size() != Effect.values().length || !controls.equals(semantic.get("effects"))) throw new IllegalStateException("Air fog ablation incomplete inert effect controls");
        for (Effect effect : Effect.values()) {
            var control = json.fromJson(controls.get(effect.name()), ShaderLodEffectConfig.Control.class);
            var policy = control.policy();
            if (control.mode() != ShaderLodEffectConfig.Mode.INHERIT || control.repaired() || !control.requested().equals(policy)
                    || policy.enabled() || !policy.preserveSalientLighting() || policy.mediumWorkRatio() != 1 || policy.farWorkRatio() != 1) throw new IllegalStateException("Air fog ablation unrelated effect control active: " + effect);
        }
        for (String field : List.of("air_fog_ablation_source_qualification", "air_fog_ablation_effect_controls")) {
            if (!canonicalJsonHash(state.get(field)).equals(state.get(field + "_sha256").getAsString())) throw new IllegalStateException("Air fog ablation fingerprint differs: " + field);
        }
        if (!state.get("config/shaderlod-effects-client.toml_sha256").getAsString().matches("[0-9a-f]{64}")) throw new IllegalStateException("Air fog ablation effects config fingerprint unavailable");
        if (!state.get("config/shaderlod-effects-client.toml_sha256").equals(state.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/shaderlod-effects-client.toml").get("sha256"))) throw new IllegalStateException("Air fog ablation effects raw hash alias differs");
        semanticState(state, "air_fog_ablation");
        if (!airFogAblationConfigurationSemantics(state.getAsJsonObject("configuration_files")).equals(state.get("air_fog_ablation_configuration_semantics"))) throw new IllegalStateException("Air fog ablation configuration normalization differs");
        var window = state.getAsJsonObject("air_fog_ablation_window_state");
        if (!window.get("availability").getAsString().equals("available") || !window.get("focused").getAsString().equals("true")
                || !window.get("visible").getAsString().equals("true") || !window.get("iconified").getAsString().equals("false")) throw new IllegalStateException("Air fog ablation window unavailable/hidden/unfocused");
        requireInteger(window, "framebuffer_width", 2976); requireInteger(window, "framebuffer_height", 1672);
        requireInteger(window, "window_width", 1488); requireInteger(window, "window_height", 836);
        var pacing = window.getAsJsonObject("pacing");
        requireBoolean(pacing, "vsync", false); requireBoolean(pacing, "fullscreen", false); requireBoolean(pacing, "pause_on_lost_focus", false);
        requireInteger(pacing, "configured_framerate_limit", 260);
    }

    private JsonObject referenceInputState(String feature, List<SourceBinding> bindings) throws Exception {
        var state = benchmarkInputState();
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var controls = json.toJsonTree(ShaderLodEffectConfig.snapshot()).getAsJsonObject();
        state.getAsJsonObject("loaded_semantic_config").add("effects", controls.deepCopy());
        state.add(feature + "_effect_controls", controls);
        state.addProperty(feature + "_effect_controls_sha256", jsonHash(controls));
        state.addProperty("config/shaderlod-effects-client.toml_sha256", hash(directory.resolve("config/shaderlod-effects-client.toml")));
        var binding = json.toJsonTree(IrisAdapterRuntime.snapshot()).getAsJsonObject();
        state.add(feature + "_adapter_binding", binding);
        state.addProperty(feature + "_adapter_binding_sha256", jsonHash(binding));
        Path archive = directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName());
        // First settling boundary only. Every boundary still hashes the archive above and
        // rechecks all loaded options, so cached member pins cannot qualify changed bytes.
        if (qualifiedSource == null) {
            qualifiedSource = PackSourceReader.read(archive);
            qualifiedSourcePath = archive;
        } else if (!archive.equals(qualifiedSourcePath)) throw new IllegalStateException("Reference archive path changed");
        var source = qualifyReferenceSource(qualifiedSource, loadedOptions(state), bindings, feature);
        state.add(feature + "_source_qualification", source);
        state.addProperty(feature + "_source_qualification_sha256", jsonHash(source));
        state.addProperty(feature + "_dh_present", ModList.get().isLoaded("distanthorizons"));
        var client = Minecraft.getInstance(); var window = client.getWindow();
        state.addProperty(feature + "_camera_type", client.options.getCameraType().name());
        state.addProperty(feature + "_fov", client.options.fov().get());
        state.add(feature + "_window_state", json.toJsonTree(WindowStateTracker.readWindow(
                window.getWindow(), window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(),
                new WindowStateTracker.Pacing(client.options.pauseOnLostFocus, window.isFullscreen(),
                        client.options.framerateLimit().get(), client.options.enableVsync().get()))));
        return state;
    }

    private static java.util.Map<String, String> loadedOptions(JsonObject state) {
        var options = new java.util.TreeMap<String, String>();
        state.getAsJsonObject("effective_shader_options").entrySet().forEach(entry -> options.put(entry.getKey(), entry.getValue().getAsString()));
        return options;
    }

    static JsonObject qualifySpatialSource(SourceFingerprint source, java.util.Map<String, String> options) {
        return qualifyReferenceSource(source, options, PhotonSpatialReferenceAdapter.sourceBindings(), "spatial");
    }

    static JsonObject qualifyVolumeSource(SourceFingerprint source, java.util.Map<String, String> options) {
        return qualifyReferenceSource(source, options, PhotonReferenceAdapter.sourceBindings(), "volume");
    }

    private static JsonObject qualifyReferenceSource(SourceFingerprint source, java.util.Map<String, String> options,
                                                     List<SourceBinding> bindings, String feature) {
        var matches = bindings.stream().filter(binding -> binding.matches(source) && binding.acceptsOptions(options)).toList();
        if (matches.size() != 1) throw new IllegalStateException(feature + " exact container/member/loaded-option binding unavailable");
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var result = new JsonObject();
        result.addProperty("qualified", true);
        result.add("source_binding", json.toJsonTree(matches.getFirst()));
        result.add("source_fingerprint", json.toJsonTree(source));
        return result;
    }

    public static void validateSpatial(JsonObject state, boolean enabled) {
        validateReference(state, enabled, "spatial", PhotonSpatialReferenceAdapter.descriptor(),
                PhotonSpatialReferenceAdapter.sourceBindings(), PhotonSpatialReferenceAdapter.OPAQUE_SPATIAL_IRRADIANCE,
                Effect.OPAQUE_LIGHTING, new DistanceBands(32, 96, 8), 1, .25);
    }

    public static void validateVolume(JsonObject state, boolean enabled) {
        validateReference(state, enabled, "volume", PhotonReferenceAdapter.descriptor(),
                PhotonReferenceAdapter.sourceBindings(), PhotonReferenceAdapter.VOLUME_INTEGRATION,
                Effect.VOLUMETRICS, new DistanceBands(512, 1536, 128), .85, .75);
    }

    /** Safety/snapshot checks shared by two explicit, frozen Photon reference contracts. */
    private static void validateReference(JsonObject state, boolean enabled, String feature,
                                          AdapterDescriptor descriptor, List<SourceBinding> bindings, WorkKey operation,
                                          Effect selectedEffect, DistanceBands bands, double medium, double far) {
        validateBenchmarkControls(state, enabled);
        if (!state.get("config/shaderlod-effects-client.toml_sha256").getAsString().matches("[0-9a-f]{64}")) throw new IllegalStateException(feature + " effects config fingerprint unavailable");
        requireBoolean(state, feature + "_dh_present", false);
        requireInteger(state, "requested_chunks", 24); requireInteger(state, "effective_chunks", 24);
        if (state.get(feature + "_fov").getAsInt() < 1) throw new IllegalStateException(feature + " field of view unavailable");
        var json = new GsonBuilder().disableHtmlEscaping().create();
        var binding = state.getAsJsonObject(feature + "_adapter_binding");
        // AVAILABLE is the actual registered runtime resolver's source/options/protocol result.
        // Snapshot has only reason, descriptor and operations; no inferred protocol readback.
        if (!binding.get("reason").getAsString().equals("AVAILABLE")
                || !binding.get("descriptor").equals(json.toJsonTree(descriptor))
                || !binding.getAsJsonArray("operations").contains(json.toJsonTree(operation))) {
            throw new IllegalStateException(feature + " operation unavailable in actual adapter snapshot");
        }
        var source = state.getAsJsonObject(feature + "_source_qualification");
        var observed = json.fromJson(source.get("source_fingerprint"), SourceFingerprint.class);
        if (!source.equals(qualifyReferenceSource(observed, loadedOptions(state), bindings, feature))
                || !observed.sourceSha256().equals(state.get("actual_shader_sha256").getAsString())) {
            throw new IllegalStateException(feature + " source qualification differs from selected archive");
        }
        var controls = state.getAsJsonObject(feature + "_effect_controls");
        if (controls.size() != Effect.values().length || !controls.equals(state.getAsJsonObject("loaded_semantic_config").get("effects"))) throw new IllegalStateException("Incomplete " + feature + " effect config snapshot");
        for (Effect effect : Effect.values()) {
            var control = json.fromJson(controls.get(effect.name()), ShaderLodEffectConfig.Control.class);
            boolean selected = effect == selectedEffect;
            var policy = control.policy();
            if (control.repaired() || !control.requested().equals(policy)
                    || control.mode() != (selected ? ShaderLodEffectConfig.Mode.ADAPTIVE : ShaderLodEffectConfig.Mode.INHERIT)
                    || policy.enabled() != selected || !policy.preserveSalientLighting()
                    || policy.mediumWorkRatio() != (selected ? medium : 1) || policy.farWorkRatio() != (selected ? far : 1)
                    || selected && !policy.bands().equals(bands)) {
                throw new IllegalStateException(feature + " fixed effect policy differs: " + effect);
            }
        }
        for (String field : List.of(feature + "_effect_controls", feature + "_adapter_binding", feature + "_source_qualification")) {
            if (!jsonHash(state.getAsJsonObject(field)).equals(state.get(field + "_sha256").getAsString())) throw new IllegalStateException(feature + " fingerprint differs: " + field);
        }
        if (!state.get(feature + "_camera_type").getAsString().equals("FIRST_PERSON")) throw new IllegalStateException(feature + " requires fixed first-person view");
        var window = state.getAsJsonObject(feature + "_window_state");
        if (!window.get("availability").getAsString().equals("available") || window.get("focused").getAsString().equals("unknown")
                || !window.get("visible").getAsString().equals("true") || !window.get("iconified").getAsString().equals("false")) throw new IllegalStateException(feature + " window/focus unavailable or hidden");
        requireInteger(window, "framebuffer_width", state.get("framebuffer_width").getAsInt());
        requireInteger(window, "framebuffer_height", state.get("framebuffer_height").getAsInt());
        requireBoolean(window.getAsJsonObject("pacing"), "vsync", false);
    }

    static String jsonHash(JsonObject object) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                    new GsonBuilder().disableHtmlEscaping().create().toJson(object).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Three explicit frozen interventions; exact member/metadata verification, read-only. */
    public JsonObject qualifySsrCostArchive() throws Exception {
        Path path = directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName());
        String pin = hash(path);
        var members = new JsonObject();
        members.addProperty("LICENSE", "514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5");
        members.addProperty("shaderlod-build.json", "5653a52f590ce10cd75fbaff24f1ed053e49564ade7b4c79bb2747f69feed084");
        members.addProperty("shaders/include/shaderlod/lod.glsl", "2ac35e39e408d953e2d40769db4302aeff2fadd8e5884c47cfc757fa7d294b7f");
        members.addProperty("shaders/program/d4_deferred_shading.fsh", "d195159465a86f88b918c504f25cc56f5c987e27774906108e02993d24bbe000");
        String policy, metadataPath = null, probeSwitch = null, manifestPin = null;
        if (pin.equals(TERRAIN_PHOTON_SHA256)) {
            policy = "skip";
            members.addProperty("shaders/settings.glsl", "d1f571e5d96ecffdf7ebcfcf28a13d34dc5bfd5c3606702e06832f95f88aa3cd");
            members.addProperty("shaders/include/lighting/specular_lighting.glsl", "6a4a8c53675f7287cf28168913f3e3e2b7ce9bd9d2fb5755548a27927ee4d92d");
        } else if (pin.equals(SSR_FULL_SHA256)) {
            policy = "full"; metadataPath = "shaderlod-ssr-far-full-trace-probe.json"; probeSwitch = "PHOTON_SSR_FAR_FULL_TRACE_PROBE";
            manifestPin = "f801ab5eaa05268997ed9b705ed04839e1807e0f4250ea3a80d1f553c647442d";
            members.addProperty("shaders/settings.glsl", "33e39309f4e9a31990ce722c32cd56bbd981880f6447756f87bbbebebad69602");
            members.addProperty("shaders/include/lighting/specular_lighting.glsl", "d57fce9683b3675f60edb08dd7f52168833852c7b57e15196387d337e8938df8");
            members.addProperty(metadataPath, "30f3a1ffddbc8391a4f50536b8cd25b9e6982c826ac6156e90e5142da34c1c8b");
        } else if (pin.equals(SSR_REDUCED_SHA256)) {
            policy = "reduced"; metadataPath = "shaderlod-ssr-far-reduced-probe.json"; probeSwitch = "PHOTON_SSR_FAR_REDUCED_TRACE_PROBE";
            manifestPin = "86ebfa2c42b28571867caddc634df1667efb3d6abbc89c103a87a2f462c686fa";
            members.addProperty("shaders/settings.glsl", "a61e7f1c13a78fd3c6eb9ce72d9aec079b707b14c1ae2c3fb6251aa072de7299");
            members.addProperty("shaders/include/lighting/specular_lighting.glsl", "6db453ffc732600265d642d3fd4b7ff9a2c34fda66ec64010ec9f9e24afa471a");
            members.addProperty(metadataPath, "a05b241f23ccf5d40680061cf0d10ca73b6c8ae075fcdd721dedccd6ab286d60");
        } else throw new IllegalStateException("Unknown SSR cost source intervention; exact B15/full-enabled/reduced-enabled only");
        var result = new JsonObject();
        try (var archive = new java.util.zip.ZipFile(path.toFile())) {
            var observed = new JsonObject();
            for (var member : members.entrySet()) {
                var entry = archive.getEntry(member.getKey());
                if (entry == null) throw new IllegalStateException("SSR cost pinned member missing: " + member.getKey());
                String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(archive.getInputStream(entry).readAllBytes()));
                if (!actual.equals(member.getValue().getAsString())) throw new IllegalStateException("SSR cost member mismatch: " + member.getKey());
                observed.addProperty(member.getKey(), actual);
            }
            var baseline = com.google.gson.JsonParser.parseString(new String(archive.getInputStream(archive.getEntry("shaderlod-build.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (baseline.get("bridge_version").getAsInt() != 2 || !baseline.get("base_version").getAsString().equals("v1.3b")) throw new IllegalStateException("SSR cost baseline metadata mismatch");
            if (metadataPath != null) {
                var metadata = com.google.gson.JsonParser.parseString(new String(archive.getInputStream(archive.getEntry(metadataPath)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if (!metadata.get("arm").getAsString().equals("enabled") || !metadata.get("switch_enabled").getAsBoolean()
                        || !metadata.get("switch").getAsString().equals(probeSwitch) || !metadata.get("base_sha256").getAsString().equals(TERRAIN_PHOTON_SHA256)
                        || !metadata.get("manifest_sha256").getAsString().equals(manifestPin)) throw new IllegalStateException("SSR cost private metadata/gate mismatch");
                result.add("probe_metadata", metadata);
            }
            result.add("member_sha256", observed);
        }
        result.addProperty("archive_sha256", pin);
        result.addProperty("policy", policy);
        result.addProperty("qualified", true);
        return result;
    }

    public static void validateSsrCost(JsonObject state, boolean enabled) {
        validateEffects(state, enabled); // Preserves every existing no-terrain/CPU/config guard.
        requireBoolean(state, "ssr_cost_dh_present", false);
        requireInteger(state, "requested_chunks", 24);
        requireInteger(state, "effective_chunks", 24);
        requireInteger(state, "framebuffer_width", 2976);
        requireInteger(state, "framebuffer_height", 1672);
        if (!state.get("ssr_cost_camera_type").getAsString().equals("FIRST_PERSON")) throw new IllegalStateException("SSR cost requires fixed first-person view");
        for (String name : List.of("SHADERLOD_CLOUDS", "SHADERLOD_WATER", "SHADERLOD_FOG", "SHADERLOD_MATERIALS")) requireOption(state.getAsJsonObject("effective_shader_options"), name, "false");
        for (String name : List.of("SHADERLOD_SSR", "ENVIRONMENT_REFLECTIONS", "SKY_REFLECTIONS")) requireOption(state.getAsJsonObject("effective_shader_options"), name, "true");
        String pin = state.get("actual_shader_sha256").getAsString();
        var options = state.getAsJsonObject("effective_shader_options");
        for (String name : List.of("PHOTON_SSR_FAR_FULL_TRACE_PROBE", "PHOTON_SSR_FAR_REDUCED_TRACE_PROBE")) {
            boolean selected = pin.equals(SSR_FULL_SHA256) && name.equals("PHOTON_SSR_FAR_FULL_TRACE_PROBE")
                    || pin.equals(SSR_REDUCED_SHA256) && name.equals("PHOTON_SSR_FAR_REDUCED_TRACE_PROBE");
            if (selected) requireOption(options, name, "true");
            else if (options.has(name)) throw new IllegalStateException("Unexpected SSR cost private option: " + name);
        }
        if (!List.of(TERRAIN_PHOTON_SHA256, SSR_FULL_SHA256, SSR_REDUCED_SHA256).contains(pin)) throw new IllegalStateException("Unknown SSR cost archive");
        requirePose(state, "x", 500.5); requirePose(state, "y", 68.0); requirePose(state, "z", 660.5);
        requirePose(state, "yaw", 87.38); requirePose(state, "pitch", 3.735);
        requirePose(state, "rain", 0); requirePose(state, "thunder", 0);
        if (state.get("day_time").getAsLong() != 12500 || !state.get("dimension").getAsString().equals("minecraft:overworld")) throw new IllegalStateException("SSR cost sun/time/dimension changed");
        var window = state.getAsJsonObject("ssr_cost_window_state");
        if (!window.get("availability").getAsString().equals("available") || !window.get("visible").getAsString().equals("true") || !window.get("iconified").getAsString().equals("false")) throw new IllegalStateException("SSR cost window unavailable/hidden");
        requireBoolean(window.getAsJsonObject("pacing"), "vsync", false);
    }

    private static void requirePose(JsonObject state, String field, double expected) {
        double actual = state.get(field).getAsDouble();
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > .002) throw new IllegalStateException("SSR cost fixed pose/weather differs: " + field);
    }

    private static JsonObject terrainDhState() throws ReflectiveOperationException {
        // Public pinned DH API, outside frame hooks. OFF still records actual DH reach.
        Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
        Object configs = delayed.getField("configs").get(null);
        Object world = delayed.getField("worldProxy").get(null);
        if (configs == null || world == null) throw new IllegalStateException("Terrain control DH API unavailable");
        Object graphics = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig")
                .getMethod("graphics").invoke(configs);
        Class<?> graphicsApi = Class.forName("com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig");
        var getValue = Class.forName("com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue").getMethod("getValue");
        Object mode = getValue.invoke(graphicsApi.getMethod("renderingMode").invoke(graphics));
        Object reach = getValue.invoke(graphicsApi.getMethod("chunkRenderDistance").invoke(graphics));
        Object enabled = getValue.invoke(graphicsApi.getMethod("renderingEnabled").invoke(graphics));
        Object loaded = Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy")
                .getMethod("worldLoaded").invoke(world);
        if (!(reach instanceof Integer) || !(enabled instanceof Boolean) || !(loaded instanceof Boolean) || !(mode instanceof Enum<?>)) {
            throw new IllegalStateException("Terrain control DH API values unrecognized");
        }
        var result = new JsonObject();
        result.addProperty("world_loaded", (Boolean) loaded);
        result.addProperty("rendering_enabled", (Boolean) enabled);
        result.addProperty("rendering_mode", ((Enum<?>) mode).name());
        result.addProperty("chunks", (Integer) reach);
        return result;
    }

    public static void validateTerrainPrerequisites(JsonObject state) {
        requireBoolean(state, "mod_present", true);
        requireBoolean(state, "dh_present", true);
        requireBoolean(state, "shaders_in_use", true);
        requireBoolean(state, "fallback", false);
        requireBoolean(state, "hide_gui", true);
        requireBoolean(state.getAsJsonObject("lod_settings"), "enabled", true);
        requireBoolean(state.getAsJsonObject("lod_settings"), "debug", false);
        requireBoolean(state, "terrain_confirmation", true);
        requireInteger(state, "requested_chunks", 24);
        requireInteger(state, "framebuffer_width", 2976);
        requireInteger(state, "framebuffer_height", 1672);
        if (!state.get("terrain_camera_type").getAsString().equals("FIRST_PERSON")) throw new IllegalStateException("Terrain control requires fixed first-person camera");
        if (!state.get("actual_shader_sha256").getAsString().equals(TERRAIN_PHOTON_SHA256)) throw new IllegalStateException("Terrain control requires exact B15 archive");
        for (String name : List.of("shadow_culling_configured", "shadow_culling_active", "shadow_lists_configured", "shadow_lists_active", "shadow_coherence_configured", "shadow_coherence_active")) requireBoolean(state, name, false);
        var semantic = state.getAsJsonObject("loaded_semantic_config");
        requireBoolean(semantic.getAsJsonObject("lod"), "enabled", true);
        requireBoolean(semantic.getAsJsonObject("lod"), "debug", false);
        var terrain = semantic.getAsJsonObject("terrain");
        requireBoolean(terrain, "warmSourceConfirmed", true);
        requireInteger(terrain, "vanillaChunkCap", 16);
        requireInteger(terrain, "warmupSeconds", 30);
        for (String name : List.of("optimizeShadowCulling", "separateShadowVisibilityLists", "shadowFrustumCoherence")) requireBoolean(semantic.getAsJsonObject("rendering"), name, false);
        var options = state.getAsJsonObject("effective_shader_options");
        for (String name : List.of("SHADERLOD_CLOUDS", "SHADERLOD_WATER", "SHADERLOD_FOG", "SHADERLOD_SSR", "SHADERLOD_MATERIALS", "SHADERLOD_USE_MOD_CONFIG", "SHADERLOD_PRESERVE_LIGHTING")) requireOption(options, name, "true");
        for (String name : List.of("SHADERLOD_CACHED_SKY", "SHADERLOD_WATER_ANALYTIC", "SHADERLOD_DEBUG")) requireOption(options, name, "false");
        var dh = state.getAsJsonObject("terrain_dh_state");
        requireBoolean(dh, "world_loaded", true);
        requireBoolean(dh, "rendering_enabled", true);
        requireInteger(dh, "chunks", 128);
        if (!dh.get("rendering_mode").getAsString().equals("DEFAULT")) throw new IllegalStateException("Terrain control requires normal DH rendering");
        var policy = state.getAsJsonObject("geometry_policy");
        requireInteger(policy, "cap", 16);
        requireInteger(policy, "requestedChunks", 24);
        requireInteger(policy, "warmupTicks", 600);
        var window = state.getAsJsonObject("terrain_window_state");
        if (!window.get("availability").getAsString().equals("available") || !window.get("visible").getAsString().equals("true") || !window.get("iconified").getAsString().equals("false")) throw new IllegalStateException("Terrain control window unavailable/hidden");
        requireInteger(window, "framebuffer_width", 2976);
        requireInteger(window, "framebuffer_height", 1672);
        var pacing = window.getAsJsonObject("pacing");
        requireBoolean(pacing, "vsync", false);
        if (pacing.get("pause_on_lost_focus").getAsBoolean() && !window.get("focused").getAsString().equals("true")) throw new IllegalStateException("Unfocused paused terrain control");
    }

    public static void validateTerrain(JsonObject state, boolean enabled) {
        validateTerrainPrerequisites(state);
        requireBoolean(state, "terrain_configured", enabled);
        requireBoolean(state, "terrain_active", enabled);
        requireBoolean(state.getAsJsonObject("loaded_semantic_config").getAsJsonObject("terrain"), "enabled", enabled);
        requireInteger(state, "effective_chunks", enabled ? 16 : 24);
        var policy = state.getAsJsonObject("geometry_policy");
        requireBoolean(policy, "active", enabled);
        requireInteger(policy, "effectiveChunks", enabled ? 16 : 24);
        String reason = enabled ? "DH terrain cap active" : "terrain policy disabled";
        if (!policy.get("reason").getAsString().equals(reason)) throw new IllegalStateException("Terrain policy reason differs");
        if (!policy.get("detail").getAsString().isEmpty()) throw new IllegalStateException("Terrain policy API failure detail");
        int ticks = policy.get("stableTicks").getAsInt();
        if (enabled ? ticks < 600 : ticks != 0) throw new IllegalStateException("Terrain policy has not settled");
    }

    private static void requireInteger(JsonObject object, String name, int expected) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.get(name).getAsJsonPrimitive().isNumber() || object.get(name).getAsDouble() != expected) {
            throw new IllegalStateException("Required terrain-control integer: " + name + "=" + expected);
        }
    }

    public static void validateAlloff(JsonObject state) {
        if (!state.has("mod_present") || !state.get("mod_present").getAsBoolean()) throw new IllegalStateException("In-process control requires ShaderLOD");
        if (!state.get("shaders_in_use").getAsBoolean() || state.get("fallback").getAsBoolean()) throw new IllegalStateException("Iris pipeline unavailable");
        if (!state.has("hide_gui") || !state.get("hide_gui").getAsBoolean()) throw new IllegalStateException("In-process control requires configured hidden HUD");
        for (String name : java.util.List.of("terrain_configured", "terrain_confirmation", "terrain_active", "shadow_culling_configured", "shadow_culling_active", "shadow_lists_configured", "shadow_lists_active", "shadow_coherence_configured", "shadow_coherence_active")) {
            if (state.get(name).getAsBoolean()) throw new IllegalStateException("In-process control feature must stay off: " + name);
        }
        if (state.getAsJsonObject("lod_settings").get("debug").getAsBoolean()) throw new IllegalStateException("Mod debug must stay off");
        var options = state.getAsJsonObject("effective_shader_options");
        for (String name : java.util.List.of("SHADERLOD_CLOUDS", "SHADERLOD_WATER", "SHADERLOD_FOG", "SHADERLOD_SSR", "SHADERLOD_MATERIALS", "SHADERLOD_CACHED_SKY", "SHADERLOD_WATER_ANALYTIC", "SHADERLOD_DEBUG")) {
            if (!options.has(name) || !options.get(name).getAsString().equals("false")) throw new IllegalStateException("Alloff option required: " + name);
        }
        for (String name : java.util.List.of("SHADERLOD_USE_MOD_CONFIG", "SHADERLOD_PRESERVE_LIGHTING")) {
            if (!options.has(name) || !options.get(name).getAsString().equals("true")) throw new IllegalStateException("Required bridge/lighting option: " + name);
        }
    }

    /** Fixed pack options may activate effects; only the global Iris bridge input changes. */
    public static void validateEffects(JsonObject state, boolean expectedEnabled) {
        validateBenchmarkControls(state, expectedEnabled);
        var options = state.getAsJsonObject("effective_shader_options");
        for (String name : List.of("SHADERLOD_CLOUDS", "SHADERLOD_WATER", "SHADERLOD_FOG", "SHADERLOD_SSR", "SHADERLOD_MATERIALS")) {
            if (options == null || !options.has(name) || !options.get(name).isJsonPrimitive()
                    || !options.get(name).getAsJsonPrimitive().isString()
                    || !List.of("true", "false").contains(options.get(name).getAsString())) {
                throw new IllegalStateException("Complete boolean effect option required: " + name);
            }
        }
        for (String name : List.of("SHADERLOD_CACHED_SKY", "SHADERLOD_WATER_ANALYTIC", "SHADERLOD_DEBUG")) {
            requireOption(options, name, "false");
        }
        for (String name : List.of("SHADERLOD_USE_MOD_CONFIG", "SHADERLOD_PRESERVE_LIGHTING")) {
            requireOption(options, name, "true");
        }
    }

    /** Common safety checks shared by the existing effects guard and explicit spatial diagnostic. */
    private static void validateBenchmarkControls(JsonObject state, boolean expectedEnabled) {
        requireBoolean(state, "mod_present", true);
        requireBoolean(state, "shaders_in_use", true);
        requireBoolean(state, "fallback", false);
        requireBoolean(state, "hide_gui", true);
        for (String name : List.of("terrain_configured", "terrain_confirmation", "terrain_active", "shadow_culling_configured", "shadow_culling_active", "shadow_lists_configured", "shadow_lists_active", "shadow_coherence_configured", "shadow_coherence_active")) {
            requireBoolean(state, name, false);
        }
        var lod = state.getAsJsonObject("lod_settings");
        requireBoolean(lod, "enabled", expectedEnabled);
        requireBoolean(lod, "debug", false);
        var semantic = state.getAsJsonObject("loaded_semantic_config");
        var loadedLod = semantic.getAsJsonObject("lod");
        requireBoolean(loadedLod, "enabled", expectedEnabled);
        requireBoolean(loadedLod, "debug", false);
        var terrain = semantic.getAsJsonObject("terrain");
        requireBoolean(terrain, "enabled", false);
        requireBoolean(terrain, "warmSourceConfirmed", false);
        var rendering = semantic.getAsJsonObject("rendering");
        for (String name : List.of("optimizeShadowCulling", "separateShadowVisibilityLists", "shadowFrustumCoherence")) {
            requireBoolean(rendering, name, false);
        }
    }

    private static void requireBoolean(JsonObject object, String name, boolean expected) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.get(name).getAsJsonPrimitive().isBoolean() || object.get(name).getAsBoolean() != expected) {
            throw new IllegalStateException("Required effects-control state: " + name + "=" + expected);
        }
    }

    private static void requireOption(JsonObject options, String name, String expected) {
        if (options == null || !options.has(name) || !options.get(name).isJsonPrimitive()
                || !options.get(name).getAsJsonPrimitive().isString() || !options.get(name).getAsString().equals(expected)) {
            throw new IllegalStateException("Required effects-control option: " + name + "=" + expected);
        }
    }

    public static void validateCoherence(JsonObject state, boolean expected) {
        if (!state.has("mod_present") || !state.get("mod_present").getAsBoolean()) throw new IllegalStateException("Coherence control requires ShaderLOD present");
        if (!state.get("shaders_in_use").getAsBoolean() || state.get("fallback").getAsBoolean()) throw new IllegalStateException("Iris pipeline unavailable");
        if (!state.get("hide_gui").getAsBoolean()) throw new IllegalStateException("Coherence control requires hidden HUD");
        if (!state.get("actual_shader_sha256").getAsString().equals("120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead")) throw new IllegalStateException("Coherence control requires pinned original Photon archive");
        if (!state.getAsJsonObject("lod_settings").get("enabled").getAsBoolean()) throw new IllegalStateException("Global enabled must remain true in coherence control");
        if (!state.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").get("enabled").getAsBoolean()) throw new IllegalStateException("Loaded global enabled must remain true");
        if (state.getAsJsonObject("lod_settings").get("debug").getAsBoolean()) throw new IllegalStateException("Debug must remain false");
        for (String name : List.of("terrain_configured", "terrain_confirmation", "terrain_active", "shadow_culling_configured", "shadow_culling_active", "shadow_lists_configured", "shadow_lists_active")) {
            if (state.get(name).getAsBoolean()) throw new IllegalStateException("Unrelated control active: " + name);
        }
        for (String name : List.of("shadow_coherence_configured", "shadow_coherence_active")) {
            if (state.get(name).getAsBoolean() != expected) throw new IllegalStateException("Coherence did not reach expected settled state: " + name);
        }
        if (state.getAsJsonObject("loaded_semantic_config").getAsJsonObject("rendering").get("shadowFrustumCoherence").getAsBoolean() != expected) throw new IllegalStateException("Loaded coherence config mismatch");
    }

    public static boolean gpuBackendAccepted(String backend, boolean requested) {
        return !requested || backend.equals("TIMESTAMP") || backend.equals("ELAPSED_ALTERNATING");
    }

    public void validateNoAtmosphereGuard() throws Exception {
        try (var archive = new java.util.zip.ZipFile(directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName()).toFile())) {
            var entry = archive.getEntry("shaders/program/d4_deferred_shading.fsh");
            if (entry == null) throw new IllegalStateException("Pinned Photon lighting program missing");
            String source = new String(archive.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (source.contains("shaderlod_needs_atmosphere(")) throw new IllegalStateException("Atmosphere guard changes global-flag work; use the no-atmosphere candidate");
        }
    }

    public static void validateWetness(JsonObject temporal, boolean capture) {
        var wetness = temporal.getAsJsonObject("wetness");
        if (!wetness.get("mode").getAsString().equals("fixed")) throw new IllegalStateException("In-process control requires explicit fixed shader wetness from startup");
        float target = wetness.get("fixedWetness").getAsFloat();
        if (!Float.isFinite(target) || target < 0 || target > 1) throw new IllegalStateException("Invalid fixed wetness target");
        long observed = 0;
        for (var element : wetness.getAsJsonArray("suppliers")) {
            var supplier = element.getAsJsonObject();
            if (supplier.get("supplierReads").getAsLong() == 0) continue;
            observed++;
            if (!supplier.has("appliedWetness") || !matchesWetness(supplier.get("appliedWetness").getAsFloat(), target)) throw new IllegalStateException("Unexpected applied wetness");
            if (capture && supplier.get("measurementReads").getAsLong() > 0) {
                for (String name : java.util.List.of("appliedMin", "appliedMax")) {
                    if (!supplier.has(name) || !matchesWetness(supplier.get(name).getAsFloat(), target)) throw new IllegalStateException("Wetness changed within phase");
                }
            }
        }
        if (observed == 0 || capture && wetness.get("measurementReads").getAsLong() == 0) throw new IllegalStateException("Wetness supplier observations missing");
    }
    static void validateAirFogAblationTemporal(JsonObject temporal, boolean capture) {
        validateWetness(temporal, capture);
        if (temporal.getAsJsonObject("wetness").get("fixedWetness").getAsDouble() != 1
                || !"normal; counter/frame-time remain real".equals(temporal.get("clock_policy").getAsString()))
            throw new IllegalStateException("Air fog ablation requires wetness1 and normal clocks");
        for (var field : java.util.Map.of("day_time_source", 12500.0, "world_time_source", 12500.0,
                "world_day_source", 0.0, "photon_world_age_source", 625.0, "rain_strength_source", 1.0).entrySet()) {
            if (!temporal.has(field.getKey()) || temporal.get(field.getKey()).getAsDouble() != field.getValue())
                throw new IllegalStateException("Air fog ablation CPU temporal source differs: " + field.getKey());
        }
        for (String field : List.of("frame_counter_source", "frame_time_counter_source", "frame_time_source")) {
            double value = temporal.get(field).getAsDouble();
            if (!Double.isFinite(value) || value <= 0) throw new IllegalStateException("Air fog ablation advancing clock unavailable: " + field);
        }
    }

    static void validateAirFogAblationClocks(JsonObject previous, JsonObject current) {
        for (String field : List.of("frame_counter_source", "frame_time_counter_source")) {
            double before = previous.get(field).getAsDouble(), after = current.get(field).getAsDouble();
            if (!Double.isFinite(before) || !Double.isFinite(after) || after <= before)
                throw new IllegalStateException("Air fog ablation normal clock did not advance: " + field);
        }
    }

    private static boolean matchesWetness(float value, float target) { return Float.isFinite(value) && Math.abs(value - target) <= 1e-6; }
    public static String hash(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(path)) {
            byte[] block = new byte[1024 * 1024];
            int count;
            while ((count = stream.read(block)) >= 0) digest.update(block, 0, count);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    /** Read Iris's loaded option values, including defaults it removes from the .txt file. */
    public static JsonObject effectiveOptions() {
        var options = Iris.getCurrentPack().orElseThrow(() -> new IllegalStateException("No loaded Iris shader pack")).getShaderPackOptions();
        var values = options.getOptionValues();
        var sorted = new java.util.TreeMap<String, String>();
        for (String name : options.getOptionSet().getBooleanOptions().keySet()) sorted.put(name, Boolean.toString(values.getBooleanValueOrDefault(name)));
        for (String name : options.getOptionSet().getStringOptions().keySet()) sorted.put(name, values.getStringValueOrDefault(name));
        return new GsonBuilder().disableHtmlEscaping().create().toJsonTree(sorted).getAsJsonObject();
    }

    /** Advancing inputs deliberately live outside the strict fixed-input comparison. */
    public JsonObject temporalInputs() {
        var client = Minecraft.getInstance();
        var result = new JsonObject();
        result.add("wetness", new GsonBuilder().create().toJsonTree(ShaderInputTracker.snapshot()));
        result.addProperty("frame_counter_source", net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt());
        result.addProperty("frame_time_counter_source", net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getFrameTimeCounter());
        result.addProperty("frame_time_source", net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getLastFrameTime());
        long dayTime = client.level.getDayTime();
        var dimension = Iris.getCurrentDimension();
        int worldTime = (int) (dimension == net.irisshaders.iris.shaderpack.DimensionId.END || dimension == net.irisshaders.iris.shaderpack.DimensionId.NETHER
                ? dayTime % 24000 : client.level.dimensionType().fixedTime().orElse(dayTime % 24000));
        int worldDay = (int) (dayTime / 24000);
        result.addProperty("day_time_source", dayTime);
        result.addProperty("world_time_source", worldTime);
        result.addProperty("world_day_source", worldDay);
        result.addProperty("photon_world_age_source", ((worldDay % 128) * 24000.0 + worldTime) / 20.0);
        result.addProperty("moon_phase_source", client.level.getMoonPhase());
        result.addProperty("rain_strength_source", Math.clamp(client.level.getRainLevel(net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.getTickDelta()), 0.0f, 1.0f));
        result.addProperty("clock_policy", "normal; counter/frame-time remain real");
        return result;
    }

    /** Capture once at each boundary; never add hashing or pipeline checks to frame hooks. */
    public JsonObject inputState() throws Exception {
        var client = Minecraft.getInstance();
        var state = new JsonObject();
        state.addProperty("hide_gui", client.options.hideGui);
        state.addProperty("gui_scale", client.options.guiScale().get());
        state.addProperty("framebuffer_width", client.getWindow().getWidth());
        state.addProperty("framebuffer_height", client.getWindow().getHeight());
        state.addProperty("requested_chunks", client.options.renderDistance().get());
        state.addProperty("effective_chunks", client.options.getEffectiveRenderDistance());
        state.addProperty("shader_pack", Iris.getCurrentPackName());
        state.addProperty("shaders_in_use", IrisApi.getInstance().isShaderPackInUse());
        state.addProperty("fallback", Iris.isFallback());
        JsonObject options = effectiveOptions();
        state.add("effective_shader_options", options);
        String canonical = new GsonBuilder().disableHtmlEscaping().create().toJson(options);
        state.addProperty("effective_shader_options_sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        state.addProperty("dimension", client.level.dimension().location().toString());
        state.addProperty("day_time", client.level.getDayTime());
        state.addProperty("rain", client.level.getRainLevel(1));
        state.addProperty("thunder", client.level.getThunderLevel(1));
        var camera = client.getCameraEntity();
        state.addProperty("x", camera.getX()); state.addProperty("y", camera.getY()); state.addProperty("z", camera.getZ());
        state.addProperty("yaw", camera.getYRot()); state.addProperty("pitch", camera.getXRot());
        if (ModList.get().isLoaded("shaderlod")) {
            state.add("lod_settings", new GsonBuilder().create().toJsonTree(com.pp.shaderlod.ShaderLodConfig.snapshot()));
            state.addProperty("lod_preset", com.pp.shaderlod.ShaderLodConfig.PRESET.get().name());
            state.addProperty("terrain_active", com.pp.shaderlod.GeometryLodController.status().active());
        }
        for (String file : List.of("config/iris.properties", "config/DistantHorizons.toml", "config/shaderlod-client.toml")) {
            Path path = directory.resolve(file);
            if (Files.exists(path)) state.addProperty(file + "_sha256", hash(path));
        }
        Path control = directory.getParent().resolve("control.json");
        if (Files.exists(control)) state.addProperty("control_sha256", hash(control));
        return state;
    }
}
