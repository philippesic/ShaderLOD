package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.DhRenderEvidence;
import com.pp.shaderlod.dev.timing.ShaderInputTracker;
import com.pp.shaderlod.dev.timing.ShadowInputObserver;
import com.pp.shaderlod.dev.timing.WindowStateTracker;
import com.pp.shaderlod.dev.timing.VisibilityListPhaseObserver;
import com.pp.shaderlod.dev.timing.ShadowCoherenceOracle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.ModList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fixed configuration run: independent of ShaderLOD, CPU data separate from GPU. */
final class WorkloadSession {
    private final Path directory;
    private final WorkloadInputs inputs;
    private final List<GpuTimers.Sample> gpu = new ArrayList<>();
    private final List<CpuTimers.Sample> cpu = new ArrayList<>();
    private final WindowStateTracker windowState = new WindowStateTracker();
    private final double warmup = Double.parseDouble(System.getProperty("shaderlod.dev.initialWarmup", "60"));
    private final double duration = Double.parseDouble(System.getProperty("shaderlod.dev.measurementSeconds", "30"));
    private final long started = System.nanoTime();
    private long measurementStarted;
    private final JsonObject request;
    private JsonObject inputStart;
    private final JsonObject temporalInitial;
    private JsonObject temporalStart;
    private String capturedAt;
    private boolean measuring, done;
    private int startFrames;

