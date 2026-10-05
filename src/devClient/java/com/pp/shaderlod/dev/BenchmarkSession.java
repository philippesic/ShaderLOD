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
    private final boolean initialFeatureEnabled = feature.equals("coherence") ? ShaderLodConfig.SHADOW_FRUSTUM_COHERENCE.get() : initialEnabled;
    private final boolean initialHideGui = Minecraft.getInstance().options.hideGui;
    private final long started = System.nanoTime();
    private final JsonObject temporalInitial;
    private final JsonObject header = new JsonObject();
    private JsonObject stableReference;
    private Object worldReference, pipelineReference;
    private long phaseStarted, measurementStarted;
    private int phase = -1;
    private boolean sampling, done, awaitingImage;
    private Phase current;
    private static volatile PhaseMarker marker;

    BenchmarkSession(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        this.inputs = new WorkloadInputs(this.directory);
        if (Boolean.getBoolean("shaderlod.dev.measureWorkload")) throw new IllegalStateException("Benchmark and workload capture are mutually exclusive");
        if (feature.equals("coherence") && (!initialEnabled || Boolean.getBoolean("shaderlod.dev.shadowCoherenceOracle") || Boolean.getBoolean("shaderlod.dev.visibilityLists"))) throw new IllegalStateException("Coherence timing requires global enabled and oracle/phase observer disabled");
        temporalInitial = inputs.temporalInputs();
        Minecraft.getInstance().options.hideGui = true; // one fixed configured HUD policy for all phases
        header.addProperty("schema", 3);
        header.addProperty("session_id", sessionId);
        header.addProperty("created_at_utc", java.time.Instant.now().toString());
        header.addProperty("benchmark_feature", feature);
        header.addProperty("method", switch (feature) {
            case "coherence" -> "One JVM/world/pipeline; original Photon; only shadow frustum coherence changes";
            case "effects" -> "One JVM/world/pipeline; fixed five-module effect options; only live global bridge enabled changes";
            default -> "One JVM/world/pipeline; all five modules off; only live global bridge enabled changes";
        });
        header.addProperty("starting_order", startEnabled ? "ON/OFF/ON/OFF" : "OFF/ON/OFF/ON");
        header.addProperty("initial_warmup_seconds", initialWarmup);
        header.addProperty("settle_seconds", switchWarmup);
        header.addProperty("measurement_seconds", measurement);
        header.addProperty("gpu_requested", gpuRequested);
        header.addProperty("cpu_requested", cpuRequested);
        header.addProperty("configured_hide_gui", true);
        header.addProperty("initial_enabled", initialEnabled);
        header.addProperty("initial_feature_enabled", initialFeatureEnabled);
        header.addProperty("selected_flag", feature.equals("coherence") ? "rendering.shadowFrustumCoherence" : "lod.enabled");
        header.addProperty("uniform_observation_note", "Config values are intended bridge inputs; no GPU readback or proof of active linked uniforms. PhaseMarker allows a bounded dev observer to label original supplier evaluations.");
        header.addProperty("limits", "Correlated phases, not independent JVM repeats. GPU timeline is not busy utilization; CPU includes driver work. Clocks/noise keep advancing. No causal attribution to an individual uniform call.");
        header.add("shader_temporal_inputs_initial", temporalInitial);
        var mods = new JsonObject();
        for (var mod : ModList.get().getMods()) mods.addProperty(mod.getModId(), mod.getVersion().toString());
        header.add("loaded_mods", mods);
        var properties = new JsonObject();
        for (String name : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming", "gpuPassTiming", "gpuSampleInterval", "fixedWetness", "performanceStack", "includeDh", "benchmarkFeature", "shadowCoherenceOracle", "visibilityLists", "shadowObserve")) {
            properties.addProperty(name, System.getProperty("shaderlod.dev." + name, "unspecified"));
        }
        header.add("instrumentation_properties", properties);
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
            if (phase < 0) {
                if ((now - started) * 1e-9 < initialWarmup) return;
                if (feature.equals("global") || feature.equals("effects")) {
                    inputs.validateNoAtmosphereGuard();
                    if (feature.equals("effects")) header.addProperty("atmosphere_guard_absent", true);
                }
                startPhase(now);
                return;
            }
            var observedWindow = window();
            if (!sampling) {
                current.window.observeWarmup(observedWindow, (now - phaseStarted) * 1e-9, GpuTimers.frameId());
                if ((now - phaseStarted) * 1e-9 < switchWarmup) return;
                JsonObject state = inputs.benchmarkInputState();
                validate(state, current.enabled);
                if (stableReference == null) {
                    stableReference = WorkloadInputs.withoutFeature(state, feature);
                    worldReference = Minecraft.getInstance().level;
                    pipelineReference = Iris.getPipelineManager().getPipelineNullable();
                    header.add("stable_input_reference", stableReference);
                    addArtifactIdentity();
                } else checkIdentity(state);
                var backend = GpuTimers.snapshot();
                if (!WorkloadInputs.gpuBackendAccepted(backend.backend(), gpuRequested)) throw new IllegalStateException("Requested GPU queries unavailable: " + backend.status());
                current.inputStart = state;
                current.temporalStart = inputs.temporalInputs();
                WorkloadInputs.validateWetness(current.temporalStart, false);
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
        current.inputEnd = inputs.benchmarkInputState();
        validate(current.inputEnd, current.enabled);
        checkIdentity(current.inputEnd);
        if (!WorkloadInputs.semanticState(current.inputStart).equals(WorkloadInputs.semanticState(current.inputEnd))) throw new IllegalStateException("Phase inputs changed during capture");
        current.temporalEnd = inputs.temporalInputs();
        WorkloadInputs.validateWetness(current.temporalEnd, true);
        current.shadow = new GsonBuilder().create().toJsonTree(ShadowInputObserver.snapshot()).getAsJsonObject();
        current.timingSnapshot = new GsonBuilder().create().toJsonTree(GpuTimers.snapshot()).getAsJsonObject();
        current.windowState = new GsonBuilder().create().toJsonTree(current.window.snapshot()).getAsJsonObject();
        if (current.renderFrames < 30 || gpuRequested && current.gpu.stream().noneMatch(sample -> sample.scope().equals(GpuTimers.FRAME))) throw new IllegalStateException("Insufficient render/timing observations");
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
        if (feature.equals("coherence")) WorkloadInputs.validateCoherence(state, expectedEnabled);
        else if (feature.equals("effects")) WorkloadInputs.validateEffects(state, expectedEnabled);
        else {
            WorkloadInputs.validateAlloff(state);
            if (state.getAsJsonObject("lod_settings").get("enabled").getAsBoolean() != expectedEnabled) throw new IllegalStateException("Wrong live global flag");
        }
    }

    private void checkIdentity(JsonObject state) {
        if (!WorkloadInputs.withoutFeature(state, feature).equals(stableReference)) throw new IllegalStateException("Input changed beyond the selected feature flag");
        if (Minecraft.getInstance().level != worldReference || Iris.getPipelineManager().getPipelineNullable() != pipelineReference) throw new IllegalStateException("World/pipeline reloaded in one-JVM control");
    }

    private void addArtifactIdentity() throws Exception {
        var files = new JsonObject();
        for (var modFile : ModList.get().getModFiles()) {
            Path file = modFile.getFile().getFilePath();
            if (Files.isRegularFile(file)) files.addProperty(file.toAbsolutePath().toString(), WorkloadInputs.hash(file));
            else if (Files.isDirectory(file)) files.add(file.toAbsolutePath().toString(), directoryIdentity(file));
        }
        header.add("loaded_artifacts_sha256", files);
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
            report.addProperty("restored_initial_enabled", done);
            report.addProperty("restored_initial_feature", done);
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

    private static String benchmarkFeature() {
        String value = System.getProperty("shaderlod.dev.benchmarkFeature", "global");
        if (!value.equals("global") && !value.equals("coherence") && !value.equals("effects")) throw new IllegalArgumentException("Unknown benchmark feature: " + value);
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
        JsonObject inputStart, inputEnd, temporalStart, temporalEnd, shadow, timingSnapshot, windowState;
        Phase(int index, boolean enabled) { this.index = index; this.enabled = enabled; }
    }
}
