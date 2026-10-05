package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.timing.ShaderInputTracker;
import com.pp.shaderlod.dev.timing.WindowStateTracker;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shared boundary evidence; no additional uniform/smoother evaluation or GL readback. */
public final class WorkloadInputs {
    public static final String TERRAIN_PHOTON_SHA256 = "b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c";
    public static final String SSR_FULL_SHA256 = "41ecbee8b72039654fb0d25b16a841e3a2f4500025ec918687f94c0b8cba1555";
    public static final String SSR_REDUCED_SHA256 = "636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83";
    private final Path directory;
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
        return copy;
    }

    public static JsonObject semanticState(JsonObject state, String feature) {
        JsonObject copy = semanticState(state);
        if (feature.equals("terrain")) copy.getAsJsonObject("geometry_policy").remove("stableTicks");
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
