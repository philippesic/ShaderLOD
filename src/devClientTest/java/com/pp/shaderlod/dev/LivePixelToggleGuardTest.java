package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.ShaderLodEffectConfig;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.WindowStateTracker;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.SourceFingerprint;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Actual pinned source/map plus model boundary fixtures; no Minecraft or GPU calls. */
public final class LivePixelToggleGuardTest {
    private static final com.google.gson.Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static SourceFingerprint source;
    private static Map<String, String> options;
    private static JsonObject configuration;
    private static String controlSha256;
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        source = PackSourceReader.read(Path.of("build/live-pixel-budget-enabled.zip"));
        var inventory = JsonParser.parseString(Files.readString(Path.of("build/live-pixel-runtime-r1/option-inventory.json"))).getAsJsonObject();
        options = new java.util.TreeMap<>();
        inventory.getAsJsonObject("live_fixed_taau").getAsJsonObject("options").entrySet().forEach(e -> options.put(e.getKey(), e.getValue().getAsString()));
        configuration = ConfigurationFilesEvidence.capture(Path.of("run-workloads/live-pixel-runtime-r1/global-off/control"));
        controlSha256 = WorkloadInputs.hash(Path.of("run-workloads/live-pixel-runtime-r1/global-off/control.json"));
        JsonObject reference = null;
        for (int phase = 0; phase < 4; phase++) {
            boolean enabled = phase % 2 == 1;
            var state = state(enabled);
            // Model real async persistence changing only the boolean token.
            if (enabled) changePersistedGlobal(state, true);
            WorkloadInputs.validateLivePixel(state, enabled);
            var semantic = WorkloadInputs.withoutFeature(state, "live_pixel");
            if (reference != null && !reference.equals(semantic)) throw new AssertionError("More than global config changed");
            reference = semantic;
            var window = new WindowStateTracker();
            window.observeCapture(window(), 0, 1); window.observeCapture(window(), 20, 601);
            BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(window.snapshot()).getAsJsonObject(), state.getAsJsonObject("live_pixel_window_state"));
            checks++;
        }
        reject(s -> s.getAsJsonObject("lod_settings").addProperty("enabled", true));
        reject(s -> s.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").addProperty("enabled", true));
        reject(s -> s.addProperty("actual_shader_sha256", "0".repeat(64)));
        reject(s -> s.getAsJsonObject("effective_shader_options").remove("SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED"));
        reject(s -> s.getAsJsonObject("effective_shader_options").addProperty("UNEXPECTED_HELPER", "true"));
        for (String key : List.of("TAA", "TAAU", "SHADERLOD_LIVE_PIXEL_BUDGET", "SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED")) reject(s -> s.getAsJsonObject("effective_shader_options").addProperty(key, "false"));
        reject(s -> s.getAsJsonObject("live_pixel_source_qualification").getAsJsonObject("source_fingerprint").getAsJsonObject("memberSha256").addProperty("LICENSE", "0".repeat(64)));
        var unknownMembers = new java.util.TreeMap<>(source.memberSha256()); unknownMembers.put("EXTRA", "0".repeat(64));
        expectFailure(() -> WorkloadInputs.qualifyLivePixelSource(new SourceFingerprint(source.sourceSha256(), unknownMembers), options));
        var wrongMember = new java.util.TreeMap<>(source.memberSha256()); wrongMember.put("shaders/program/d1_clouds.fsh", "0".repeat(64));
        expectFailure(() -> WorkloadInputs.qualifyLivePixelSource(new SourceFingerprint(source.sourceSha256(), wrongMember), options));
        expectFailure(() -> WorkloadInputs.qualifyLivePixelSource(new SourceFingerprint("0".repeat(64), source.memberSha256()), options));
        reject(s -> s.addProperty("live_pixel_dh_present", true));
        reject(s -> s.addProperty("shadow_culling_active", true));
        reject(s -> s.addProperty("day_time", 6000));
        reject(s -> s.addProperty("requested_chunks", 16));
        reject(s -> s.addProperty("framebuffer_width", 1488));
        reject(s -> s.addProperty("gui_scale", 3));
        reject(s -> s.addProperty("live_pixel_camera_type", "THIRD_PERSON_BACK"));
        reject(s -> s.addProperty("x", 501));
        reject(s -> s.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").addProperty("near", 64));
        reject(s -> s.getAsJsonObject("live_pixel_window_state").addProperty("focused", "unknown"));
        reject(s -> s.getAsJsonObject("live_pixel_window_state").getAsJsonObject("pacing").addProperty("vsync", true));
        reject(s -> s.getAsJsonObject("live_pixel_effect_controls").remove("VOLUMETRICS"));
        var adaptive = state(false); adaptive.getAsJsonObject("live_pixel_effect_controls").getAsJsonObject("VOLUMETRICS").addProperty("mode", "ADAPTIVE"); resyncControls(adaptive);
        expectFailure(() -> WorkloadInputs.validateLivePixel(adaptive, false));
        var ratio = state(false); ratio.getAsJsonObject("live_pixel_effect_controls").getAsJsonObject("VOLUMETRICS").getAsJsonObject("policy").addProperty("farWorkRatio", .75); resyncControls(ratio);
        expectFailure(() -> WorkloadInputs.validateLivePixel(ratio, false));
        reject(s -> s.getAsJsonObject("configuration_files").addProperty("complete", false));
        reject(s -> s.getAsJsonObject("configuration_files").getAsJsonObject("files").remove("config/iris.properties"));
        for (String replacement : List.of("enabled = 1", "enabled = false\nenabled = true")) {
            var capture = configuration.deepCopy();
            var main = capture.getAsJsonObject("files").getAsJsonObject("config/shaderlod-client.toml");
            main.addProperty("utf8", "[lod]\n" + replacement + "\n[terrain]\nenabled=false\n");
            expectFailure(() -> WorkloadInputs.livePixelConfigurationSemantics(capture));
        }
        var unrelated = state(false);
        unrelated.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("options.txt").addProperty("sha256", "0".repeat(64));
        unrelated.add("live_pixel_configuration_semantics", WorkloadInputs.livePixelConfigurationSemantics(unrelated.getAsJsonObject("configuration_files")));
        if (WorkloadInputs.withoutFeature(unrelated, "live_pixel").equals(WorkloadInputs.withoutFeature(state(false), "live_pixel"))) throw new AssertionError("Unrelated settings hash was ignored");
        checks++;
        var mainOther = state(false);
        var file = mainOther.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/shaderlod-client.toml");
        file.addProperty("utf8", file.get("utf8").getAsString().replace("near = 32.0", "near = 64.0").replace("near=32.0", "near=64.0"));
        refreshRawRecord(file);
        var backup = mainOther.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/shaderlod-client-1.toml.bak");
        if (backup != null) {
            backup.addProperty("utf8", backup.get("utf8").getAsString().replace("near = 32.0", "near = 64.0").replace("near=32.0", "near=64.0"));
            refreshRawRecord(backup);
        }
        mainOther.add("live_pixel_configuration_semantics", WorkloadInputs.livePixelConfigurationSemantics(mainOther.getAsJsonObject("configuration_files")));
        if (WorkloadInputs.withoutFeature(mainOther, "live_pixel").equals(WorkloadInputs.withoutFeature(state(false), "live_pixel"))) throw new AssertionError("Other main settings were ignored");
        checks++;
        var windowChanged = new WindowStateTracker(); windowChanged.observeCapture(window(), 0, 1);
        windowChanged.observeCapture(new WindowStateTracker.State("available", "false", "false", "true", 1488, 836, 2976, 1672, window().pacing(), "fixture"), 1, 61);
        expectFailure(() -> BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(windowChanged.snapshot()).getAsJsonObject(), JSON.toJsonTree(window()).getAsJsonObject()));
        var samples = new java.util.ArrayList<GpuTimers.Sample>();
        for (int index = 0; index < 60; index++) samples.add(new GpuTimers.Sample(GpuTimers.FRAME, 12, index + 1, 1));
        BenchmarkSession.validateReferenceGpuSamples(samples, true); checks++;
        samples.removeLast(); expectFailure(() -> BenchmarkSession.validateReferenceGpuSamples(samples, true));
        samples.add(new GpuTimers.Sample(GpuTimers.FRAME, Double.NaN, 60, 1)); expectFailure(() -> BenchmarkSession.validateReferenceGpuSamples(samples, true));
        recipe();
        if (arguments.length > 0) Files.writeString(Path.of(arguments[0]), JSON.toJson(state(false)));
        if (arguments.length > 1) {
            var capture = new WindowStateTracker(); capture.observeCapture(window(), 0, 1); capture.observeCapture(window(), 20, 601);
            Files.writeString(Path.of(arguments[1]), JSON.toJson(capture.snapshot()));
        }
        if (arguments.length > 2) Files.writeString(Path.of(arguments[2]), JSON.toJson(WorkloadInputs.withoutFeature(state(false), "live_pixel")));
        System.out.println("Live pixel guard: " + checks + " exact-source/options/config/four-phase/window/sample/recipe checks passed; no runtime claims");
    }

    private static void recipe() {
        var previous = new java.util.HashMap<String, String>();
        var values = Map.of("gpuSampleInterval", "5", "fixedWetness", "0", "cpuLayerTiming", "false", "gpuPassTiming", "false", "gpuAttribution", "false");
        values.forEach((key, value) -> { previous.put(key, System.getProperty("shaderlod.dev." + key)); System.setProperty("shaderlod.dev." + key, value); });
        try {
            BenchmarkSession.validateLivePixelRecipe(30, 8, 20, false, true, true); checks++;
            expectFailure(() -> BenchmarkSession.validateLivePixelRecipe(29, 8, 20, false, true, true));
            expectFailure(() -> BenchmarkSession.validateLivePixelRecipe(30, 7.99, 20, false, true, true));
            expectFailure(() -> BenchmarkSession.validateLivePixelRecipe(30, 8, 19.99, false, true, true));
            expectFailure(() -> BenchmarkSession.validateLivePixelRecipe(30, 8, 20, true, true, true));
            expectFailure(() -> BenchmarkSession.validateLivePixelRecipe(30, 8, 20, false, false, true));
            for (String key : values.keySet()) {
                System.setProperty("shaderlod.dev." + key, key.equals("gpuSampleInterval") ? "1" : key.equals("fixedWetness") ? ".5" : "true");
                expectFailure(() -> BenchmarkSession.validateLivePixelRecipe(30, 8, 20, false, true, true));
                System.setProperty("shaderlod.dev." + key, values.get(key));
            }
            for (String name : List.of("cloudSamplerProbe", "pixelCoverage", "pixelCoverageOnly")) {
                String previousProbe = System.getProperty("shaderlod.dev." + name);
                try {
                    System.setProperty("shaderlod.dev." + name, "true");
                    expectFailure(() -> BenchmarkSession.validateLivePixelRecipe(30, 8, 20, false, true, true));
                } finally {
                    if (previousProbe == null) System.clearProperty("shaderlod.dev." + name);
                    else System.setProperty("shaderlod.dev." + name, previousProbe);
                }
            }
        } finally {
            previous.forEach((key, value) -> { if (value == null) System.clearProperty("shaderlod.dev." + key); else System.setProperty("shaderlod.dev." + key, value); });
        }
    }

    private static void changePersistedGlobal(JsonObject state, boolean enabled) {
        var file = state.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("config/shaderlod-client.toml");
        String before = file.get("utf8").getAsString();
        String after = before.replaceFirst("(?m)(^\\h*enabled\\h*=\\h*)false", "$1" + enabled);
        file.addProperty("utf8", after); file.addProperty("bytes", after.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        try { file.addProperty("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(after.getBytes(java.nio.charset.StandardCharsets.UTF_8)))); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
        state.addProperty("config/shaderlod-client.toml_sha256", file.get("sha256").getAsString());
        state.add("live_pixel_configuration_semantics", WorkloadInputs.livePixelConfigurationSemantics(state.getAsJsonObject("configuration_files")));
    }

    private static void refreshRawRecord(JsonObject file) {
        byte[] bytes = file.get("utf8").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        file.addProperty("bytes", bytes.length);
        try { file.addProperty("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
    }

    private static void resyncControls(JsonObject state) {
        var controls = state.getAsJsonObject("live_pixel_effect_controls");
        state.getAsJsonObject("loaded_semantic_config").add("effects", controls.deepCopy());
        state.addProperty("live_pixel_effect_controls_sha256", WorkloadInputs.canonicalJsonHash(controls));
    }

    private static void reject(Consumer<JsonObject> mutation) { var state = state(false); mutation.accept(state); expectFailure(() -> WorkloadInputs.validateLivePixel(state, false)); }
    private static void expectFailure(Runnable action) { try { action.run(); } catch (RuntimeException expected) { checks++; return; } throw new AssertionError("Invalid fixture accepted"); }
    private static WindowStateTracker.State window() { return new WindowStateTracker.State("available", "true", "false", "true", 1488, 836, 2976, 1672, new WindowStateTracker.Pacing(false, false, 260, false), "fixture"); }

    private static JsonObject state(boolean enabled) {
        var state = new JsonObject();
        state.addProperty("live_pixel_control_receipt_schema", WorkloadInputs.LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        for (String field : Set.of("mod_present", "shaders_in_use", "hide_gui")) state.addProperty(field, true);
        for (String field : Set.of("fallback", "terrain_configured", "terrain_confirmation", "terrain_active", "shadow_culling_configured", "shadow_culling_active", "shadow_lists_configured", "shadow_lists_active", "shadow_coherence_configured", "shadow_coherence_active", "live_pixel_dh_present")) state.addProperty(field, false);
        var lod = new JsonObject(); lod.addProperty("enabled", enabled); lod.addProperty("debug", false); lod.addProperty("preset", "PERFORMANCE"); lod.addProperty("near", 32.0); lod.addProperty("far", 96.0); lod.addProperty("transition", 8.0);
        state.add("lod_settings", lod.deepCopy());
        var semantic = new JsonObject(); semantic.add("lod", lod.deepCopy());
        var terrain = new JsonObject(); terrain.addProperty("enabled", false); terrain.addProperty("warmSourceConfirmed", false); terrain.addProperty("vanillaChunkCap", 16); terrain.addProperty("warmupSeconds", 30); semantic.add("terrain", terrain);
        var rendering = new JsonObject(); for (String field : Set.of("optimizeShadowCulling", "separateShadowVisibilityLists", "shadowFrustumCoherence")) rendering.addProperty(field, false); semantic.add("rendering", rendering);
        state.add("loaded_semantic_config", semantic);
        var controls = new java.util.EnumMap<Effect, ShaderLodEffectConfig.Control>(Effect.class);
        for (Effect effect : Effect.values()) controls.put(effect, new ShaderLodEffectConfig.Control(ShaderLodEffectConfig.Mode.INHERIT, EffectPolicy.defaultsFor(effect)));
        state.add("live_pixel_effect_controls", JSON.toJsonTree(controls)); resyncControls(state);
        state.add("effective_shader_options", JSON.toJsonTree(options)); state.addProperty("effective_shader_options_sha256", WorkloadInputs.LIVE_PIXEL_OPTIONS_SHA256);
        state.addProperty("actual_shader_sha256", source.sourceSha256());
        var qualified = WorkloadInputs.qualifyLivePixelSource(source, options); state.add("live_pixel_source_qualification", qualified); state.addProperty("live_pixel_source_qualification_sha256", WorkloadInputs.canonicalJsonHash(qualified));
        state.add("configuration_files", configuration.deepCopy());
        state.add("live_pixel_configuration_semantics", WorkloadInputs.livePixelConfigurationSemantics(configuration));
        for (String name : List.of("config/shaderlod-client.toml", "config/shaderlod-effects-client.toml", "config/iris.properties")) state.addProperty(name + "_sha256", configuration.getAsJsonObject("files").getAsJsonObject(name).get("sha256").getAsString());
        state.addProperty("requested_chunks", 24); state.addProperty("effective_chunks", 24); state.addProperty("framebuffer_width", 2976); state.addProperty("framebuffer_height", 1672);
        state.addProperty("gui_scale", 2); state.addProperty("control_sha256", controlSha256);
        state.addProperty("dimension", "minecraft:overworld"); state.addProperty("day_time", 12500); state.addProperty("rain", 0.0); state.addProperty("thunder", 0.0);
        state.addProperty("x", 500.5); state.addProperty("y", 68.0); state.addProperty("z", 660.5); state.addProperty("yaw", 87.38f); state.addProperty("pitch", 3.735f);
        state.addProperty("live_pixel_camera_type", "FIRST_PERSON"); state.addProperty("live_pixel_fov", 70); state.add("live_pixel_window_state", JSON.toJsonTree(window()));
        return state;
    }
}
