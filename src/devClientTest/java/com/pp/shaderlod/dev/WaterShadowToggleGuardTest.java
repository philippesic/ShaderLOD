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
public final class WaterShadowToggleGuardTest {
    private static final com.google.gson.Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static SourceFingerprint source;
    private static Map<String, String> options;
    private static JsonObject configuration;
    private static String controlSha256;
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        source = PackSourceReader.read(Path.of("build/water-shadow-zero-enabled.zip"));
        var inventory = JsonParser.parseString(Files.readString(Path.of("build/water-shadow-zero-source-checks/full-inventories.json"))).getAsJsonObject();
        options = new java.util.TreeMap<>();
        inventory.getAsJsonObject("canonical-enabled").getAsJsonObject("options").entrySet().forEach(e -> options.put(e.getKey(), e.getValue().getAsString()));
        if (!WorkloadInputs.hash(Path.of("build/water-shadow-zero-source-checks/freeze.json")).equals("7bb171d2d3bacbdb27d34f9f50779a817e7373d6445bcefe3663084aba1bf2ee")) throw new AssertionError("Accepted water source freeze changed");
        configuration = configurationFixture();
        controlSha256 = WorkloadInputs.hash(Path.of("run-workloads/live-pixel-runtime-r1/global-off/control.json"));
        JsonObject reference = null;
        for (int phase = 0; phase < 4; phase++) {
            boolean enabled = phase % 2 == 1;
            var state = state(enabled);
            // Model real async persistence changing only the boolean token.
            if (enabled) changePersistedGlobal(state, true);
            WorkloadInputs.validateWaterShadowZero(state, enabled);
            var semantic = WorkloadInputs.withoutFeature(state, "water_shadow_zero");
            if (reference != null && !reference.equals(semantic)) throw new AssertionError("More than global config changed");
            reference = semantic;
            var window = new WindowStateTracker();
            window.observeCapture(window(), 0, 1); window.observeCapture(window(), 20, 601);
            BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(window.snapshot()).getAsJsonObject(), state.getAsJsonObject("water_shadow_zero_window_state"));
            checks++;
        }
        reject(s -> s.getAsJsonObject("lod_settings").addProperty("enabled", true));
        reject(s -> s.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").addProperty("enabled", true));
        reject(s -> s.addProperty("actual_shader_sha256", "0".repeat(64)));
        reject(s -> s.getAsJsonObject("effective_shader_options").remove("SHADERLOD_WATER_SHADOW_ZERO_INCLUDED"));
        reject(s -> s.getAsJsonObject("effective_shader_options").addProperty("UNEXPECTED_HELPER", "true"));
        for (String key : List.of("TAA", "SHADOW", "SHADERLOD_WATER_SHADOW_ZERO", "SHADERLOD_WATER_SHADOW_ZERO_INCLUDED")) reject(s -> s.getAsJsonObject("effective_shader_options").addProperty(key, "false"));
        reject(s -> s.getAsJsonObject("water_shadow_zero_source_qualification").getAsJsonObject("source_fingerprint").getAsJsonObject("memberSha256").addProperty("LICENSE", "0".repeat(64)));
        var unknownMembers = new java.util.TreeMap<>(source.memberSha256()); unknownMembers.put("EXTRA", "0".repeat(64));
        expectFailure(() -> WorkloadInputs.qualifyWaterShadowZeroSource(new SourceFingerprint(source.sourceSha256(), unknownMembers), options));
        var wrongMember = new java.util.TreeMap<>(source.memberSha256()); wrongMember.put("shaders/program/d1_clouds.fsh", "0".repeat(64));
        expectFailure(() -> WorkloadInputs.qualifyWaterShadowZeroSource(new SourceFingerprint(source.sourceSha256(), wrongMember), options));
        expectFailure(() -> WorkloadInputs.qualifyWaterShadowZeroSource(new SourceFingerprint("0".repeat(64), source.memberSha256()), options));
        for (String key : List.of("TAAU", "SHADOW_VPS", "SHADOW_COLOR")) reject(s -> s.getAsJsonObject("effective_shader_options").addProperty(key, "true"));
        reject(s -> s.getAsJsonObject("effective_shader_options").addProperty("shadowMapResolution", "1536"));
        reject(s -> s.addProperty("shader_pack", "live-pixel-budget-enabled.zip"));
        reject(s -> s.addProperty("water_shadow_zero_control_receipt_schema", 1));
        reject(s -> s.addProperty("water_shadow_zero_dh_present", true));
        reject(s -> s.addProperty("shadow_culling_active", true));
        reject(s -> s.addProperty("day_time", 6000));
        reject(s -> s.addProperty("requested_chunks", 16));
        reject(s -> s.addProperty("framebuffer_width", 1488));
        reject(s -> s.addProperty("gui_scale", 3));
        reject(s -> s.addProperty("water_shadow_zero_camera_type", "THIRD_PERSON_BACK"));
        reject(s -> s.addProperty("x", 501));
        reject(s -> s.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").addProperty("near", 64));
        reject(s -> s.getAsJsonObject("water_shadow_zero_window_state").addProperty("focused", "unknown"));
        reject(s -> s.getAsJsonObject("water_shadow_zero_window_state").getAsJsonObject("pacing").addProperty("vsync", true));
        reject(s -> s.getAsJsonObject("water_shadow_zero_effect_controls").remove("VOLUMETRICS"));
        var adaptive = state(false); adaptive.getAsJsonObject("water_shadow_zero_effect_controls").getAsJsonObject("VOLUMETRICS").addProperty("mode", "ADAPTIVE"); resyncControls(adaptive);
        expectFailure(() -> WorkloadInputs.validateWaterShadowZero(adaptive, false));
        var ratio = state(false); ratio.getAsJsonObject("water_shadow_zero_effect_controls").getAsJsonObject("VOLUMETRICS").getAsJsonObject("policy").addProperty("farWorkRatio", .75); resyncControls(ratio);
        expectFailure(() -> WorkloadInputs.validateWaterShadowZero(ratio, false));
        reject(s -> s.getAsJsonObject("configuration_files").addProperty("complete", false));
        reject(s -> s.getAsJsonObject("configuration_files").getAsJsonObject("files").remove("config/iris.properties"));
        for (String replacement : List.of("enabled = 1", "enabled = false\nenabled = true")) {
            var capture = configuration.deepCopy();
            var main = capture.getAsJsonObject("files").getAsJsonObject("config/shaderlod-client.toml");
            main.addProperty("utf8", "[lod]\n" + replacement + "\n[terrain]\nenabled=false\n");
            expectFailure(() -> WorkloadInputs.waterShadowZeroConfigurationSemantics(capture));
        }
        var unrelated = state(false);
        unrelated.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("options.txt").addProperty("sha256", "0".repeat(64));
        unrelated.add("water_shadow_zero_configuration_semantics", WorkloadInputs.waterShadowZeroConfigurationSemantics(unrelated.getAsJsonObject("configuration_files")));
        if (WorkloadInputs.withoutFeature(unrelated, "water_shadow_zero").equals(WorkloadInputs.withoutFeature(state(false), "water_shadow_zero"))) throw new AssertionError("Unrelated settings hash was ignored");
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
        mainOther.add("water_shadow_zero_configuration_semantics", WorkloadInputs.waterShadowZeroConfigurationSemantics(mainOther.getAsJsonObject("configuration_files")));
        if (WorkloadInputs.withoutFeature(mainOther, "water_shadow_zero").equals(WorkloadInputs.withoutFeature(state(false), "water_shadow_zero"))) throw new AssertionError("Other main settings were ignored");
        checks++;
        var windowChanged = new WindowStateTracker(); windowChanged.observeCapture(window(), 0, 1);
        windowChanged.observeCapture(new WindowStateTracker.State("available", "false", "false", "true", 1488, 836, 2976, 1672, window().pacing(), "fixture"), 1, 61);
        expectFailure(() -> BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(windowChanged.snapshot()).getAsJsonObject(), JSON.toJsonTree(window()).getAsJsonObject()));
        var samples = new java.util.ArrayList<GpuTimers.Sample>();
        for (int index = 0; index < 60; index++) samples.add(new GpuTimers.Sample(GpuTimers.FRAME, 12, index + 1, 1));
        BenchmarkSession.validateReferenceGpuSamples(samples, true); checks++;
        samples.removeLast(); expectFailure(() -> BenchmarkSession.validateReferenceGpuSamples(samples, true));
        samples.add(new GpuTimers.Sample(GpuTimers.FRAME, Double.NaN, 60, 1)); expectFailure(() -> BenchmarkSession.validateReferenceGpuSamples(samples, true));
        var badBackup = configuration.deepCopy();
        var effectsBackup = badBackup.getAsJsonObject("files").getAsJsonObject("config/shaderlod-effects-client-1.toml.bak");
        if (effectsBackup == null) throw new AssertionError("Real startup effects backup fixture unavailable");
        effectsBackup.addProperty("utf8", effectsBackup.get("utf8").getAsString() + "\n[unknown]\nchanged=true\n"); refreshRawRecord(effectsBackup);
        expectFailure(() -> WorkloadInputs.waterShadowZeroConfigurationSemantics(badBackup));
        var wrongSidecar = configuration.deepCopy(); wrongSidecar.getAsJsonObject("files").remove("shaderpacks/water-shadow-zero-enabled.zip.txt");
        expectFailure(() -> WorkloadInputs.waterShadowZeroConfigurationSemantics(wrongSidecar));
        recipe();
        if (arguments.length > 0) Files.writeString(Path.of(arguments[0]), JSON.toJson(state(false)));
        if (arguments.length > 1) {
            var capture = new WindowStateTracker(); capture.observeCapture(window(), 0, 1); capture.observeCapture(window(), 20, 601);
            Files.writeString(Path.of(arguments[1]), JSON.toJson(capture.snapshot()));
        }
        if (arguments.length > 2) Files.writeString(Path.of(arguments[2]), JSON.toJson(WorkloadInputs.withoutFeature(state(false), "water_shadow_zero")));
        System.out.println("Water shadow zero guard: " + checks + " exact-source/options/config/four-phase/window/sample/recipe checks passed; no runtime claims");
    }

    /** Raw r3 bytes model a new pack selection; this is not a runtime receipt. */
    private static JsonObject configurationFixture() throws Exception {
        var report = JsonParser.parseString(Files.readString(Path.of("run-workloads/pixel-coverage-runtime-r3/global-off/control/pixel-coverage-evidence.json"))).getAsJsonObject();
        var captured = report.getAsJsonObject("initialInputs").getAsJsonObject("configuration_files").deepCopy();
        var files = captured.getAsJsonObject("files");
        var oldSidecar = files.remove("shaderpacks/live-pixel-budget-enabled.zip.txt").getAsJsonObject();
        var requested = new java.util.TreeMap<String, String>();
        var inventory = JsonParser.parseString(Files.readString(Path.of("build/water-shadow-zero-source-checks/full-inventories.json"))).getAsJsonObject();
        inventory.getAsJsonObject("canonical-enabled").getAsJsonObject("options").entrySet().forEach(e -> requested.put(e.getKey(), e.getValue().getAsString()));
        var text = new StringBuilder("#Tue Oct 06 01:11:59 PDT 2026\n"); requested.forEach((key,value) -> text.append(key).append('=').append(value).append('\n'));
        oldSidecar.addProperty("utf8", text.toString()); refreshRawRecord(oldSidecar); files.add("shaderpacks/water-shadow-zero-enabled.zip.txt", oldSidecar);
        var iris = files.getAsJsonObject("config/iris.properties");
        iris.addProperty("utf8", iris.get("utf8").getAsString().replace("live-pixel-budget-enabled.zip", "water-shadow-zero-enabled.zip")); refreshRawRecord(iris);
        return captured;
    }

    private static void recipe() {
        var previous = new java.util.HashMap<String, String>();
        var values = Map.of("gpuSampleInterval", "5", "fixedWetness", "0", "cpuLayerTiming", "false", "gpuPassTiming", "false", "gpuAttribution", "false");
        values.forEach((key, value) -> { previous.put(key, System.getProperty("shaderlod.dev." + key)); System.setProperty("shaderlod.dev." + key, value); });
        try {
            BenchmarkSession.validateWaterShadowZeroRecipe(30, 8, 20, false, true, true); checks++;
            expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(29, 8, 20, false, true, true));
            expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, 7.99, 20, false, true, true));
            expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, 8, 19.99, false, true, true));
            expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, 8, 20, true, true, true));
            expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, 8, 20, false, false, true));
            expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, 8, 20, false, true, false));
            expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(Double.NaN, 8, 20, false, true, true));
            expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, Double.POSITIVE_INFINITY, 20, false, true, true));
            for (String key : values.keySet()) {
                System.setProperty("shaderlod.dev." + key, key.equals("gpuSampleInterval") ? "1" : key.equals("fixedWetness") ? ".5" : "true");
                expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, 8, 20, false, true, true));
                System.setProperty("shaderlod.dev." + key, values.get(key));
            }
            String priorCamera = System.getProperty("shaderlod.dev.cameraQuality");
            try {
                System.setProperty("shaderlod.dev.cameraQuality", "motion");
                expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, 8, 20, false, true, true));
            } finally { if (priorCamera == null) System.clearProperty("shaderlod.dev.cameraQuality"); else System.setProperty("shaderlod.dev.cameraQuality", priorCamera); }
            for (String name : List.of("shadowCoherenceOracle", "visibilityLists", "shadowObserve", "workTrace", "workTraceOnly", "spatialLayoutProbe", "spatialLayoutOnly", "cloudSamplerProbe", "pixelCoverage", "pixelCoverageOnly", "dhDrawEvidence", "waterSceneSurvey", "smoke", "smokeTest", "gpuBenchmark", "cameraQualityOnly")) {
                String previousProbe = System.getProperty("shaderlod.dev." + name);
                try {
                    System.setProperty("shaderlod.dev." + name, "true");
                    expectFailure(() -> BenchmarkSession.validateWaterShadowZeroRecipe(30, 8, 20, false, true, true));
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
        state.add("water_shadow_zero_configuration_semantics", WorkloadInputs.waterShadowZeroConfigurationSemantics(state.getAsJsonObject("configuration_files")));
    }

    private static void refreshRawRecord(JsonObject file) {
        byte[] bytes = file.get("utf8").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        file.addProperty("bytes", bytes.length);
        try { file.addProperty("sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
    }

    private static void resyncControls(JsonObject state) {
        var controls = state.getAsJsonObject("water_shadow_zero_effect_controls");
        state.getAsJsonObject("loaded_semantic_config").add("effects", controls.deepCopy());
        state.addProperty("water_shadow_zero_effect_controls_sha256", WorkloadInputs.canonicalJsonHash(controls));
    }

    private static void reject(Consumer<JsonObject> mutation) { var state = state(false); mutation.accept(state); expectFailure(() -> WorkloadInputs.validateWaterShadowZero(state, false)); }
    private static void expectFailure(Runnable action) { try { action.run(); } catch (RuntimeException expected) { checks++; return; } throw new AssertionError("Invalid fixture accepted"); }
    private static WindowStateTracker.State window() { return new WindowStateTracker.State("available", "true", "false", "true", 1488, 836, 2976, 1672, new WindowStateTracker.Pacing(false, false, 260, false), "fixture"); }

    private static JsonObject state(boolean enabled) {
        var state = new JsonObject();
        state.addProperty("water_shadow_zero_control_receipt_schema", WorkloadInputs.LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        for (String field : Set.of("mod_present", "shaders_in_use", "hide_gui")) state.addProperty(field, true);
        for (String field : Set.of("fallback", "terrain_configured", "terrain_confirmation", "terrain_active", "shadow_culling_configured", "shadow_culling_active", "shadow_lists_configured", "shadow_lists_active", "shadow_coherence_configured", "shadow_coherence_active", "water_shadow_zero_dh_present")) state.addProperty(field, false);
        var lod = new JsonObject(); lod.addProperty("enabled", enabled); lod.addProperty("debug", false); lod.addProperty("preset", "PERFORMANCE"); lod.addProperty("near", 32.0); lod.addProperty("far", 96.0); lod.addProperty("transition", 8.0);
        state.add("lod_settings", lod.deepCopy());
        var semantic = new JsonObject(); semantic.add("lod", lod.deepCopy());
        var terrain = new JsonObject(); terrain.addProperty("enabled", false); terrain.addProperty("warmSourceConfirmed", false); terrain.addProperty("vanillaChunkCap", 16); terrain.addProperty("warmupSeconds", 30); semantic.add("terrain", terrain);
        var rendering = new JsonObject(); for (String field : Set.of("optimizeShadowCulling", "separateShadowVisibilityLists", "shadowFrustumCoherence")) rendering.addProperty(field, false); semantic.add("rendering", rendering);
        state.add("loaded_semantic_config", semantic);
        var controls = new java.util.EnumMap<Effect, ShaderLodEffectConfig.Control>(Effect.class);
        for (Effect effect : Effect.values()) controls.put(effect, new ShaderLodEffectConfig.Control(ShaderLodEffectConfig.Mode.INHERIT, EffectPolicy.defaultsFor(effect)));
        state.add("water_shadow_zero_effect_controls", JSON.toJsonTree(controls)); resyncControls(state);
        state.add("effective_shader_options", JSON.toJsonTree(options)); state.addProperty("effective_shader_options_sha256", WorkloadInputs.WATER_SHADOW_ZERO_OPTIONS_SHA256);
        state.addProperty("actual_shader_sha256", source.sourceSha256());
        var qualified = WorkloadInputs.qualifyWaterShadowZeroSource(source, options); state.add("water_shadow_zero_source_qualification", qualified); state.addProperty("water_shadow_zero_source_qualification_sha256", WorkloadInputs.canonicalJsonHash(qualified));
        state.add("configuration_files", configuration.deepCopy());
        state.add("water_shadow_zero_configuration_semantics", WorkloadInputs.waterShadowZeroConfigurationSemantics(configuration));
        for (String name : List.of("config/shaderlod-client.toml", "config/shaderlod-effects-client.toml", "config/iris.properties")) state.addProperty(name + "_sha256", configuration.getAsJsonObject("files").getAsJsonObject(name).get("sha256").getAsString());
        state.addProperty("requested_chunks", 24); state.addProperty("effective_chunks", 24); state.addProperty("framebuffer_width", 2976); state.addProperty("framebuffer_height", 1672);
        state.addProperty("gui_scale", 2); state.addProperty("control_sha256", controlSha256);
        state.addProperty("dimension", "minecraft:overworld"); state.addProperty("day_time", 12500); state.addProperty("rain", 0.0); state.addProperty("thunder", 0.0);
        state.addProperty("x", 500.5); state.addProperty("y", 68.0); state.addProperty("z", 660.5); state.addProperty("yaw", 87.38f); state.addProperty("pitch", 3.735f);
        state.addProperty("shader_pack", "water-shadow-zero-enabled.zip");
        state.addProperty("water_shadow_zero_camera_type", "FIRST_PERSON"); state.addProperty("water_shadow_zero_fov", 70); state.add("water_shadow_zero_window_state", JSON.toJsonTree(window()));
        return state;
    }
}
