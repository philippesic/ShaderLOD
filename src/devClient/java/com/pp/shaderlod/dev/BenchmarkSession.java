package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.ShaderInputTracker;
import com.pp.shaderlod.dev.timing.ShadowInputObserver;
import com.pp.shaderlod.dev.timing.WindowStateTracker;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.ModList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Four correlated phases in one pipeline/world, varying one explicitly selected flag. */
public final class BenchmarkSession {
    private final Path directory;
    private final WorkloadInputs inputs;
    private final boolean originalNullControl;
    private final String feature = benchmarkFeature();
    private final String sessionId = UUID.randomUUID().toString();
    private final List<Phase> phases = new ArrayList<>();
    private final double initialWarmup = seconds("initialWarmup", 60);
    private final double switchWarmup = seconds("benchmarkSettleSeconds", 8);
    private final double measurement = seconds("measurementSeconds", 20);
    private final boolean startEnabled = Boolean.getBoolean("shaderlod.dev.benchmarkStartEnabled");
    private final boolean gpuRequested = Boolean.parseBoolean(System.getProperty("shaderlod.dev.gpuTiming", "true"));
    private final boolean cpuRequested = Boolean.parseBoolean(System.getProperty("shaderlod.dev.cpuTiming", "true"));
    private final boolean initialEnabled = ShaderLodConfig.ENABLED.get();
    private final boolean initialFeatureEnabled = feature.equals("coherence") ? ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.get()
            : feature.equals("terrain") ? ShaderLodConfig.TERRAIN_ENABLED.get() : initialEnabled;
    private final boolean initialHideGui = Minecraft.getInstance().options.hideGui;
    private final long constructed = System.nanoTime();
    private final long started;
    private final JsonObject temporalInitial;
    private final JsonObject header = new JsonObject();
    private JsonObject stableReference;
    private Object worldReference, pipelineReference, cameraReference;
    private final String terrainSceneIdentifier = sessionId + ":inspected-fixed-scene";
    private long phaseStarted, measurementStarted;
    private int phase = -1;
    private boolean sampling, done, awaitingImage;
    private Phase current;
    private static volatile PhaseMarker marker;

