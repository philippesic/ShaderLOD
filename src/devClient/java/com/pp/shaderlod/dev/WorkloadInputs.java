package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.timing.ShaderInputTracker;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shared boundary evidence; no additional uniform/smoother evaluation or GL readback. */
public final class WorkloadInputs {
    private final Path directory;
    public WorkloadInputs(Path directory) { this.directory = directory; }

    public static JsonObject withoutEnabled(JsonObject state) {
        JsonObject copy = semanticState(state);
        if (copy.has("lod_settings")) copy.getAsJsonObject("lod_settings").remove("enabled");
        if (copy.has("loaded_semantic_config")) copy.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").remove("enabled");
        return copy;
    }

    public static JsonObject withoutFeature(JsonObject state, String feature) {
        if (feature.equals("global")) return withoutEnabled(state);
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
        return copy;
    }

    public JsonObject benchmarkInputState() throws Exception {
        var state = inputState();
        var client = Minecraft.getInstance();
        state.addProperty("hide_gui", client.options.hideGui);
        state.addProperty("gui_scale", client.options.guiScale().get());
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
