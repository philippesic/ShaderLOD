package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.ShaderLodEffectConfig;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.WindowStateTracker;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.SourceFingerprint;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pinned actual source/options/raw startup files, through the runtime receipt producer. No client or GL. */
public final class AirFogAblationToggleGuardTest {
    private static final com.google.gson.Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static SourceFingerprint source;
    private static JsonObject raw, configuration, temporal, controls, artifacts;
    private static Map<String, String> options;
    private static int negativeChecks;

    @BeforeAll static void fixtures() throws Exception {
        assertEquals("e27d5512f9f1f447d5472ea8dbb58ed55d294e94225c70c4d8b6760481ab3364",
                WorkloadInputs.hash(Path.of("build/air-fog-cost-ablation-source-checks/frozen-evidence.json")));
        source = PackSourceReader.read(Path.of("build/air-fog-cost-ablation-enabled.zip"));
        var actual = JsonParser.parseString(Files.readString(Path.of("run-workloads/air-fog-ablation-runtime-r1/global-off/control/workload-measurement.json"))).getAsJsonObject();
        raw = actual.getAsJsonObject("input_state_start").deepCopy();
        configuration = raw.getAsJsonObject("configuration_files").deepCopy();
        temporal = actual.getAsJsonObject("shader_temporal_inputs_start").deepCopy();
        options = new java.util.TreeMap<>(); raw.getAsJsonObject("effective_shader_options").entrySet().forEach(e -> options.put(e.getKey(), e.getValue().getAsString()));
        var policies = new java.util.EnumMap<Effect, ShaderLodEffectConfig.Control>(Effect.class);
        for (Effect effect : Effect.values()) policies.put(effect, new ShaderLodEffectConfig.Control(ShaderLodEffectConfig.Mode.INHERIT, EffectPolicy.defaultsFor(effect)));
        controls = JSON.toJsonTree(policies).getAsJsonObject();
        // Preserve actual accepted ModList roots; bind the isolated owning module
        // to the actual isolated output directory, never an invented URI/hash.
        Path isolated = Path.of(BenchmarkSession.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var observations = new java.util.ArrayList<JsonObject>();
        for (var element : actual.getAsJsonArray("loaded_artifact_inventory")) {
            var observation = element.getAsJsonObject();
            observations.add(observation.getAsJsonObject("mods").has("shaderlod_dev")
                    ? LoadedArtifactEvidence.observe(isolated, observation.getAsJsonObject("mods")) : observation.deepCopy());
        }
        var loaded = LoadedArtifactEvidence.snapshot(actual.getAsJsonObject("loaded_mods"), observations);
        LoadedArtifactEvidence.requireValid(loaded);
        artifacts = new JsonObject(); artifacts.add("loaded_artifacts", loaded);
        var code = new JsonObject();
        for (Class<?> owner : List.of(BenchmarkSession.class, WorkloadInputs.class)) {
            Path resource = Path.of(owner.getResource(owner.getSimpleName() + ".class").toURI());
            code.add(owner.getName(), BenchmarkSession.airFogAblationOwningSource(owner, isolated, resource, WorkloadInputs.hash(resource), loaded));
        }
        artifacts.add("owning_code_sources", code);
    }

    @Test void genuineProducerAndFourContiguousPhases() throws Exception {
        assertEquals(7, controls.size()); assertEquals(533, source.memberSha256().size()); assertEquals(367, options.size());
        JsonObject reference = null, previous = null;
        for (int index = 0; index < 4; index++) {
            boolean enabled = index % 2 == 1;
            var state = state(enabled); WorkloadInputs.validateAirFogAblation(state, enabled);
            var semantic = WorkloadInputs.withoutFeature(state, "air_fog_ablation");
            if (reference != null) assertEquals(reference, semantic); reference = semantic;
            var start = temporal.deepCopy(); start.addProperty("frame_counter_source", 1000 + index * 2000); start.addProperty("frame_time_counter_source", 50 + index * 30);
            WorkloadInputs.validateAirFogAblationTemporal(start, false);
            if (previous != null) WorkloadInputs.validateAirFogAblationClocks(previous, start);
            var end = start.deepCopy(); end.addProperty("frame_counter_source", 2000 + index * 2000); end.addProperty("frame_time_counter_source", 70 + index * 30);
            WorkloadInputs.validateAirFogAblationTemporal(end, true); WorkloadInputs.validateAirFogAblationClocks(start, end); previous = end;
        }
        Files.writeString(Path.of("build/air-fog-inprocess-guard-r1/model-off-state.json"), JSON.toJson(state(false)));
        Files.writeString(Path.of("build/air-fog-inprocess-guard-r1/model-stable-reference.json"), JSON.toJson(reference));
    }

    @Test void unknownSourceAndOptionsFailClosed() {
        for (String name : List.of("LICENSE", "shaderlod-air-fog-cost-ablation.json", "shaders/program/c0_vl.fsh", "shaders/settings.glsl", "shaders/include/shaderlod/air_fog_cost_ablation.glsl")) {
            var members = new java.util.TreeMap<>(source.memberSha256()); members.put(name, "0".repeat(64));
            fails(() -> WorkloadInputs.qualifyAirFogAblationSource(new SourceFingerprint(source.sourceSha256(), members), options));
        }
        var members = new java.util.TreeMap<>(source.memberSha256()); members.put("unknown", "0".repeat(64));
        fails(() -> WorkloadInputs.qualifyAirFogAblationSource(new SourceFingerprint(source.sourceSha256(), members), options));
        fails(() -> WorkloadInputs.qualifyAirFogAblationSource(new SourceFingerprint("0".repeat(64), source.memberSha256()), options));
        for (String key : List.of("TAA", "VL", "LPV_VL", "SHADERLOD_AIR_FOG_COST_ABLATION")) {
            var map = new java.util.TreeMap<>(options); map.remove(key); fails(() -> WorkloadInputs.qualifyAirFogAblationSource(source, map));
            map.put(key, "false"); fails(() -> WorkloadInputs.qualifyAirFogAblationSource(source, map));
        }
        for (String key : List.of("TAAU", "COLORED_LIGHTS", "AIR_FOG_COLORED_LIGHT_SHAFTS")) {
            var map = new java.util.TreeMap<>(options); map.put(key, "true"); fails(() -> WorkloadInputs.qualifyAirFogAblationSource(source, map));
        }
        var map = new java.util.TreeMap<>(options); map.put("UNKNOWN", "false"); fails(() -> WorkloadInputs.qualifyAirFogAblationSource(source, map));
        reject(s -> s.addProperty("actual_shader_sha256", "0".repeat(64)));
        reject(s -> s.addProperty("effective_shader_options_sha256", "0".repeat(64)));
    }

    @Test void actualConfigurationAndOnlyGlobalNormalization() {
        for (String name : List.of("config/iris.properties", "config/shaderlod-client.toml", "config/shaderlod-effects-client.toml", "shaderpacks/air-fog-cost-ablation-enabled.zip.txt")) {
            var config = configuration.deepCopy(); config.getAsJsonObject("files").remove(name); fails(() -> WorkloadInputs.airFogAblationConfigurationSemantics(config));
        }
        var incomplete = configuration.deepCopy(); incomplete.addProperty("complete", false); fails(() -> WorkloadInputs.airFogAblationConfigurationSemantics(incomplete));
        for (String name : List.of("config/shaderlod-client.toml", "config/shaderlod-client-1.toml.bak", "config/shaderlod-effects-client-1.toml.bak")) {
            var config = configuration.deepCopy(); var record = config.getAsJsonObject("files").getAsJsonObject(name);
            record.addProperty("utf8", record.get("utf8").getAsString() + "\n[unknown]\nchanged=true\n"); refresh(record);
            if (name.endsWith("shaderlod-client.toml")) {
                var before = WorkloadInputs.airFogAblationConfigurationSemantics(configuration);
                fails(() -> assertStableConfig(config, before));
            } else fails(() -> WorkloadInputs.airFogAblationConfigurationSemantics(config));
        }
        for (String replacement : List.of("enabled = 1", "enabled = false\nenabled = true")) {
            var config = configuration.deepCopy(); var record = config.getAsJsonObject("files").getAsJsonObject("config/shaderlod-client.toml");
            record.addProperty("utf8", "[lod]\n" + replacement + "\n"); refresh(record); fails(() -> WorkloadInputs.airFogAblationConfigurationSemantics(config));
        }
        var config = configuration.deepCopy(); var iris = config.getAsJsonObject("files").getAsJsonObject("config/iris.properties");
        iris.addProperty("utf8", iris.get("utf8").getAsString().replace("air-fog-cost-ablation-enabled.zip", "unknown.zip")); refresh(iris);
        fails(() -> WorkloadInputs.airFogAblationConfigurationSemantics(config));
        reject(s -> s.addProperty("config/iris.properties_sha256", "0".repeat(64)));
        reject(s -> s.addProperty("config/shaderlod-effects-client.toml_sha256", "0".repeat(64)));
        var changed = state(false); var optionFile = changed.getAsJsonObject("configuration_files").getAsJsonObject("files").getAsJsonObject("options.txt"); optionFile.addProperty("sha256", "0".repeat(64));
        changed.add("air_fog_ablation_configuration_semantics", WorkloadInputs.airFogAblationConfigurationSemantics(changed.getAsJsonObject("configuration_files")));
        assertNotEquals(WorkloadInputs.withoutFeature(state(false), "air_fog_ablation"), WorkloadInputs.withoutFeature(changed, "air_fog_ablation"));
    }

    @Test void actualSettingsWeatherCameraAndAllSevenEffects() {
        for (String field : List.of("air_fog_ablation_dh_present", "shadow_culling_active", "shadow_lists_active", "shadow_coherence_active", "terrain_configured")) reject(s -> s.addProperty(field, true));
        for (String field : List.of("x", "y", "z", "yaw", "pitch", "rain", "thunder", "day_time", "requested_chunks", "effective_chunks", "framebuffer_width", "gui_scale", "air_fog_ablation_fov")) reject(s -> s.addProperty(field, -1));
        reject(s -> s.addProperty("air_fog_ablation_camera_type", "THIRD_PERSON_BACK")); reject(s -> s.addProperty("shader_pack", "unknown.zip"));
        reject(s -> s.getAsJsonObject("lod_settings").addProperty("enabled", true));
        reject(s -> s.getAsJsonObject("lod_settings").addProperty("near", 64));
        reject(s -> s.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").addProperty("enabled", true));
        for (Effect effect : Effect.values()) {
            reject(s -> s.getAsJsonObject("air_fog_ablation_effect_controls").remove(effect.name()));
            var state = state(false); var selected = state.getAsJsonObject("air_fog_ablation_effect_controls").getAsJsonObject(effect.name()); selected.addProperty("mode", "ADAPTIVE"); syncEffects(state); var adaptive = state; fails(() -> WorkloadInputs.validateAirFogAblation(adaptive, false));
            state = state(false); selected = state.getAsJsonObject("air_fog_ablation_effect_controls").getAsJsonObject(effect.name()); selected.getAsJsonObject("policy").addProperty("farWorkRatio", .75); syncEffects(state); var ratio = state; fails(() -> WorkloadInputs.validateAirFogAblation(ratio, false));
        }
    }

    @Test void unknownWindowAndClassArtifactChangesAreStrict() {
        for (String field : List.of("availability", "focused", "visible", "iconified")) reject(s -> s.getAsJsonObject("air_fog_ablation_window_state").addProperty(field, "unknown"));
        reject(s -> s.getAsJsonObject("air_fog_ablation_window_state").getAsJsonObject("pacing").addProperty("vsync", true));
        reject(s -> s.getAsJsonObject("air_fog_ablation_artifact_state").getAsJsonObject("loaded_artifacts").addProperty("valid", false));
        reject(s -> s.getAsJsonObject("air_fog_ablation_artifact_state").getAsJsonObject("owning_code_sources").remove(WorkloadInputs.class.getName()));
        var changed = state(false); changed.getAsJsonObject("air_fog_ablation_artifact_state").getAsJsonObject("owning_code_sources").getAsJsonObject(WorkloadInputs.class.getName()).addProperty("class_sha256", "0".repeat(64));
        assertNotEquals(WorkloadInputs.withoutFeature(state(false), "air_fog_ablation"), WorkloadInputs.withoutFeature(changed, "air_fog_ablation"));
        var capture = new WindowStateTracker(); capture.observeCapture(window(), 0, 1); capture.observeCapture(window(), 20, 601);
        BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(capture.snapshot()).getAsJsonObject(), JSON.toJsonTree(window()).getAsJsonObject());
        capture.observeCapture(new WindowStateTracker.State("available", "false", "false", "true", 1488, 836, 2976, 1672, window().pacing(), "fixture"), 21, 602);
        fails(() -> BenchmarkSession.validateReferenceWindow(JSON.toJsonTree(capture.snapshot()).getAsJsonObject(), JSON.toJsonTree(window()).getAsJsonObject()));
    }

    @Test void actualNeoForgeUnionPrimaryRootAndClassBinding() throws Exception {
        var loaded = artifacts.getAsJsonObject("loaded_artifacts");
        Path physical = Path.of(BenchmarkSession.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var provider = (cpw.mods.niofs.union.UnionFileSystemProvider) java.nio.file.spi.FileSystemProvider.installedProviders().stream()
                .filter(item -> item instanceof cpw.mods.niofs.union.UnionFileSystemProvider).findFirst().orElseThrow();
        // Real pinned provider, two real roots, real class bytes and directory hashes.
        Path resources = Path.of("build/air-fog-inprocess-guard-r1/union-fix-r1/resources").toAbsolutePath();
        Files.createDirectories(resources); Files.writeString(resources.resolve("fixture-resource.txt"), "actual Union overlay fixture");
        try (var filesystem = provider.newFileSystem((member, base) -> true, physical, resources)) {
            Path virtual = filesystem.getRoot(); assertEquals(physical, filesystem.getPrimaryPath());
            var candidate = artifacts.deepCopy(); var records = new JsonObject();
            for (Class<?> owner : List.of(BenchmarkSession.class, WorkloadInputs.class)) {
                Path resource = virtual.resolve(owner.getName().replace('.', '/') + ".class");
                assertEquals(resource, provider.getPath(resource.toUri()));
                var record = BenchmarkSession.airFogAblationOwningSource(owner, virtual, resource, WorkloadInputs.hash(resource), loaded);
                assertEquals("union", record.get("filesystem_provider").getAsString());
                assertEquals(physical.toUri().toString(), record.getAsJsonObject("primary_root").get("uri").getAsString());
                records.add(owner.getName(), record);
            }
            candidate.add("owning_code_sources", records);
            BenchmarkSession.validateAirFogAblationArtifactState(candidate);
            Files.writeString(Path.of("build/air-fog-inprocess-guard-r1/union-fix-r1/genuine-union-receipt.json"), JSON.toJson(candidate));
            for (String field : List.of("uri", "raw_code_source_uri", "class_resource_uri", "raw_class_resource_uri")) {
                var bad = candidate.deepCopy(); bad.getAsJsonObject("owning_code_sources").getAsJsonObject(BenchmarkSession.class.getName()).addProperty(field, "unknown:/unresolved");
                fails(() -> BenchmarkSession.validateAirFogAblationArtifactState(bad));
            }
            for (String field : List.of("filesystem_provider", "path", "class_member", "class_sha256")) {
                var bad = candidate.deepCopy(); bad.getAsJsonObject("owning_code_sources").getAsJsonObject(BenchmarkSession.class.getName()).addProperty(field, "wrong");
                fails(() -> BenchmarkSession.validateAirFogAblationArtifactState(bad));
            }
            var badTree = candidate.deepCopy(); badTree.getAsJsonObject("owning_code_sources").getAsJsonObject(BenchmarkSession.class.getName()).getAsJsonObject("identity").addProperty("tree_sha256", "0".repeat(64));
            fails(() -> BenchmarkSession.validateAirFogAblationArtifactState(badTree));
            var wrongPhysical = candidate.deepCopy(); wrongPhysical.getAsJsonObject("owning_code_sources").getAsJsonObject(BenchmarkSession.class.getName()).getAsJsonObject("primary_root").addProperty("uri", resources.toUri().toString());
            fails(() -> BenchmarkSession.validateAirFogAblationArtifactState(wrongPhysical));
            Path foreign = Path.of("build/air-fog-inprocess-guard-r1/union-fix-r1/foreign").toAbsolutePath();
            Files.createDirectories(foreign.resolve("com/pp/shaderlod/dev"));
            Files.copy(physical.resolve("com/pp/shaderlod/dev/BenchmarkSession.class"), foreign.resolve("com/pp/shaderlod/dev/BenchmarkSession.class"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (var foreignFilesystem = provider.newFileSystem((member, base) -> true, foreign)) {
                Path foreignRoot = foreignFilesystem.getRoot(), foreignResource = foreignRoot.resolve("com/pp/shaderlod/dev/BenchmarkSession.class");
                fails(() -> { try { BenchmarkSession.airFogAblationOwningSource(BenchmarkSession.class, foreignRoot, foreignResource, WorkloadInputs.hash(foreignResource), loaded); } catch (Exception failure) { throw new IllegalStateException(failure); } });
            }
            Path wrongMember = virtual.resolve("com/pp/shaderlod/dev/WorkloadInputs.class");
            fails(() -> { try { BenchmarkSession.airFogAblationOwningSource(BenchmarkSession.class, virtual, wrongMember, WorkloadInputs.hash(wrongMember), loaded); } catch (Exception failure) { throw new IllegalStateException(failure); } });
        }
    }

    @Test void actualCpuRainWetnessAndAdvancingClockBoundaries() {
        for (String field : List.of("rain_strength_source", "day_time_source", "world_time_source", "world_day_source", "photon_world_age_source")) {
            var t = temporal.deepCopy(); t.addProperty(field, -1); fails(() -> WorkloadInputs.validateAirFogAblationTemporal(t, true));
        }
        for (String field : List.of("frame_counter_source", "frame_time_counter_source", "frame_time_source")) {
            for (double value : List.of(0.0, Double.NaN, Double.POSITIVE_INFINITY)) { var t = temporal.deepCopy(); t.addProperty(field, value); fails(() -> WorkloadInputs.validateAirFogAblationTemporal(t, true)); }
        }
        var t = temporal.deepCopy(); t.addProperty("clock_policy", "frozen"); var frozen = t; fails(() -> WorkloadInputs.validateAirFogAblationTemporal(frozen, true));
        t = temporal.deepCopy(); t.getAsJsonObject("wetness").addProperty("fixedWetness", 0); var wet = t; fails(() -> WorkloadInputs.validateAirFogAblationTemporal(wet, true));
        t = temporal.deepCopy(); t.getAsJsonObject("wetness").addProperty("measurementReads", 0); var missing = t; fails(() -> WorkloadInputs.validateAirFogAblationTemporal(missing, true));
        t = temporal.deepCopy(); t.getAsJsonObject("wetness").getAsJsonArray("suppliers").get(1).getAsJsonObject().addProperty("appliedMax", .5); var range = t; fails(() -> WorkloadInputs.validateAirFogAblationTemporal(range, true));
        fails(() -> WorkloadInputs.validateAirFogAblationClocks(temporal, temporal));
        var reversed = temporal.deepCopy(); reversed.addProperty("frame_counter_source", 1); fails(() -> WorkloadInputs.validateAirFogAblationClocks(temporal, reversed));
    }

    @Test void recipeExcludesEveryObserverAndDetailedTimer() {
        var defaults = new java.util.HashMap<String,String>();
        defaults.putAll(Map.of("gpuSampleInterval", "5", "fixedWetness", "1", "cpuLayerTiming", "false", "gpuPassTiming", "false", "gpuAttribution", "false"));
        for (String key : List.of("shadowCoherenceOracle", "visibilityLists", "shadowObserve", "workTrace", "workTraceOnly", "spatialLayoutProbe", "spatialLayoutOnly", "cloudSamplerProbe", "pixelCoverage", "pixelCoverageOnly", "dhDrawEvidence", "waterSceneSurvey", "smokeTest", "gpuBenchmark", "cameraQualityOnly", "smoke", "pixelHistoryCapture", "pixelHistoryCaptureOnly", "measureOnly", "measureWorkload")) defaults.put(key, "false");
        defaults.put("cameraQuality", ""); var previous = new java.util.HashMap<String,String>();
        defaults.forEach((k,v) -> { previous.put(k,System.getProperty("shaderlod.dev."+k)); System.setProperty("shaderlod.dev."+k,v); });
        try {
            BenchmarkSession.validateAirFogAblationRecipe(30,8,20,false,true,true);
            for (double[] recipe : List.of(new double[]{29,8,20}, new double[]{30,7.99,20}, new double[]{30,8,19.99}, new double[]{Double.NaN,8,20}, new double[]{30,Double.POSITIVE_INFINITY,20})) fails(() -> BenchmarkSession.validateAirFogAblationRecipe(recipe[0],recipe[1],recipe[2],false,true,true));
            fails(() -> BenchmarkSession.validateAirFogAblationRecipe(30,8,20,true,true,true)); fails(() -> BenchmarkSession.validateAirFogAblationRecipe(30,8,20,false,false,true)); fails(() -> BenchmarkSession.validateAirFogAblationRecipe(30,8,20,false,true,false));
            defaults.forEach((k,v) -> { System.setProperty("shaderlod.dev."+k, k.equals("gpuSampleInterval") ? "1" : k.equals("fixedWetness") ? "0" : k.equals("cameraQuality") ? "motion" : "true"); fails(() -> BenchmarkSession.validateAirFogAblationRecipe(30,8,20,false,true,true)); System.setProperty("shaderlod.dev."+k,v); });
        } finally { previous.forEach((k,v) -> { if(v==null) System.clearProperty("shaderlod.dev."+k); else System.setProperty("shaderlod.dev."+k,v); }); }
    }

    @Test void actualGpuMinimumAndEpochSchema() {
        var samples = new java.util.ArrayList<GpuTimers.Sample>();
        for (int i = 0; i < 60; i++) samples.add(new GpuTimers.Sample(GpuTimers.FRAME, 12, i + 1, 1));
        var cpu = new java.util.ArrayList<CpuTimers.Sample>();
        for (String scope : List.of("run-tick", "game-render", "render-and-blit", "swap-and-events", "frame-limiter")) cpu.add(new CpuTimers.Sample(scope, 10, 8.0, 10, 1));
        BenchmarkSession.validateAirFogAblationTiming(samples, cpu);
        samples.removeLast(); fails(() -> BenchmarkSession.validateAirFogAblationTiming(samples, cpu));
        samples.add(new GpuTimers.Sample(GpuTimers.FRAME, Double.NaN, 60, 1)); fails(() -> BenchmarkSession.validateAirFogAblationTiming(samples, cpu));
        samples.set(59, new GpuTimers.Sample(GpuTimers.FRAME, 12, 60, 2)); fails(() -> BenchmarkSession.validateAirFogAblationTiming(samples, cpu));
        samples.set(59, new GpuTimers.Sample("pass/C0", 12, 60, 1)); fails(() -> BenchmarkSession.validateAirFogAblationTiming(samples, cpu));
        samples.set(59, new GpuTimers.Sample(GpuTimers.FRAME, 12, 60, 1));
        cpu.add(new CpuTimers.Sample("iris/shadow-map", 12, 8.0, 10, 1)); fails(() -> BenchmarkSession.validateAirFogAblationTiming(samples, cpu)); cpu.removeLast();
        cpu.add(new CpuTimers.Sample("game-render", 12, 8.0, 10, 2)); fails(() -> BenchmarkSession.validateAirFogAblationTiming(samples, cpu)); cpu.removeLast();
        cpu.add(new CpuTimers.Sample("game-render", Double.NaN, 8.0, 10, 1)); fails(() -> BenchmarkSession.validateAirFogAblationTiming(samples, cpu)); cpu.removeLast();
        cpu.clear(); fails(() -> BenchmarkSession.validateAirFogAblationTiming(samples, cpu));
    }

    private static void assertStableConfig(JsonObject candidate, JsonObject reference) { if(!reference.equals(WorkloadInputs.airFogAblationConfigurationSemantics(candidate))) throw new IllegalStateException("Config changed beyond global"); }
    private static void fails(Runnable action) { assertThrows(RuntimeException.class,action::run); negativeChecks++; }
    private static void reject(Consumer<JsonObject> mutation) { var s=state(false); mutation.accept(s); fails(() -> WorkloadInputs.validateAirFogAblation(s,false)); }
    private static void syncEffects(JsonObject state) { var effects=state.getAsJsonObject("air_fog_ablation_effect_controls"); state.getAsJsonObject("loaded_semantic_config").add("effects",effects.deepCopy()); state.addProperty("air_fog_ablation_effect_controls_sha256",WorkloadInputs.canonicalJsonHash(effects)); }
    private static void refresh(JsonObject record) { try { byte[] bytes=record.get("utf8").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8); record.addProperty("bytes",bytes.length); record.addProperty("sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))); } catch(Exception e){throw new AssertionError(e);} }
    private static WindowStateTracker.State window() { return new WindowStateTracker.State("available","true","false","true",1488,836,2976,1672,new WindowStateTracker.Pacing(false,false,260,false),"fixture"); }
    private static JsonObject state(boolean enabled) {
        var s=raw.deepCopy();
        // Restore actual getter Float primitives after JSON roundtrip; no pose tolerance is widened.
        s.addProperty("yaw", s.get("yaw").getAsFloat()); s.addProperty("pitch", s.get("pitch").getAsFloat());
        s.remove("configuration_files"); s.addProperty("mod_present",true); s.addProperty("actual_shader_sha256",source.sourceSha256());
        for(String name:List.of("terrain_configured","terrain_confirmation","shadow_culling_configured","shadow_culling_active","shadow_lists_configured","shadow_lists_active","shadow_coherence_configured","shadow_coherence_active")) s.addProperty(name,false);
        var lod=s.getAsJsonObject("lod_settings"); lod.addProperty("enabled",enabled); var loadedLod=lod.deepCopy(); loadedLod.addProperty("preset","PERFORMANCE");
        var semantic=new JsonObject(); semantic.add("lod",loadedLod);
        var terrain=new JsonObject(); terrain.addProperty("enabled",false); terrain.addProperty("vanillaChunkCap",16); terrain.addProperty("warmSourceConfirmed",false); terrain.addProperty("warmupSeconds",30); semantic.add("terrain",terrain);
        var rendering=new JsonObject(); for(String name:List.of("optimizeShadowCulling","separateShadowVisibilityLists","shadowFrustumCoherence")) rendering.addProperty(name,false); semantic.add("rendering",rendering); s.add("loaded_semantic_config",semantic);
        var config=configuration.deepCopy(); var main=config.getAsJsonObject("files").getAsJsonObject("config/shaderlod-client.toml");
        if(enabled) { main.addProperty("utf8", main.get("utf8").getAsString().replaceFirst("(?m)(^\\h*enabled\\h*=\\h*)false", "$1true")); refresh(main); }
        s.addProperty("config/shaderlod-client.toml_sha256",main.get("sha256").getAsString()); s.add("air_fog_ablation_artifact_state",artifacts.deepCopy());
        return WorkloadInputs.airFogAblationReceipt(s,source,controls,config,JSON.toJsonTree(window()).getAsJsonObject(),false,"FIRST_PERSON",70);
    }

    /** Isolated actual JUnit launcher; no Gradle or Minecraft initialization. */
    public static void main(String[] arguments) throws Exception {
        var request=org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request().selectors(org.junit.platform.engine.discovery.DiscoverySelectors.selectClass(AirFogAblationToggleGuardTest.class)).build();
        var listener=new org.junit.platform.launcher.listeners.SummaryGeneratingListener();
        org.junit.platform.launcher.core.LauncherFactory.create().execute(request,listener);
        listener.getSummary().printTo(new java.io.PrintWriter(System.out,true)); listener.getSummary().printFailuresTo(new java.io.PrintWriter(System.out,true));
        System.out.println("Negative boundaries checked: "+negativeChecks+"; source/build checks only, no runtime claims");
        if(listener.getSummary().getTestsFailedCount()>0 || listener.getSummary().getTestsSucceededCount()!=9) throw new AssertionError("Isolated air fog JUnit acceptance failed");
    }
}