    BenchmarkSession(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        this.inputs = new WorkloadInputs(this.directory);
        try { originalNullControl = feature.equals("global") && WorkloadInputs.ORIGINAL_NULL_SHA256.equals(WorkloadInputs.hash(this.directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName()))); }
        catch (Exception failure) { throw new IllegalStateException("Cannot identify global control source", failure); }
        if (Boolean.getBoolean("shaderlod.dev.measureWorkload")) throw new IllegalStateException("Benchmark and workload capture are mutually exclusive");
        if (feature.equals("coherence") && (!initialEnabled || Boolean.getBoolean("shaderlod.dev.shadowCoherenceOracle") || Boolean.getBoolean("shaderlod.dev.visibilityLists"))) throw new IllegalStateException("Coherence timing requires global enabled and oracle/phase observer disabled");
        if (feature.equals("terrain") && (!initialEnabled || !ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get()
                || ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.get() != 16 || ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get() != 30
                || switchWarmup < ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get() + 30 || measurement < 30
                || Boolean.getBoolean("shaderlod.dev.shadowCoherenceOracle") || Boolean.getBoolean("shaderlod.dev.visibilityLists")
                || Boolean.getBoolean("shaderlod.dev.shadowObserve"))) throw new IllegalStateException("Terrain timing requires inspected cap16/manual acknowledgement, global enabled, >=60s settle/>=30s capture and diagnostic observers disabled");
        if (feature.equals("ssr_cost") && (Boolean.getBoolean("shaderlod.dev.shadowCoherenceOracle") || Boolean.getBoolean("shaderlod.dev.visibilityLists") || Boolean.getBoolean("shaderlod.dev.shadowObserve"))) throw new IllegalStateException("SSR cost control requires diagnostic observers disabled");
        if (isReferenceDiagnostic()) {
            for (String observer : List.of("shadowCoherenceOracle", "visibilityLists", "shadowObserve", "workTrace", "spatialLayoutProbe")) {
                if (Boolean.getBoolean("shaderlod.dev." + observer)) throw new IllegalStateException(feature + " timing requires diagnostic observers disabled: " + observer);
            }
        }
        if (feature.equals("volume") && measurement < 20) throw new IllegalStateException("Volume diagnostic requires at least 20 seconds capture per phase");
        if (feature.equals("live_pixel")) validateLivePixelRecipe(initialWarmup, switchWarmup, measurement, startEnabled, gpuRequested, cpuRequested);
        if (feature.equals("water_shadow_zero")) validateWaterShadowZeroRecipe(initialWarmup, switchWarmup, measurement, startEnabled, gpuRequested, cpuRequested);
        if (originalNullControl) validateAirFogAblationRecipe(initialWarmup, switchWarmup, measurement, startEnabled, gpuRequested, cpuRequested);
        if (feature.equals("air_fog_ablation")) validateAirFogAblationRecipe(initialWarmup, switchWarmup, measurement, startEnabled, gpuRequested, cpuRequested);
        temporalInitial = inputs.temporalInputs();
        header.addProperty("schema", 3);
        header.addProperty("session_id", sessionId);
        header.addProperty("created_at_utc", java.time.Instant.now().toString());
        header.addProperty("benchmark_feature", feature);
        header.addProperty("original_null_control", originalNullControl);
        if (originalNullControl) {
            header.addProperty("diagnosticOnly", true); header.addProperty("performanceEvidence", false);
            header.addProperty("original_null_control_limits", "Pinned original Photon has no ShaderLOD GPU consumer. Global OFF/ON changes only the core CPU flag; rendering-equivalent null control, not literal unchanged-state A/A or an optimization gain.");
        }
        header.addProperty("method", switch (feature) {
            case "coherence" -> "One JVM/world/pipeline; original Photon; only shadow frustum coherence changes";
            case "effects" -> "One JVM/world/pipeline; fixed five-module effect options; only live global bridge enabled changes";
            case "terrain" -> "One JVM/world/pipeline; fixed B15/global enabled; only terrain policy enabled changes plus recorded fixed-scene manual re-arm";
            case "ssr_cost" -> "One JVM/world/pipeline; qualified fixed SSR-only B15/private trace policy; only live global bridge enabled changes";
            case "spatial" -> "One JVM/world/pipeline; pinned shared spatial Photon; fixed opaque ADAPTIVE policy; only live global bridge enabled changes; diagnostic incremental cost, OFF retains extra pass";
            case "volume" -> "One JVM/world/pipeline; registered shared volume Photon; fixed volumetrics ADAPTIVE policy; only live global bridge enabled changes; diagnostic incremental cost, OFF retains identical volume graph";
            case "live_pixel" -> "One JVM/world/pipeline; pinned live pixel Photon; fixed TAAU graph; only live core global enabled changes; settled diagnostic incremental cost, OFF scale 1.0 is not original native";
            case "water_shadow_zero" -> "One JVM/world/pipeline; pinned native Photon water receiver zero gate; only live core global enabled changes; incremental candidate OFF/ON cost screen";
            case "air_fog_ablation" -> "One JVM/world/pipeline; pinned native VL=true Photon; only core global changes; deliberate C0 air-fog removal cost diagnostic, not quality optimization";
            default -> "One JVM/world/pipeline; all five modules off; only live global bridge enabled changes";
        });
        if (originalNullControl) header.addProperty("method", "One JVM/world/pipeline; pinned original Photon without private options or ShaderLOD GPU consumers; only global CPU flag changes, rendering-equivalent null control");
        header.addProperty("starting_order", startEnabled ? "ON/OFF/ON/OFF" : "OFF/ON/OFF/ON");
        header.addProperty("initial_warmup_seconds", initialWarmup);
        header.addProperty("settle_seconds", switchWarmup);
        header.addProperty("measurement_seconds", measurement);
        header.addProperty("gpu_requested", gpuRequested);
        header.addProperty("cpu_requested", cpuRequested);
        header.addProperty("configured_hide_gui", true);
        header.addProperty("initial_enabled", initialEnabled);
        header.addProperty("initial_feature_enabled", initialFeatureEnabled);
        header.addProperty("selected_flag", feature.equals("coherence") ? "rendering.shadowFrustumCoherence" : feature.equals("terrain") ? "terrain.enabled" : "lod.enabled");
        if (feature.equals("terrain")) {
            header.addProperty("terrain_scene_identifier", terrainSceneIdentifier);
            header.addProperty("terrain_scene_identity_note", "Opaque session label for checked world/pipeline/camera reference equality and fixed pose/control; raw object IDs and automatic coverage are not exported");
            header.addProperty("terrain_rearm_policy", "One existing confirmCurrentScene call per eligible ON settling phase in the unchanged manually inspected scene; no new-scene/travel coverage claim");
        }
        header.addProperty("uniform_observation_note", "Config values are intended bridge inputs; no GPU readback or proof of active linked uniforms. PhaseMarker allows a bounded dev observer to label original supplier evaluations.");
        header.addProperty("limits", "Correlated phases, not independent JVM repeats. GPU timeline is not busy utilization; CPU includes driver work. Clocks/noise keep advancing. No causal attribution to an individual uniform call.");
        if (feature.equals("spatial")) header.addProperty("spatial_diagnostic_limits", "Incremental global OFF/ON diagnostic in the same spatial graph; OFF retains extra pass/attachments. Cannot establish original Photon 1.5x throughput or quality acceptance. Frozen source/supplier/linked-uniform qualification is a separate untimed check; no GPU readback in these phases.");
        if (feature.equals("volume")) header.addProperty("volume_diagnostic_limits", "Incremental global OFF/ON diagnostic in the same volume graph; OFF retains the identical volume graph. Cannot establish original Photon 1.5x throughput or quality acceptance. Frozen source/supplier/linked-uniform qualification is a separate untimed check; no GPU readback in these phases.");
        if (feature.equals("live_pixel")) {
            header.addProperty("live_pixel_diagnostic_limits", "Fixed TAAU graph OFF scale 1.0 is not original/default native Photon; settled >=8s exclusion is diagnostic only. Cloud history toggle transient is unaccepted; no source policy, raster coverage, motion quality or original-baseline performance claim.");
            header.addProperty("core_protocol", 2); // Expected source contract only, not linked readback.
            header.addProperty("transport", "core_global_only");
            header.addProperty("raster_policy_proof", false);
        }
        if (feature.equals("water_shadow_zero")) {
            header.addProperty("water_shadow_zero_diagnostic_limits", "Exact-zero source proof is separate from actual driver/sampler water-stage qualification, executed skip coverage, finite texture inputs, wave-motion parity and speed. Candidate global OFF is not the original archive denominator; original Photon comparison and independent repeats remain required.");
            header.addProperty("core_protocol", 2); // Expected source contract only, not linked readback.
            header.addProperty("transport", "core_global_only");
            header.addProperty("water_stage_runtime_qualification", false);
            header.addProperty("executed_skip_coverage_proof", false);
            header.addProperty("control_receipt_schema", WorkloadInputs.LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        }
        if (feature.equals("air_fog_ablation")) {
            header.addProperty("air_fog_ablation_diagnostic_limits", "Private OFF denominator, purposeful fog loss; no original-quality gain. C0 identity, neutral RT6/RT7 texels and physical body retirement remain unknown. CPU rain getter and wetness supplier evidence are not GL coefficients. Same-JVM correlation does not replace independent repeats.");
            header.addProperty("core_protocol", 2);
            header.addProperty("transport", "core_global_only");
            header.addProperty("c0_stage_identity_proof", false);
            header.addProperty("neutral_render_target_proof", false);
            header.addProperty("retired_gpu_work_proof", false);
            header.addProperty("quality_gain", false);
            header.addProperty("control_receipt_schema", WorkloadInputs.LIVE_PIXEL_CONTROL_RECEIPT_SCHEMA);
        }
        header.add("shader_temporal_inputs_initial", temporalInitial);
        var mods = new JsonObject();
        for (var mod : ModList.get().getMods()) mods.addProperty(mod.getModId(), mod.getVersion().toString());
        header.add("loaded_mods", mods);
        var properties = new JsonObject();
        for (String name : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming", "gpuPassTiming", "gpuSampleInterval", "fixedWetness", "performanceStack", "includeDh", "benchmarkFeature", "shadowCoherenceOracle", "visibilityLists", "shadowObserve")) {
            properties.addProperty(name, System.getProperty("shaderlod.dev." + name, "unspecified"));
        }
        header.add("instrumentation_properties", properties);
        if (isStrictCoreDiagnostic()) {
            for (String name : (feature.equals("air_fog_ablation") || originalNullControl) ? airFogAblationObservers() : feature.equals("water_shadow_zero") ? waterShadowZeroObservers() : livePixelObservers()) properties.addProperty(name, System.getProperty("shaderlod.dev." + name, "false"));
            for (String name : List.of("gpuAttribution", "cpuLayerTiming", "gpuPassTiming", "gpuSampleInterval")) properties.addProperty(name, System.getProperty("shaderlod.dev." + name, "unspecified"));
            // Direct independent roots captured before the initial warmup clock.
            var artifacts = LoadedArtifactEvidence.capture();
            header.add("loaded_artifact_evidence", artifacts);
            LoadedArtifactEvidence.requireValid(artifacts);
            if (!artifacts.get("loaded_mods").equals(mods)) throw new IllegalStateException("Live pixel direct loaded registry differs");
            header.add("loaded_artifact_inventory", artifacts.get("loaded_artifact_inventory").deepCopy());
            try {
                addArtifactIdentity();
                if (feature.equals("air_fog_ablation")) header.add("air_fog_ablation_artifact_state_initial", airFogAblationArtifactState());
            }
            catch (Exception failure) { throw new IllegalStateException("Live pixel initial artifacts/manifests unavailable", failure); }
        }
        if (feature.equals("coherence")) {
            var policy = new JsonObject();
            policy.addProperty("shadow_coherence_oracle", false);
            policy.addProperty("visibility_phase_observer", false);
            header.add("diagnostic_policy", policy);
        }
        if (ShadowInputObserver.enabled()) {
            var options = new java.util.TreeMap<String, String>();
            WorkloadInputs.effectiveOptions().entrySet().forEach(entry -> options.put(entry.getKey(), entry.getValue().getAsString()));
            ShadowInputObserver.configurePhotonFeatures(options);
        }
        try {
            Path previous = this.directory.resolve("benchmark.json");
            if (Files.exists(previous)) Files.move(previous, this.directory.resolve("benchmark-previous-" + sessionId + ".json"));
        } catch (Exception failure) { throw new IllegalStateException("Cannot preserve prior benchmark", failure); }
        // Complete fallible setup before changing the configured HUD policy.
        Minecraft.getInstance().options.hideGui = true;
        started = isStrictCoreDiagnostic() ? System.nanoTime() : constructed;
        write("warming", null);
    }

