package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.ShaderLodEffectConfig;
import com.pp.shaderlod.dev.timing.WindowStateTracker;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.PhotonReferenceAdapter;
import com.pp.shaderlod.integration.SourceFingerprint;
import com.pp.shaderlod.lod.DistanceBands;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Executes real boundary guards with model/JSON fixtures; no Minecraft/GLFW/GPU calls. */
public final class VolumeToggleGuardTest {
    private static final com.google.gson.Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        JsonObject reference = null;
        for (int phase = 0; phase < 4; phase++) {
            boolean enabled = phase % 2 == 1;
            var state = state(enabled);
            WorkloadInputs.validateVolume(state, enabled);
            var semantic = WorkloadInputs.withoutFeature(state, "volume");
            if (reference != null && !reference.equals(semantic)) throw new AssertionError("Only global flag should differ");
            reference = semantic;
            var capture = new WindowStateTracker();
            capture.observeCapture(window(), 0, 1); capture.observeCapture(window(), 1, 61);
            BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(capture.snapshot()).getAsJsonObject(), state.getAsJsonObject("volume_window_state"));
            checks++;
        }
        reject(s -> s.getAsJsonObject("lod_settings").addProperty("enabled", true));
        reject(s -> s.remove("config/shaderlod-effects-client.toml_sha256"));
        reject(s -> s.addProperty("volume_dh_present", true));
        reject(s -> s.addProperty("shadow_culling_active", true));
        reject(s -> s.getAsJsonObject("loaded_semantic_config").getAsJsonObject("terrain").addProperty("warmSourceConfirmed", true));
        reject(s -> s.getAsJsonObject("effective_shader_options").remove("SHADERLOD_VOLUMETRIC_INTEGRATION_LOD"));
        reject(s -> s.getAsJsonObject("effective_shader_options").addProperty("SHADERLOD_VOLUMETRIC_INTEGRATION_LOD", "false"));
        reject(s -> s.getAsJsonObject("volume_adapter_binding").addProperty("reason", "UNKNOWN_SOURCE"));
        reject(s -> s.getAsJsonObject("volume_adapter_binding").add("operations", new com.google.gson.JsonArray()));
        reject(s -> s.addProperty("actual_shader_sha256", "0".repeat(64)));
        reject(s -> s.getAsJsonObject("volume_source_qualification").getAsJsonObject("source_fingerprint").getAsJsonObject("memberSha256").addProperty("LICENSE", "0".repeat(64)));
        reject(s -> s.getAsJsonObject("volume_source_qualification").getAsJsonObject("source_binding").addProperty("sourceSha256", "0".repeat(64)));
        reject(s -> s.getAsJsonObject("volume_effect_controls").remove("VOLUMETRICS"));
        reject(s -> s.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").addProperty("mode", "FULL"));
        for (String field : Set.of("mediumWorkRatio", "farWorkRatio")) {
            var altered = state(false);
            altered.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").getAsJsonObject("policy").addProperty(field, .5);
            altered.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").add("requested", altered.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").get("policy").deepCopy());
            resyncControls(altered); expectFailure(() -> WorkloadInputs.validateVolume(altered, false));
        }
        var bands = state(false);
        bands.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").getAsJsonObject("policy").getAsJsonObject("bands").addProperty("nearBlocks", 64);
        bands.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").add("requested", bands.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").get("policy").deepCopy());
        resyncControls(bands); expectFailure(() -> WorkloadInputs.validateVolume(bands, false));
        var other = state(false); other.getAsJsonObject("volume_effect_controls").getAsJsonObject("OPAQUE_LIGHTING").addProperty("mode", "ADAPTIVE");
        resyncControls(other); expectFailure(() -> WorkloadInputs.validateVolume(other, false));
        reject(s -> s.getAsJsonObject("volume_window_state").addProperty("focused", "unknown"));
        reject(s -> s.getAsJsonObject("volume_window_state").getAsJsonObject("pacing").addProperty("vsync", true));
        var badWindow = new WindowStateTracker();
        badWindow.observeCapture(window(), 0, 1);
        var changed = new WindowStateTracker.State("available", "false", "false", "true", 1280, 720, 2560, 1440, window().pacing(), "fixture");
        badWindow.observeCapture(changed, 1, 61); badWindow.observeCapture(window(), 2, 121);
        expectFailure(() -> BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(badWindow.snapshot()).getAsJsonObject(), JSON.toJsonTree(window()).getAsJsonObject()));
        var constantDifferent = new WindowStateTracker(); constantDifferent.observeCapture(changed, 0, 1);
        expectFailure(() -> BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(constantDifferent.snapshot()).getAsJsonObject(), JSON.toJsonTree(window()).getAsJsonObject()));
        reject(s -> s.getAsJsonObject("volume_adapter_binding").addProperty("reason", "UNSUPPORTED_PROTOCOL"));
        reject(s -> s.getAsJsonObject("volume_adapter_binding").addProperty("reason", "MISSING_PROTOCOL"));
        reject(s -> s.getAsJsonObject("volume_adapter_binding").getAsJsonObject("descriptor").getAsJsonArray("protocols").get(0).getAsJsonObject().add("acceptedVersions", JSON.toJsonTree(Set.of(99))));
        reject(s -> s.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").addProperty("repaired", true));
        var requested = state(false);
        requested.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").getAsJsonObject("requested").addProperty("farWorkRatio", .5);
        resyncControls(requested); expectFailure(() -> WorkloadInputs.validateVolume(requested, false));
        var lighting = state(false);
        lighting.getAsJsonObject("volume_effect_controls").getAsJsonObject("AMBIENT_OCCLUSION").getAsJsonObject("policy").addProperty("farWorkRatio", .5);
        resyncControls(lighting); expectFailure(() -> WorkloadInputs.validateVolume(lighting, false));
        var dropped = new WindowStateTracker(); dropped.observeCapture(window(), 0, 1);
        var droppedJson = JSON.toJsonTree(dropped.snapshot()).getAsJsonObject();
        droppedJson.getAsJsonObject("capture").addProperty("dropped_transitions", 1);
        expectFailure(() -> BenchmarkSession.validateReferenceWindow(droppedJson, JSON.toJsonTree(window()).getAsJsonObject()));
        reject(s -> s.getAsJsonObject("volume_window_state").addProperty("visible", "false"));
        reject(s -> s.getAsJsonObject("volume_window_state").addProperty("iconified", "true"));
        var policyChanged = state(false);
        policyChanged.getAsJsonObject("volume_effect_controls").getAsJsonObject("VOLUMETRICS").getAsJsonObject("policy").addProperty("farWorkRatio", .5);
        resyncControls(policyChanged);
        if (WorkloadInputs.withoutFeature(state(false), "volume").equals(WorkloadInputs.withoutFeature(policyChanged, "volume"))) throw new AssertionError("Selected effect drift removed from semantic comparison");
        checks++;
        var samples = new java.util.ArrayList<GpuTimers.Sample>();
        for (int index = 0; index < 60; index++) samples.add(new GpuTimers.Sample(GpuTimers.FRAME, 12, index + 1, 1));
        BenchmarkSession.validateReferenceGpuSamples(samples, true); checks++;
        samples.removeLast();
        expectFailure(() -> BenchmarkSession.validateReferenceGpuSamples(samples, true));
        samples.add(new GpuTimers.Sample(GpuTimers.FRAME, Double.NaN, 60, 1));
        expectFailure(() -> BenchmarkSession.validateReferenceGpuSamples(samples, true));
        samples.set(59, new GpuTimers.Sample("other", 12, 60, 1));
        expectFailure(() -> BenchmarkSession.validateReferenceGpuSamples(samples, true));
        samples.set(59, new GpuTimers.Sample(GpuTimers.FRAME, 0, 60, 1));
        expectFailure(() -> BenchmarkSession.validateReferenceGpuSamples(samples, true));
        var realWindowSnapshot = new WindowStateTracker();
        realWindowSnapshot.observeCapture(window(), 0, 1); realWindowSnapshot.observeCapture(window(), 20, 601);
        if (arguments.length > 1) java.nio.file.Files.writeString(java.nio.file.Path.of(arguments[1]), JSON.toJson(realWindowSnapshot.snapshot()));
        var binding = PhotonReferenceAdapter.sourceBindings().getFirst();
        expectFailure(() -> WorkloadInputs.qualifyVolumeSource(new SourceFingerprint("0".repeat(64), binding.memberSha256()), options()));
        if (arguments.length > 0) java.nio.file.Files.writeString(java.nio.file.Path.of(arguments[0]), JSON.toJson(state(false)));
        System.out.println("Volume toggle guard: " + checks + " four-phase/source/options/config/availability/window checks passed; no runtime claims");
    }

    private static void resyncControls(JsonObject state) {
        var controls = state.getAsJsonObject("volume_effect_controls");
        state.getAsJsonObject("loaded_semantic_config").add("effects", controls.deepCopy());
        state.addProperty("volume_effect_controls_sha256", WorkloadInputs.jsonHash(controls));
    }

    private static void reject(Consumer<JsonObject> mutation) {
        var state = state(false); mutation.accept(state);
        expectFailure(() -> WorkloadInputs.validateVolume(state, false));
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Invalid fixture accepted");
    }

    private static Map<String, String> options() {
        var options = new java.util.TreeMap<String, String>();
        PhotonReferenceAdapter.sourceBindings().getFirst().requiredOptions().forEach((key, values) -> options.put(key, values.iterator().next()));
        return options;
    }

    private static WindowStateTracker.State window() {
        return new WindowStateTracker.State("available", "true", "false", "true", 1280, 720, 2560, 1440,
                new WindowStateTracker.Pacing(false, false, 260, false), "fixture");
    }

    private static JsonObject state(boolean enabled) {
        var state = new JsonObject();
        state.addProperty("config/shaderlod-effects-client.toml_sha256", "1".repeat(64));
        state.addProperty("volume_dh_present", false); state.addProperty("requested_chunks", 24); state.addProperty("effective_chunks", 24); state.addProperty("volume_fov", 70);
        for (String field : Set.of("mod_present", "shaders_in_use", "hide_gui")) state.addProperty(field, true);
        for (String field : Set.of("fallback", "terrain_configured", "terrain_confirmation", "terrain_active", "shadow_culling_configured", "shadow_culling_active", "shadow_lists_configured", "shadow_lists_active", "shadow_coherence_configured", "shadow_coherence_active")) state.addProperty(field, false);
        var lod = new JsonObject(); lod.addProperty("enabled", enabled); lod.addProperty("debug", false);
        state.add("lod_settings", lod);
        var semantic = new JsonObject(); semantic.add("lod", lod.deepCopy());
        var terrain = new JsonObject(); terrain.addProperty("enabled", false); terrain.addProperty("warmSourceConfirmed", false); semantic.add("terrain", terrain);
        var rendering = new JsonObject();
        for (String field : Set.of("optimizeShadowCulling", "separateShadowVisibilityLists", "shadowFrustumCoherence")) rendering.addProperty(field, false);
        semantic.add("rendering", rendering); state.add("loaded_semantic_config", semantic);
        var controls = new java.util.EnumMap<Effect, ShaderLodEffectConfig.Control>(Effect.class);
        for (Effect effect : Effect.values()) {
            boolean volume = effect == Effect.VOLUMETRICS;
            var policy = new EffectPolicy(volume, true, new DistanceBands(512, 1536, 128), volume ? .85 : 1, volume ? .75 : 1);
            controls.put(effect, new ShaderLodEffectConfig.Control(volume ? ShaderLodEffectConfig.Mode.ADAPTIVE : ShaderLodEffectConfig.Mode.INHERIT, policy));
        }
        state.add("volume_effect_controls", JSON.toJsonTree(controls)); semantic.add("effects", state.get("volume_effect_controls").deepCopy());
        state.add("effective_shader_options", JSON.toJsonTree(options()));
        var source = PhotonReferenceAdapter.sourceBindings().getFirst();
        state.addProperty("actual_shader_sha256", source.sourceSha256());
        state.add("volume_source_qualification", WorkloadInputs.qualifyVolumeSource(new SourceFingerprint(source.sourceSha256(), source.memberSha256()), options()));
        state.add("volume_adapter_binding", JSON.toJsonTree(new AdapterRuntimeBinding.Snapshot(AdapterRuntimeBinding.Reason.AVAILABLE, PhotonReferenceAdapter.descriptor(), Set.of(PhotonReferenceAdapter.VOLUME_INTEGRATION))));
        for (String field : Set.of("volume_effect_controls", "volume_source_qualification", "volume_adapter_binding")) state.addProperty(field + "_sha256", WorkloadInputs.jsonHash(state.getAsJsonObject(field)));
        state.addProperty("framebuffer_width", 2560); state.addProperty("framebuffer_height", 1440);
        state.addProperty("volume_camera_type", "FIRST_PERSON"); state.add("volume_window_state", JSON.toJsonTree(window()));
        return state;
    }
}