    WorkloadSession(Path directory) {
        this.directory = directory;
        this.inputs = new WorkloadInputs(directory);
        Path path = directory.resolve("workload-request.json");
        try {
            request = Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : new JsonObject();
        } catch (Exception failure) { throw new IllegalStateException("Invalid workload request", failure); }
        temporalInitial = temporalInputs();
        if (ShadowInputObserver.enabled()) {
            var options = new java.util.TreeMap<String, String>();
            effectiveOptions().entrySet().forEach(entry -> options.put(entry.getKey(), entry.getValue().getAsString()));
            ShadowInputObserver.configurePhotonFeatures(options);
        }
    }
    String status() { return done ? "Workload capture finished" : measuring ? "Workload measuring CPU/GPU" : "Workload warming"; }
    void tick() {
        var gpuSamples = GpuTimers.drainSamples();
        var cpuSamples = CpuTimers.drainSamples();
        if (done) return;
        long now = System.nanoTime();
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        var observedWindow = WindowStateTracker.readWindow(window.getWindow(), window.getScreenWidth(), window.getScreenHeight(),
                window.getWidth(), window.getHeight(), new WindowStateTracker.Pacing(client.options.pauseOnLostFocus,
                        window.isFullscreen(), client.options.framerateLimit().get(), client.options.enableVsync().get()));
        double elapsedWorkloadSeconds = (now - started) * 1e-9;
        long frameId = GpuTimers.frameId();
        if (!measuring) {
            if (elapsedWorkloadSeconds < warmup) {
                windowState.observeWarmup(observedWindow, elapsedWorkloadSeconds, frameId);
                return;
            }
            windowState.observeCapture(observedWindow, elapsedWorkloadSeconds, frameId);
            GpuTimers.resetSamples();
            try { inputStart = inputState(); }
            catch (Exception failure) { throw new IllegalStateException("Cannot fingerprint measurement inputs", failure); }
            capturedAt = java.time.Instant.now().toString();
            temporalStart = temporalInputs();
            ShaderInputTracker.beginMeasurement();
            ShadowInputObserver.beginMeasurement();
            VisibilityListPhaseObserver.beginMeasurement();
            ShadowCoherenceOracle.beginMeasurement();
            measuring = true; measurementStarted = now;
            startFrames = (int) GpuTimers.snapshot().renderFrames();
            LogUtils.getLogger().info("ShaderLOD workload measurement started: {}", directory);
            return;
        }
        windowState.observeCapture(observedWindow, elapsedWorkloadSeconds, frameId);
        gpu.addAll(gpuSamples); cpu.addAll(cpuSamples);
        if ((now - measurementStarted) * 1e-9 >= duration) {
            done = true;
            write((now - measurementStarted) * 1e-9);
            VisibilityListPhaseObserver.endMeasurement();
            ShadowCoherenceOracle.endMeasurement();
            Screenshot.grab(client.gameDirectory, "workload-final.png", client.getMainRenderTarget(), message -> {
                LogUtils.getLogger().info("Workload screenshot: {}", message.getString());
                if (Boolean.getBoolean("shaderlod.dev.measureOnly")) client.execute(client::stop);
            });
        }
    }
    private void write(double seconds) {
        try {
            var gson = new GsonBuilder().setPrettyPrinting().create();
            var client = Minecraft.getInstance();
            JsonObject report = new JsonObject();
            report.addProperty("status", "complete");
            report.addProperty("schema", 2);
            report.add("request", request.deepCopy());
            report.addProperty("measurement_started_at_utc", capturedAt);
            report.addProperty("written_at_utc", java.time.Instant.now().toString());
            report.add("input_state_start", inputStart);
            report.add("input_state_end", inputState());
            report.add("shader_temporal_inputs_initial", temporalInitial);
            report.add("shader_temporal_inputs_start", temporalStart);
            report.add("shader_temporal_inputs_end", temporalInputs());
            report.add("shadow_observation", gson.toJsonTree(ShadowInputObserver.snapshot()));
            report.add("visibility_observation", gson.toJsonTree(VisibilityListPhaseObserver.snapshot()));
            report.add("shadow_coherence_oracle", gson.toJsonTree(ShadowCoherenceOracle.snapshot()));
            report.add("window_state", gson.toJsonTree(windowState.snapshot()));
            if (DhRenderEvidence.enabled()) report.add("dh_draw_evidence", DhRenderEvidence.captureTerminalSnapshot());
            if (Boolean.getBoolean("shaderlod.dev.waterSceneSurvey")) report.add("water_scene_survey", WaterSceneSurvey.captureTerminalSnapshot(client));
            report.addProperty("shader_temporal_note", "Wetness is observed at normal Iris supplier evaluation; clocks/day/weather are CPU source getters. Dynamic wetness and frame phase can advance. No smoother reevaluation, GPU readback, or clock freeze.");
            report.add("effective_shader_options", inputStart.get("effective_shader_options").deepCopy());
            report.addProperty("effective_shader_options_sha256", inputStart.get("effective_shader_options_sha256").getAsString());
            var mods = new JsonObject();
            for (var mod : ModList.get().getMods()) mods.addProperty(mod.getModId(), mod.getVersion().toString());
            report.add("loaded_mods", mods);
            var runtimeProperties = new JsonObject();
            for (String property : List.of("shaderlod.dev.performanceStack", "shaderlod.dev.includeDh", "shaderlod.dev.gpuSampleInterval", "shaderlod.dev.gpuTiming", "shaderlod.dev.cpuLayerTiming", "shaderlod.dev.fixedWetness", "shaderlod.dev.shadowObserve", "shaderlod.dev.visibilityLists", "shaderlod.dev.shadowCoherenceOracle", "shaderlod.dev.benchmarkWindowScale", "shaderlod.dev.cloudSamplerProbe", "shaderlod.dev.dhDrawEvidence", "shaderlod.dev.waterSceneSurvey")) {
                runtimeProperties.addProperty(property, System.getProperty(property, "unspecified"));
            }
            report.add("runtime_properties", runtimeProperties);
            var artifacts = new JsonObject();
            Path modsDirectory = directory.resolve("mods");
            if (Files.isDirectory(modsDirectory)) try (var paths = Files.list(modsDirectory)) {
                for (Path jar : paths.filter(path -> path.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                    artifacts.addProperty(jar.getFileName().toString(), hash(jar));
                }
            }
            report.add("runtime_mod_jars_sha256", artifacts);
            var loadedArtifacts = new JsonObject();
            for (var modFile : ModList.get().getModFiles()) {
                Path path = modFile.getFile().getFilePath();
                var artifact = new JsonObject();
                artifact.addProperty("path", path.toAbsolutePath().toString());
                if (Files.isRegularFile(path)) artifact.addProperty("sha256", hash(path));
                else artifact.addProperty("kind", "development-directory");
                loadedArtifacts.add(modFile.getMods().stream().map(mod -> mod.getModId()).sorted().collect(java.util.stream.Collectors.joining(",")), artifact);
            }
            report.add("loaded_mod_artifacts", loadedArtifacts);
            report.addProperty("profile", directory.getFileName().toString());
            report.addProperty("modPresent", ModList.get().isLoaded("shaderlod"));
            report.addProperty("dhPresent", ModList.get().isLoaded("distanthorizons"));
            report.addProperty("shadow_culling_active", ModList.get().isLoaded("shaderlod")
                    && com.pp.shaderlod.ShadowCullingController.isActive());
            report.addProperty("separate_shadow_lists_active", ModList.get().isLoaded("shaderlod")
                    && com.pp.shaderlod.ShadowVisibilityListsController.isActive());
            report.addProperty("shadow_frustum_coherence_active", ModList.get().isLoaded("shaderlod")
                    && com.pp.shaderlod.ShadowFrustumCoherenceController.isActive());
            report.addProperty("elapsed_seconds", seconds);
            report.addProperty("warmup_seconds", warmup);
            report.addProperty("render_frames", GpuTimers.snapshot().renderFrames() - startFrames);
            report.addProperty("backend", GpuTimers.snapshot().backend());
            report.addProperty("sampling_policy", GpuTimers.samplingPolicy());
            report.addProperty("framebuffer_width", client.getWindow().getWidth());
            report.addProperty("framebuffer_height", client.getWindow().getHeight());
            report.addProperty("render_distance_chunks", client.options.renderDistance().get());
            report.addProperty("effective_render_distance_chunks", client.options.getEffectiveRenderDistance());
            if (ModList.get().isLoaded("shaderlod")) report.add("geometry_policy", gson.toJsonTree(com.pp.shaderlod.GeometryLodController.status()));
            report.add("gpu_samples", gson.toJsonTree(gpu)); report.add("cpu_samples", gson.toJsonTree(cpu));
            Path root = directory.getParent();
            if (Files.exists(root.resolve("control.json"))) report.addProperty("control_sha256", hash(root.resolve("control.json")));
            for (String file : List.of("control.json", "workload-manifest.json")) {
                if (Files.exists(root.resolve(file))) report.add(file, JsonParser.parseString(Files.readString(root.resolve(file))));
            }
            Path iris = directory.resolve("config/iris.properties");
            report.addProperty("iris_configuration", Files.readString(iris));
            var properties = new java.util.Properties();
            try (var reader = Files.newBufferedReader(iris)) { properties.load(reader); }
            String pack = properties.getProperty("shaderPack", "");
            Path options = directory.resolve("shaderpacks").resolve(pack + ".txt");
            if (Files.exists(options)) {
                report.addProperty("actual_shader_options", Files.readString(options));
                report.addProperty("actual_shader_options_sha256", hash(options));
            }
            Path archive = directory.resolve("shaderpacks").resolve(pack);
            if (Files.isRegularFile(archive)) report.addProperty("actual_shader_sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
            Path dhConfig = directory.resolve("config/DistantHorizons.toml");
            if (Files.exists(dhConfig)) report.addProperty("actual_dh_configuration", Files.readString(dhConfig));
            Path path = directory.resolve("workload-measurement.json");
            Files.writeString(path, gson.toJson(report));
            LogUtils.getLogger().info("ShaderLOD workload measurement written: {} GPU={} CPU={}", path, gpu.size(), cpu.size());
        } catch (Exception exception) { LogUtils.getLogger().error("Cannot save workload measurement", exception); }
    }

    private static String hash(Path path) throws Exception { return WorkloadInputs.hash(path); }
    private static JsonObject effectiveOptions() { return WorkloadInputs.effectiveOptions(); }
    private JsonObject temporalInputs() { return inputs.temporalInputs(); }
    private JsonObject inputState() throws Exception { return inputs.inputState(); }
}