    public record PhaseMarker(String sessionId, String phaseId, int phaseIndex, boolean enabled, boolean sampling) {}
    public static PhaseMarker phaseMarker() { return marker; }

    String status() {
        if (done) return "In-process control finished; benchmark.json";
        if (phase < 0) return "In-process control warming";
        return "In-process phase " + (phase + 1) + "/4" + (awaitingImage ? " saving image" : sampling ? " capture" : " settling");
    }

    void tick() {
        var gpu = GpuTimers.drainSamples();
        var cpu = CpuTimers.drainSamples();
        if (done || awaitingImage) return;
        long now = System.nanoTime();
        try {
            if (worldReference != null && (Minecraft.getInstance().level != worldReference || Iris.getPipelineManager().getPipelineNullable() != pipelineReference)) throw new IllegalStateException("World/pipeline changed between boundaries");
            if (feature.equals("terrain") && cameraReference != null) checkTerrainSceneIdentity();
            if (feature.equals("ssr_cost") && cameraReference != null) checkSsrCostSceneIdentity();
            if (feature.equals("live_pixel") && cameraReference != null) checkLivePixelSceneIdentity();
            if (feature.equals("water_shadow_zero") && cameraReference != null) checkWaterShadowZeroSceneIdentity();
            if ((feature.equals("air_fog_ablation") || originalNullControl) && cameraReference != null) checkAirFogAblationSceneIdentity();
            if (phase < 0) {
                if ((now - started) * 1e-9 < initialWarmup) return;
                if (feature.equals("global") || feature.equals("effects")) {
                    inputs.validateNoAtmosphereGuard();
                    if (feature.equals("effects")) header.addProperty("atmosphere_guard_absent", true);
                }
                if (feature.equals("terrain")) {
                    inputs.validateNoAtmosphereGuard();
                    header.addProperty("atmosphere_guard_absent", true);
                    JsonObject initial = inputs.terrainInputState();
                    WorkloadInputs.validateTerrain(initial, initialFeatureEnabled);
                    stableReference = WorkloadInputs.withoutFeature(initial, feature);
                    worldReference = Minecraft.getInstance().level;
                    pipelineReference = Iris.getPipelineManager().getPipelineNullable();
                    cameraReference = Minecraft.getInstance().getCameraEntity();
                    header.add("stable_input_reference", stableReference);
                    header.add("terrain_initial_observation", initial);
                    addArtifactIdentity();
                }
                if (feature.equals("ssr_cost")) {
                    inputs.validateNoAtmosphereGuard();
                    header.addProperty("atmosphere_guard_absent", true);
                    header.add("ssr_cost_archive_qualification", inputs.qualifySsrCostArchive());
                }
                if (isReferenceDiagnostic()) {
                    inputs.validateNoAtmosphereGuard();
                    header.addProperty("atmosphere_guard_absent", true);
                }
                startPhase(now);
                return;
            }
            var observedWindow = window();
            if (!sampling) {
                current.window.observeWarmup(observedWindow, (now - phaseStarted) * 1e-9, GpuTimers.frameId());
                if (feature.equals("terrain")) maybeRearmTerrain(now);
                if ((now - phaseStarted) * 1e-9 < switchWarmup) return;
                if (feature.equals("terrain")) {
                    if (current.enabled && (current.terrainRearm == null || (now - current.terrainRearmNanos) * 1e-9 < ShaderLodConfig.TERRAIN_WARMUP_SECONDS.get() + 30)) throw new IllegalStateException("Terrain ON missing eligible manual re-arm or insufficient post-rearm settling");
                    current.settleElapsedSeconds = (now - phaseStarted) * 1e-9;
                }
                if (isStrictCoreDiagnostic()) current.settleElapsedSeconds = (now - phaseStarted) * 1e-9;
                JsonObject state = benchmarkState();
                validate(state, current.enabled);
                if (stableReference == null) {
                    stableReference = WorkloadInputs.withoutFeature(state, feature);
                    worldReference = Minecraft.getInstance().level;
                    pipelineReference = Iris.getPipelineManager().getPipelineNullable();
                    if (feature.equals("ssr_cost") || isStrictCoreDiagnostic()) cameraReference = Minecraft.getInstance().getCameraEntity();
                    header.add("stable_input_reference", stableReference);
                    if (!isStrictCoreDiagnostic()) addArtifactIdentity();
                } else checkIdentity(state);
                var backend = GpuTimers.snapshot();
                if (feature.equals("live_pixel") && backend.sampleInterval() != 5) throw new IllegalStateException("Live pixel actual GPU cadence differs from N5");
                if (feature.equals("water_shadow_zero") && backend.sampleInterval() != 5) throw new IllegalStateException("Water shadow zero actual GPU cadence differs from N5");
                if ((feature.equals("air_fog_ablation") || originalNullControl) && backend.sampleInterval() != 5) throw new IllegalStateException("Air fog ablation actual GPU cadence differs from N5");
                if (!WorkloadInputs.gpuBackendAccepted(backend.backend(), gpuRequested)) throw new IllegalStateException("Requested GPU queries unavailable: " + backend.status());
                current.inputStart = state;
                current.temporalStart = inputs.temporalInputs();
                WorkloadInputs.validateWetness(current.temporalStart, false);
                if (feature.equals("air_fog_ablation") || originalNullControl) {
                    WorkloadInputs.validateAirFogAblationTemporal(current.temporalStart, false);
                    WorkloadInputs.validateAirFogAblationClocks(phases.isEmpty() ? temporalInitial : phases.getLast().temporalEnd, current.temporalStart);
                }
                GpuTimers.resetSamples();
                ShaderInputTracker.beginMeasurement();
                ShadowInputObserver.beginMeasurement();
                current.startFrame = GpuTimers.frameId();
                current.startedAtUtc = java.time.Instant.now().toString();
                measurementStarted = System.nanoTime();
                sampling = true;
                marker = new PhaseMarker(sessionId, current.id, phase + 1, current.enabled, true);
                current.window.observeCapture(observedWindow, 0, current.startFrame);
                return;
            }
            current.window.observeCapture(observedWindow, (now - measurementStarted) * 1e-9, GpuTimers.frameId());
            for (var sample : gpu) if (sample.frameId() > current.startFrame) current.gpu.add(sample);
            for (var sample : cpu) if (sample.frameId() > current.startFrame) current.cpu.add(sample);
            if ((now - measurementStarted) * 1e-9 >= measurement) finishPhase(now);
        } catch (Exception failure) { fail(failure); }
    }

    private void startPhase(long now) {
        phase++;
        boolean enabled = (phase % 2 == 0) == startEnabled;
        if (feature.equals("coherence")) ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.set(enabled);
        else if (feature.equals("terrain")) ShaderLodConfig.TERRAIN_ENABLED.set(enabled);
        else ShaderLodConfig.ENABLED.set(enabled);
        current = new Phase(phase + 1, enabled);
        phaseStarted = now;
        sampling = false;
        GpuTimers.resetSamples();
        marker = new PhaseMarker(sessionId, current.id, phase + 1, enabled, false);
        LogUtils.getLogger().info("ShaderLOD in-process phase {} settling: id={}, enabled={}", phase + 1, current.id, enabled);
    }

    private void finishPhase(long now) throws Exception {
        current.elapsedSeconds = (now - measurementStarted) * 1e-9;
        current.endFrame = GpuTimers.frameId();
        current.renderFrames = current.endFrame - current.startFrame;
        current.inputEnd = benchmarkState();
        validate(current.inputEnd, current.enabled);
        checkIdentity(current.inputEnd);
        if (!WorkloadInputs.semanticState(current.inputStart, feature).equals(WorkloadInputs.semanticState(current.inputEnd, feature))) throw new IllegalStateException("Phase inputs changed during capture");
        if (feature.equals("terrain") && current.enabled && current.inputEnd.getAsJsonObject("geometry_policy").get("stableTicks").getAsInt()
                < current.inputStart.getAsJsonObject("geometry_policy").get("stableTicks").getAsInt()) throw new IllegalStateException("Terrain eligibility reset during capture");
        current.temporalEnd = inputs.temporalInputs();
        WorkloadInputs.validateWetness(current.temporalEnd, true);
        if (feature.equals("air_fog_ablation") || originalNullControl) {
            WorkloadInputs.validateAirFogAblationTemporal(current.temporalEnd, true);
            WorkloadInputs.validateAirFogAblationClocks(current.temporalStart, current.temporalEnd);
        }
        current.shadow = new GsonBuilder().create().toJsonTree(ShadowInputObserver.snapshot()).getAsJsonObject();
        current.timingSnapshot = new GsonBuilder().create().toJsonTree(GpuTimers.snapshot()).getAsJsonObject();
        current.windowState = new GsonBuilder().create().toJsonTree(current.window.snapshot()).getAsJsonObject();
        if (feature.equals("terrain") || feature.equals("ssr_cost")) validateTerrainWindow(current.windowState);
        if (isReferenceDiagnostic()) validateReferenceWindow(current.windowState, stableReference.getAsJsonObject((originalNullControl ? "original_null" : feature) + "_window_state"));
        if (current.renderFrames < 30 || gpuRequested && current.gpu.stream().noneMatch(sample -> sample.scope().equals(GpuTimers.FRAME))) throw new IllegalStateException("Insufficient render/timing observations");
        if (isReferenceDiagnostic()) validateReferenceGpuSamples(current.gpu, gpuRequested);
        if (feature.equals("air_fog_ablation") || originalNullControl) validateAirFogAblationTiming(current.gpu, current.cpu);
        if (cpuRequested && current.cpu.stream().noneMatch(sample -> sample.scope().equals("run-tick"))) throw new IllegalStateException("Requested CPU timing observations missing");
        current.gpuEpoch = current.gpu.isEmpty() ? null : current.gpu.get(0).epoch();
        current.cpuEpoch = current.cpu.isEmpty() ? null : current.cpu.get(0).epoch();
        if (current.gpu.stream().anyMatch(sample -> sample.epoch() != current.gpuEpoch)
                || current.cpu.stream().anyMatch(sample -> sample.epoch() != current.cpuEpoch)) throw new IllegalStateException("Timing epochs mixed within phase");
        current.image = "benchmark-" + sessionId + "-phase-" + (phase + 1) + ".png";
        sampling = false;
        awaitingImage = true;
        marker = new PhaseMarker(sessionId, current.id, phase + 1, current.enabled, false);
        write("capturing-images", null);
        var client = Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory, current.image, client.getMainRenderTarget(), message -> client.execute(() -> {
            try {
                Path image = directory.resolve("screenshots").resolve(current.image);
                if (!Files.isRegularFile(image)) throw new IllegalStateException("Phase screenshot missing");
                current.imageSha256 = WorkloadInputs.hash(image);
                current.status = "complete";
                phases.add(current);
                awaitingImage = false;
                write("running", null);
                if (phase == 3) complete();
                else startPhase(System.nanoTime());
            } catch (Exception failure) { fail(failure); }
        }));
    }

    private void validate(JsonObject state, boolean expectedEnabled) {
        if (originalNullControl) WorkloadInputs.validateOriginalNull(state, expectedEnabled);
        else if (feature.equals("coherence")) WorkloadInputs.validateCoherence(state, expectedEnabled);
        else if (feature.equals("effects")) WorkloadInputs.validateEffects(state, expectedEnabled);
        else if (feature.equals("terrain")) WorkloadInputs.validateTerrain(state, expectedEnabled);
        else if (feature.equals("ssr_cost")) WorkloadInputs.validateSsrCost(state, expectedEnabled);
        else if (feature.equals("spatial")) WorkloadInputs.validateSpatial(state, expectedEnabled);
        else if (feature.equals("volume")) WorkloadInputs.validateVolume(state, expectedEnabled);
        else if (feature.equals("live_pixel")) WorkloadInputs.validateLivePixel(state, expectedEnabled);
        else if (feature.equals("water_shadow_zero")) WorkloadInputs.validateWaterShadowZero(state, expectedEnabled);
        else if (feature.equals("air_fog_ablation")) WorkloadInputs.validateAirFogAblation(state, expectedEnabled);
        else {
            WorkloadInputs.validateAlloff(state);
            if (state.getAsJsonObject("lod_settings").get("enabled").getAsBoolean() != expectedEnabled) throw new IllegalStateException("Wrong live global flag");
        }
    }

    private JsonObject benchmarkState() throws Exception {
        if (originalNullControl) {
            var state = inputs.originalNullInputState();
            if (!state.get("loaded_artifact_evidence").equals(header.get("loaded_artifact_evidence"))) throw new IllegalStateException("Original null loaded artifacts changed");
            return state;
        }
        if (feature.equals("air_fog_ablation")) {
            var state = inputs.airFogAblationInputState();
            if (!state.get("air_fog_ablation_artifact_state").equals(header.get("air_fog_ablation_artifact_state_initial")))
                throw new IllegalStateException("Air fog loaded artifacts/classes changed after initial evidence");
            return state;
        }
        return feature.equals("terrain") ? inputs.terrainInputState() : feature.equals("ssr_cost") ? inputs.ssrCostInputState()
                : feature.equals("spatial") ? inputs.spatialInputState() : feature.equals("volume") ? inputs.volumeInputState()
                : feature.equals("live_pixel") ? inputs.livePixelInputState()
                : feature.equals("water_shadow_zero") ? inputs.waterShadowZeroInputState() : inputs.benchmarkInputState();
    }

    private void checkLivePixelSceneIdentity() {
        var client = Minecraft.getInstance(); var camera = client.getCameraEntity();
        if (camera == null || camera != cameraReference || camera.getX() != 500.5 || camera.getY() != 68 || camera.getZ() != 660.5
                || camera.getYRot() != 87.38f || camera.getXRot() != 3.735f || client.level.getDayTime() != 12500
                || client.options.renderDistance().get() != 24 || client.options.getEffectiveRenderDistance() != 24
                || client.options.fov().get() != 70 || !client.options.getCameraType().name().equals("FIRST_PERSON")) throw new IllegalStateException("Live pixel fixed scene/camera identity changed");
    }

    private void checkWaterShadowZeroSceneIdentity() {
        var client = Minecraft.getInstance(); var camera = client.getCameraEntity();
        if (camera == null || camera != cameraReference || camera.getX() != 500.5 || camera.getY() != 68 || camera.getZ() != 660.5
                || camera.getYRot() != 87.38f || camera.getXRot() != 3.735f || client.level.getDayTime() != 12500
                || client.options.renderDistance().get() != 24 || client.options.getEffectiveRenderDistance() != 24
                || client.options.fov().get() != 70 || !client.options.getCameraType().name().equals("FIRST_PERSON")) throw new IllegalStateException("Water shadow zero fixed scene/camera identity changed");
    }

    private void checkAirFogAblationSceneIdentity() {
        var client = Minecraft.getInstance(); var camera = client.getCameraEntity();
        if (camera == null || camera != cameraReference || camera.getX() != 500.5 || camera.getY() != 68 || camera.getZ() != 660.5
                || camera.getYRot() != 87.38f || camera.getXRot() != 3.735f || client.level.getDayTime() != 12500
                || client.options.renderDistance().get() != 24 || client.options.getEffectiveRenderDistance() != 24
                || client.level.getRainLevel(1) != 1 || client.level.getThunderLevel(1) != 0
                || client.options.fov().get() != 70 || !client.options.getCameraType().name().equals("FIRST_PERSON")) throw new IllegalStateException("Air fog ablation fixed scene/camera identity changed");
    }

    private void checkTerrainSceneIdentity() {
        var client = Minecraft.getInstance(); var camera = client.getCameraEntity();
        if (camera != cameraReference || camera == null || client.options.renderDistance().get() != 24
                || camera.getX() != stableReference.get("x").getAsDouble() || camera.getY() != stableReference.get("y").getAsDouble()
                || camera.getZ() != stableReference.get("z").getAsDouble() || camera.getYRot() != stableReference.get("yaw").getAsFloat()
                || camera.getXRot() != stableReference.get("pitch").getAsFloat() || !ShaderLodConfig.ENABLED.get()
                || !ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.get() || !client.options.getCameraType().name().equals("FIRST_PERSON")
                || client.options.fov().get() != stableReference.get("terrain_fov").getAsInt()) throw new IllegalStateException("Inspected terrain scene identity/global/manual acknowledgement changed");
    }

    private void checkSsrCostSceneIdentity() {
        var client = Minecraft.getInstance(); var camera = client.getCameraEntity();
        if (camera == null || camera != cameraReference || client.options.renderDistance().get() != 24
                || camera.getX() != stableReference.get("x").getAsDouble() || camera.getY() != stableReference.get("y").getAsDouble()
                || camera.getZ() != stableReference.get("z").getAsDouble() || camera.getYRot() != stableReference.get("yaw").getAsFloat()
                || camera.getXRot() != stableReference.get("pitch").getAsFloat() || !client.options.getCameraType().name().equals("FIRST_PERSON")
                || client.options.fov().get() != stableReference.get("ssr_cost_fov").getAsInt() || client.level.getDayTime() != 12500) {
            throw new IllegalStateException("SSR cost fixed camera/sun/radius identity changed");
        }
    }

    private void maybeRearmTerrain(long now) throws Exception {
        if (!current.enabled || current.terrainRearm != null) return;
        var policy = com.pp.shaderlod.GeometryLodController.status();
        if (!List.of("warming DH scene", "DH replacement coverage unconfirmed", "DH terrain cap active").contains(policy.reason())) return;
        checkTerrainSceneIdentity();
        JsonObject observed = inputs.terrainInputState();
        WorkloadInputs.validateTerrainPrerequisites(observed);
        checkIdentity(observed);
        if (!observed.get("terrain_configured").getAsBoolean()) throw new IllegalStateException("Terrain ON flag not applied before re-arm");
        boolean confirmed = com.pp.shaderlod.GeometryLodController.confirmCurrentScene();
        current.terrainRearmNanos = System.nanoTime();
        current.terrainRearm = new JsonObject();
        current.terrainRearm.addProperty("attempts", 1);
        current.terrainRearm.addProperty("succeeded", confirmed);
        current.terrainRearm.addProperty("elapsed_settle_seconds", (current.terrainRearmNanos - phaseStarted) * 1e-9);
        current.terrainRearm.addProperty("at_utc", java.time.Instant.now().toString());
        current.terrainRearm.addProperty("scene_identifier", terrainSceneIdentifier);
        current.terrainRearm.addProperty("api", "GeometryLodController.confirmCurrentScene");
        current.terrainRearm.add("observed_input", observed);
        if (!confirmed) throw new IllegalStateException("Eligible fixed-scene manual terrain re-arm failed; no retry");
    }

    private static void validateTerrainWindow(JsonObject snapshot) {
        var capture = snapshot.getAsJsonObject("capture");
        long observations = capture.get("observations").getAsLong();
        if (observations < 1 || capture.get("available_observations").getAsLong() != observations
                || capture.get("unavailable_observations").getAsLong() != 0 || capture.get("dropped_transitions").getAsLong() != 0
                || capture.getAsJsonObject("transition_counts").size() != 0 || capture.getAsJsonArray("transitions").size() != 0
                || !capture.getAsJsonObject("start").get("state").equals(capture.getAsJsonObject("end").get("state"))) {
            throw new IllegalStateException("Terrain capture window/pacing changed or unavailable");
        }
    }

    private void checkIdentity(JsonObject state) {
        if (!WorkloadInputs.withoutFeature(state, feature).equals(stableReference)) throw new IllegalStateException("Input changed beyond the selected feature flag");
        if (Minecraft.getInstance().level != worldReference || Iris.getPipelineManager().getPipelineNullable() != pipelineReference) throw new IllegalStateException("World/pipeline reloaded in one-JVM control");
    }

    static void validateReferenceGpuSamples(List<GpuTimers.Sample> samples, boolean requested) {
        if (requested && samples.stream().filter(sample -> sample.scope().equals(GpuTimers.FRAME)
                && Double.isFinite(sample.milliseconds()) && sample.milliseconds() > 0).count() < 60) {
            throw new IllegalStateException("Reference diagnostic requires at least 60 positive full-frame GPU observations per phase");
        }
    }

    static void validateAirFogAblationTiming(List<GpuTimers.Sample> gpu, List<CpuTimers.Sample> cpu) {
        validateReferenceGpuSamples(gpu, true);
        if (gpu.stream().anyMatch(sample -> !sample.scope().equals(GpuTimers.FRAME) || !Double.isFinite(sample.milliseconds()) || sample.milliseconds() <= 0)
                || gpu.stream().map(GpuTimers.Sample::epoch).distinct().count() != 1)
            throw new IllegalStateException("Air fog requires positive full-frame-only GPU observations in one epoch");
        var coarse = List.of("run-tick", "render-and-blit", "swap-and-events", "frame-limiter", "game-render");
        if (cpu.stream().noneMatch(sample -> sample.scope().equals("run-tick"))
                || cpu.stream().anyMatch(sample -> !coarse.contains(sample.scope()) || !Double.isFinite(sample.wallMilliseconds()) || sample.wallMilliseconds() <= 0
                        || sample.threadMilliseconds() != null && (!Double.isFinite(sample.threadMilliseconds()) || sample.threadMilliseconds() < 0))
                || cpu.stream().map(CpuTimers.Sample::epoch).distinct().count() != 1)
            throw new IllegalStateException("Air fog requires finite coarse CPU observations in one epoch");
    }

    static void validateSpatialWindow(JsonObject snapshot, JsonObject reference) {
        validateReferenceWindow(snapshot, reference);
    }

    static void validateReferenceWindow(JsonObject snapshot, JsonObject reference) {
        validateTerrainWindow(snapshot); // Existing full-capture availability/transition guard.
        if (!snapshot.getAsJsonObject("capture").getAsJsonObject("start").get("state").equals(reference)) {
            throw new IllegalStateException("Reference window/focus/dimensions/pacing differ across phases");
        }
    }

    private void addArtifactIdentity() throws Exception {
        var files = new JsonObject();
        var inventory = new com.google.gson.JsonArray();
        var observedMods = new java.util.HashSet<String>();
        var observedLocators = new java.util.HashMap<String, JsonObject>();
        for (var modFile : ModList.get().getModFiles()) {
            Path file = modFile.getFile().getFilePath();
            String path = file.toAbsolutePath().toString();
            // Keep the legacy path map unchanged; virtual roots may share an empty path.
            // The volume inventory below retains each root's independently computed identity.
            com.google.gson.JsonElement identity = com.google.gson.JsonNull.INSTANCE;
            if (Files.isRegularFile(file)) identity = new com.google.gson.JsonPrimitive(WorkloadInputs.hash(file));
            else if (Files.isDirectory(file)) identity = directoryIdentity(file);
            if (!identity.isJsonNull()) files.add(path, identity.deepCopy());
            if (feature.equals("volume")) {
                var item = new JsonObject();
                var mods = new JsonObject();
                for (var mod : modFile.getMods()) {
                    if (!observedMods.add(mod.getModId())) throw new IllegalStateException("Volume duplicate loaded mod: " + mod.getModId());
                    mods.addProperty(mod.getModId(), mod.getVersion().toString());
                }
                item.add("mods", mods);
                item.addProperty("path", path);
                item.addProperty("filesystem_provider", file.getFileSystem().provider().getScheme());
                try {
                    String uri = file.toUri().toString();
                    item.addProperty("uri", uri);
                } catch (RuntimeException unavailable) {
                    item.add("uri", com.google.gson.JsonNull.INSTANCE);
                    item.addProperty("locator_error", unavailable.getClass().getSimpleName());
                }
                item.add("identity", identity.deepCopy());
                if (!item.get("uri").isJsonNull()) {
                    String locator = item.get("filesystem_provider").getAsString() + "\n" + item.get("uri").getAsString();
                    var previous = observedLocators.get(locator);
                    if (previous != null) {
                        // Development Minecraft and NeoForge may use one physical artifact.
                        if (!previous.get("path").equals(item.get("path")) || !previous.get("identity").equals(identity)) {
                            throw new IllegalStateException("Volume conflicting identity at artifact locator: " + locator);
                        }
                        for (var entry : mods.entrySet()) previous.getAsJsonObject("mods").add(entry.getKey(), entry.getValue().deepCopy());
                    } else {
                        observedLocators.put(locator, item);
                        inventory.add(item);
                    }
                } else inventory.add(item);
            }
        }
        header.add("loaded_artifacts_sha256", files);
        if (feature.equals("volume")) {
            if (!observedMods.equals(header.getAsJsonObject("loaded_mods").keySet())) throw new IllegalStateException("Volume loaded artifact inventory incomplete");
            header.add("loaded_artifact_inventory", inventory);
        }
        var resources = new JsonObject();
        for (String name : List.of("shaderlod.mixins.json", "shaderlod-dev.mixins.json")) {
            try (var stream = BenchmarkSession.class.getClassLoader().getResourceAsStream(name)) {
                if (stream != null) resources.addProperty(name, java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }
        }
        header.add("loaded_mixin_resources_sha256", resources);
        for (String name : List.of("control.json", "workload-manifest.json")) {
            Path path = directory.getParent().resolve(name);
            if (Files.exists(path)) {
                header.add(name, com.google.gson.JsonParser.parseString(Files.readString(path)));
                header.addProperty(name + "_sha256", WorkloadInputs.hash(path));
            }
        }
    }

    /** Direct roots plus source-qualified owning module/class records; outside timed windows. */
    static JsonObject airFogAblationArtifactState() throws Exception {
        var result = new JsonObject();
        var loaded = LoadedArtifactEvidence.capture();
        result.add("loaded_artifacts", loaded);
        var classes = new JsonObject();
        for (Class<?> owner : List.of(BenchmarkSession.class, WorkloadInputs.class)) {
            var record = new JsonObject();
            try {
                var sourceUri = owner.getProtectionDomain().getCodeSource().getLocation().toURI();
                record.addProperty("uri", sourceUri.toString());
                var resource = owner.getResource(owner.getSimpleName() + ".class");
                if (resource == null) throw new IllegalStateException("Owning class resource unavailable");
                record.addProperty("class_resource_uri", resource.toURI().toString());
                String classSha;
                try (var bytes = resource.openStream()) {
                    classSha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
                }
                record = airFogAblationOwningSource(owner, Path.of(sourceUri), Path.of(resource.toURI()), classSha, loaded);
                record.addProperty("raw_code_source_uri", sourceUri.toString());
                record.addProperty("raw_class_resource_uri", resource.toURI().toString());
            } catch (Exception failure) {
                record.addProperty("qualification_error", failure.toString());
            }
            classes.add(owner.getName(), record);
        }
        result.add("owning_code_sources", classes);
        try { validateAirFogAblationArtifactState(result); }
        catch (RuntimeException failure) {
            // Constructor failure may precede benchmark.json; retain the two exact raw
            // owning URIs and their failed records in the crash/console exception.
            throw new IllegalStateException("Air fog owning source qualification failed; records=" + classes, failure);
        }
        return result;
    }

    /** Pinned securejarhandler API resolution, never string/regex stripping of a virtual URI. */
    static JsonObject airFogAblationOwningSource(Class<?> owner, Path source, Path resource, String classSha, JsonObject loaded) throws Exception {
        LoadedArtifactEvidence.requireValid(loaded);
        String member = owner.getName().replace('.', '/') + ".class";
        if (!source.equals(source.toAbsolutePath().normalize()) || !Files.isDirectory(source))
            throw new IllegalStateException("Owning module source is not a canonical directory root");
        final Path primary;
        if (source instanceof cpw.mods.niofs.union.UnionPath union) {
            if (!source.equals(union.getFileSystem().getRoot())) throw new IllegalStateException("Owning Union source is not its module root");
            primary = union.getFileSystem().getPrimaryPath();
        } else if (source.getFileSystem().provider().getScheme().equals("file")) primary = source;
        else throw new IllegalStateException("Unknown owning source filesystem provider");
        if (!primary.getFileSystem().provider().getScheme().equals("file") || !primary.equals(primary.toAbsolutePath().normalize())
                || Files.isSymbolicLink(primary) || !Files.isDirectory(primary)) throw new IllegalStateException("Owning physical primary root unresolved");
        if (!resource.equals(source.resolve(member)) || !Files.isRegularFile(resource)
                || !classSha.matches("[0-9a-f]{64}") || !classSha.equals(WorkloadInputs.hash(resource))
                || !classSha.equals(WorkloadInputs.hash(primary.resolve(member))))
            throw new IllegalStateException("Owning class resource/source/physical member differs");
        JsonObject expected = null;
        for (var item : loaded.getAsJsonArray("loaded_artifact_inventory")) {
            var root = item.getAsJsonObject();
            if (root.getAsJsonObject("mods").has("shaderlod_dev")) {
                if (expected != null) throw new IllegalStateException("Duplicate owning development module root");
                expected = root;
            }
        }
        if (expected == null) throw new IllegalStateException("Owning development module root unavailable");
        var physical = LoadedArtifactEvidence.observe(primary, expected.getAsJsonObject("mods"));
        if (!physical.equals(expected)) throw new IllegalStateException("Owning primary root does not match loaded development module identity");
        var virtual = LoadedArtifactEvidence.observe(source, expected.getAsJsonObject("mods"));
        var result = new JsonObject();
        result.addProperty("uri", source.toUri().toString());
        result.addProperty("filesystem_provider", source.getFileSystem().provider().getScheme());
        result.addProperty("path", source.toAbsolutePath().toString());
        result.add("identity", virtual.get("identity").deepCopy());
        result.add("primary_root", physical);
        result.addProperty("class_member", member);
        result.addProperty("class_resource_uri", resource.toUri().toString());
        result.addProperty("class_sha256", classSha);
        result.addProperty("raw_code_source_uri", source.toUri().toString());
        result.addProperty("raw_class_resource_uri", resource.toUri().toString());
        return result;
    }

    static void validateAirFogAblationArtifactState(JsonObject state) {
        if (state == null) throw new IllegalStateException("Air fog artifact state unavailable");
        var loaded = state.getAsJsonObject("loaded_artifacts");
        LoadedArtifactEvidence.requireValid(loaded);
        var roots = state.getAsJsonObject("owning_code_sources");
        if (roots == null || roots.size() != 2) throw new IllegalStateException("Air fog owning code sources incomplete");
        for (Class<?> owner : List.of(BenchmarkSession.class, WorkloadInputs.class)) {
            var record = roots.getAsJsonObject(owner.getName());
            try {
                if (record == null || record.has("qualification_error")) throw new IllegalStateException("Owning source record failed");
                var recomputed = airFogAblationOwningSource(owner, Path.of(java.net.URI.create(record.get("uri").getAsString())),
                        Path.of(java.net.URI.create(record.get("class_resource_uri").getAsString())), record.get("class_sha256").getAsString(), loaded);
                if (!Path.of(java.net.URI.create(record.get("raw_code_source_uri").getAsString())).equals(Path.of(java.net.URI.create(record.get("uri").getAsString())))
                        || !Path.of(java.net.URI.create(record.get("raw_class_resource_uri").getAsString())).equals(Path.of(java.net.URI.create(record.get("class_resource_uri").getAsString()))))
                    throw new IllegalStateException("Raw owning loader/resource URI binding differs");
                recomputed.add("raw_code_source_uri", record.get("raw_code_source_uri").deepCopy());
                recomputed.add("raw_class_resource_uri", record.get("raw_class_resource_uri").deepCopy());
                if (!record.equals(recomputed)) throw new IllegalStateException("Owning source/provider/path/member/tree identity differs");
                var directory = record.getAsJsonObject("identity");
                if (!directory.get("complete").getAsBoolean() || directory.get("files").getAsInt() <= 0 || directory.get("bytes").getAsLong() <= 0)
                    throw new IllegalStateException("Owning source directory incomplete");
            } catch (Exception failure) {
                throw new IllegalStateException("Air fog owning class identity unavailable; owner=" + owner.getName() + "; record=" + record, failure);
            }
        }
    }

    private static JsonObject directoryIdentity(Path root) throws Exception {
        var result = new JsonObject();
        result.addProperty("kind", "development directory; regular files only, symlinks excluded");
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        int count = 0;
        long bytes = 0;
        try (var paths = Files.walk(root)) {
            var iterator = paths.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)).sorted().iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                bytes += Files.size(path);
                if (++count > 5000 || bytes > 32L * 1024 * 1024) {
                    result.addProperty("complete", false);
                    result.addProperty("limit", "5000 files / 32 MiB; no partial tree hash advertised");
                    return result;
                }
                String entry = root.relativize(path).toString().replace('\\', '/') + "\0" + WorkloadInputs.hash(path) + "\n";
                digest.update(entry.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        result.addProperty("complete", true);
        result.addProperty("files", count);
        result.addProperty("bytes", bytes);
        result.addProperty("tree_sha256", java.util.HexFormat.of().formatHex(digest.digest()));
        return result;
    }

    private void complete() {
        done = true;
        restore();
        write("complete", null);
        if (Boolean.getBoolean("shaderlod.dev.benchmarkOnly")) Minecraft.getInstance().stop();
    }

    private void fail(Exception failure) {
        done = true;
        if (current != null && !phases.contains(current)) {
            current.status = "failed";
            current.windowState = new GsonBuilder().create().toJsonTree(current.window.snapshot()).getAsJsonObject();
            phases.add(current);
        }
        restore();
        write("failed", failure.toString());
        LogUtils.getLogger().error("ShaderLOD in-process control failed", failure);
        if (Boolean.getBoolean("shaderlod.dev.benchmarkOnly")) Minecraft.getInstance().stop();
    }

    private void restore() {
        if (feature.equals("coherence")) {
            ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.set(initialFeatureEnabled);
            com.pp.shaderlod.ShadowFrustumCoherenceController.configure(false); // fail open until normal eligibility tick restores an initially enabled feature
        }
        if (feature.equals("terrain")) ShaderLodConfig.TERRAIN_ENABLED.set(initialFeatureEnabled);
        ShaderLodConfig.ENABLED.set(initialEnabled);
        Minecraft.getInstance().options.hideGui = initialHideGui;
        marker = null;
    }

    private void write(String status, String error) {
        try {
            var report = header.deepCopy();
            report.addProperty("status", status);
            if (error != null) report.addProperty("error", error);
            report.addProperty("updated_at_utc", java.time.Instant.now().toString());
            report.addProperty("backend", GpuTimers.snapshot().backend());
            report.addProperty("sampling_policy", GpuTimers.samplingPolicy());
            report.add("phases", new GsonBuilder().create().toJsonTree(phases));
            if (current != null && !phases.contains(current)) report.add("partial_phase", new GsonBuilder().create().toJsonTree(current));
            report.addProperty("restored_initial_enabled", done && (!(feature.equals("ssr_cost") || isReferenceDiagnostic()) || ShaderLodConfig.ENABLED.get() == initialEnabled));
            report.addProperty("restored_initial_feature", done && (!feature.equals("terrain") || ShaderLodConfig.TERRAIN_ENABLED.get() == initialFeatureEnabled));
            Path temporary = directory.resolve("benchmark.json.tmp"), target = directory.resolve("benchmark.json");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) { LogUtils.getLogger().error("Cannot save in-process benchmark", failure); }
    }

    private static double seconds(String property, double defaultValue) {
        double value = Double.parseDouble(System.getProperty("shaderlod.dev." + property, Double.toString(defaultValue)));
        if (!Double.isFinite(value) || value < 1) throw new IllegalArgumentException("Invalid benchmark seconds: " + property);
        return value;
    }

    private boolean isReferenceDiagnostic() { return originalNullControl || feature.equals("spatial") || feature.equals("volume") || feature.equals("live_pixel") || feature.equals("water_shadow_zero") || feature.equals("air_fog_ablation"); }
    private boolean isStrictCoreDiagnostic() { return originalNullControl || feature.equals("live_pixel") || feature.equals("water_shadow_zero") || feature.equals("air_fog_ablation"); }

    static void validateLivePixelRecipe(double warmup, double settle, double capture, boolean startsEnabled, boolean gpu, boolean cpu) {
        if (!Double.isFinite(warmup) || !Double.isFinite(settle) || !Double.isFinite(capture)
                || warmup < 30 || settle < 8 || capture < 20 || startsEnabled || !gpu || !cpu
                || Integer.parseInt(System.getProperty("shaderlod.dev.gpuSampleInterval", "1")) != 5
                || Double.parseDouble(System.getProperty("shaderlod.dev.fixedWetness", "NaN")) != 0) throw new IllegalStateException("Live pixel requires OFF/ON/OFF/ON, >=30s initial warmup, >=8s settle, >=20s capture, CPU/full-frame GPU N5 and fixed wetness0");
        for (String name : livePixelObservers()) if (Boolean.getBoolean("shaderlod.dev." + name)) throw new IllegalStateException("Live pixel diagnostic observer must be OFF: " + name);
        for (String name : List.of("cpuLayerTiming", "gpuPassTiming", "gpuAttribution")) if (Boolean.parseBoolean(System.getProperty("shaderlod.dev." + name, "true"))) throw new IllegalStateException("Live pixel detailed timing must be OFF: " + name);
    }

    static void validateWaterShadowZeroRecipe(double warmup, double settle, double capture, boolean startsEnabled, boolean gpu, boolean cpu) {
        if (!Double.isFinite(warmup) || !Double.isFinite(settle) || !Double.isFinite(capture)
                || warmup < 30 || settle < 8 || capture < 20 || startsEnabled || !gpu || !cpu
                || Integer.parseInt(System.getProperty("shaderlod.dev.gpuSampleInterval", "1")) != 5
                || Double.parseDouble(System.getProperty("shaderlod.dev.fixedWetness", "NaN")) != 0) throw new IllegalStateException("Water shadow zero requires OFF/ON/OFF/ON, >=30s initial warmup, >=8s settle, >=20s capture, CPU/full-frame GPU N5 and fixed wetness0");
        for (String name : waterShadowZeroObservers()) if (Boolean.getBoolean("shaderlod.dev." + name)) throw new IllegalStateException("Water shadow zero diagnostic observer must be OFF: " + name);
        for (String name : List.of("cpuLayerTiming", "gpuPassTiming", "gpuAttribution")) if (Boolean.parseBoolean(System.getProperty("shaderlod.dev." + name, "true"))) throw new IllegalStateException("Water shadow zero detailed timing must be OFF: " + name);
        if (!System.getProperty("shaderlod.dev.cameraQuality", "").isEmpty()) throw new IllegalStateException("Water shadow zero camera quality run must be OFF");
    }

    static void validateAirFogAblationRecipe(double warmup, double settle, double capture, boolean startsEnabled, boolean gpu, boolean cpu) {
        if (!Double.isFinite(warmup) || !Double.isFinite(settle) || !Double.isFinite(capture)
                || warmup < 30 || settle < 8 || capture < 20 || startsEnabled || !gpu || !cpu
                || Integer.parseInt(System.getProperty("shaderlod.dev.gpuSampleInterval", "1")) != 5
                || Double.parseDouble(System.getProperty("shaderlod.dev.fixedWetness", "NaN")) != 1) throw new IllegalStateException("Air fog ablation requires OFF/ON/OFF/ON, >=30s initial warmup, >=8s settle, >=20s capture, CPU/full-frame GPU N5 and fixed wetness1");
        for (String name : airFogAblationObservers()) if (Boolean.getBoolean("shaderlod.dev." + name)) throw new IllegalStateException("Air fog ablation diagnostic observer must be OFF: " + name);
        for (String name : List.of("cpuLayerTiming", "gpuPassTiming", "gpuAttribution")) if (Boolean.parseBoolean(System.getProperty("shaderlod.dev." + name, "true"))) throw new IllegalStateException("Air fog ablation detailed timing must be OFF: " + name);
        if (!System.getProperty("shaderlod.dev.cameraQuality", "").isEmpty()) throw new IllegalStateException("Air fog ablation camera quality run must be OFF");
    }

    private static List<String> livePixelObservers() {
        return List.of("shadowCoherenceOracle", "visibilityLists", "shadowObserve", "workTrace", "workTraceOnly", "spatialLayoutProbe", "spatialLayoutOnly", "cloudSamplerProbe", "pixelCoverage", "pixelCoverageOnly", "dhDrawEvidence", "waterSceneSurvey", "smokeTest", "gpuBenchmark", "cameraQualityOnly");
    }

    private static List<String> waterShadowZeroObservers() {
        var observers = new java.util.ArrayList<>(livePixelObservers());
        observers.add("smoke");
        return observers;
    }

    private static List<String> airFogAblationObservers() {
        var observers = new java.util.ArrayList<>(waterShadowZeroObservers());
        observers.addAll(List.of("pixelHistoryCapture", "pixelHistoryCaptureOnly", "measureOnly", "measureWorkload"));
        return observers;
    }

    private static String benchmarkFeature() {
        String value = System.getProperty("shaderlod.dev.benchmarkFeature", "global");
        if (!value.equals("global") && !value.equals("coherence") && !value.equals("effects") && !value.equals("terrain") && !value.equals("ssr_cost") && !value.equals("spatial") && !value.equals("volume") && !value.equals("live_pixel") && !value.equals("water_shadow_zero") && !value.equals("air_fog_ablation")) throw new IllegalArgumentException("Unknown benchmark feature: " + value);
        return value;
    }

    private static WindowStateTracker.State window() {
        var client = Minecraft.getInstance(); var window = client.getWindow();
        return WindowStateTracker.readWindow(window.getWindow(), window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(),
                new WindowStateTracker.Pacing(client.options.pauseOnLostFocus, window.isFullscreen(), client.options.framerateLimit().get(), client.options.enableVsync().get()));
    }

    private static final class Phase {
        final String id = UUID.randomUUID().toString();
        final int index;
        final boolean enabled;
        final List<GpuTimers.Sample> gpu = new ArrayList<>();
        final List<CpuTimers.Sample> cpu = new ArrayList<>();
        final transient WindowStateTracker window = new WindowStateTracker();
        String status = "capturing", startedAtUtc, image, imageSha256;
        long startFrame, endFrame, renderFrames;
        Long gpuEpoch, cpuEpoch;
        double elapsedSeconds;
        Double settleElapsedSeconds;
        transient long terrainRearmNanos;
        JsonObject inputStart, inputEnd, temporalStart, temporalEnd, shadow, timingSnapshot, windowState, terrainRearm;
        Phase(int index, boolean enabled) { this.index = index; this.enabled = enabled; }
    }
}
