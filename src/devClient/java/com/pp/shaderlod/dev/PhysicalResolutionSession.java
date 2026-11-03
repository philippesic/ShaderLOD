package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.ShaderInputTracker;
import com.pp.shaderlod.dev.timing.WindowStateTracker;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded original-Photon physical-output headroom diagnostic, never an upscaler. */
final class PhysicalResolutionSession {
    static final String ORIGINAL = "120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead";
    static final double WARMUP = 30, SETTLE = 8, CAPTURE = 20, RESIZE_TIMEOUT = 30;
    static final List<String> OBSERVERS = List.of("shadowObserve", "visibilityLists", "shadowCoherenceOracle",
            "workTrace", "spatialLayoutProbe", "pixelCoverage", "cloudSamplerProbe", "coreUniformProbe",
            "effectRuntimeProbe", "rasterProbe", "dhDrawEvidence", "waterSceneSurvey", "pixelHistory", "pixelHistoryCapture",
            "pixelHistoryCaptureOnly", "workTraceOnly", "spatialLayoutOnly", "pixelCoverageOnly", "smoke", "smokeTest", "gpuBenchmark");
    static final Extent NATIVE = new Extent(1488, 836, 2976, 1672);
    static final Extent REDUCED = new Extent(1116, 627, 2232, 1254);
    static final List<Extent> ORDER = List.of(NATIVE, REDUCED, NATIVE);

    /** Small adapter to the separate gated CPU evidence hook. No GL readbacks in this API. */
    interface EvidenceSource {
        void armBoundary(String label);
        JsonObject snapshot();
        ResourceStamp resourceStamp();
    }
    /** Completion is a successful hook return, not an attempted selection. */
    record ResourceStamp(long allocationEpoch, long mainColorVersion, long mainDepthVersion,
                         long colorStorageEpoch, long depthStorageEpoch, long pipelineEpoch,
                         long completedBoundaryEpoch, long completedAllocationEpoch,
                         int irisWidth, int irisHeight, String pipelineIdentity) {}
    record Extent(int logicalWidth, int logicalHeight, int physicalWidth, int physicalHeight) {}
    record ObservedSize(int glfwWidth, int glfwHeight, int glfwFramebufferWidth, int glfwFramebufferHeight,
                        int cachedWidth, int cachedHeight, int mainWidth, int mainHeight,
                        int mainViewWidth, int mainViewHeight) {}

    static boolean matches(Extent expected, ObservedSize observed) {
        return observed.glfwWidth == expected.logicalWidth && observed.glfwHeight == expected.logicalHeight
                && observed.glfwFramebufferWidth == expected.physicalWidth && observed.glfwFramebufferHeight == expected.physicalHeight
                && observed.cachedWidth == expected.physicalWidth && observed.cachedHeight == expected.physicalHeight
                && observed.mainWidth == expected.physicalWidth && observed.mainHeight == expected.physicalHeight
                && observed.mainViewWidth == expected.physicalWidth && observed.mainViewHeight == expected.physicalHeight
                && (long) expected.logicalWidth * expected.physicalHeight == (long) expected.logicalHeight * expected.physicalWidth;
    }

    /** Pure lifecycle used by the focused modeled test. All transition frames are excluded. */
    static final class Timeline {
        enum State { WARMING, RESIZING, SETTLING, CAPTURING, RESTORING, COMPLETE, FAILED }
        State state = State.WARMING;
        int phase;
        long since, stableSince;
        ResourceStamp previous, stable;
        Timeline(long now) { since = now; }
        boolean warmed(long now) { return seconds(now, since) >= WARMUP; }
        void request(long now, ResourceStamp stamp) { state = State.RESIZING; since = now; previous = stamp; stable = null; }
        boolean observe(long now, boolean dimensionsMatch, ResourceStamp stamp) {
            if (state != State.RESIZING && state != State.SETTLING) throw new IllegalStateException("Not settling");
            if (seconds(now, since) >= RESIZE_TIMEOUT) throw new IllegalStateException("Resize/resource completion timed out");
            boolean completed = stamp.completedBoundaryEpoch > previous.completedBoundaryEpoch
                    && stamp.allocationEpoch > 0 && stamp.completedAllocationEpoch == stamp.allocationEpoch
                    && stamp.pipelineEpoch > 0 && stamp.pipelineIdentity != null && !stamp.pipelineIdentity.equals("unobserved");
            boolean allocated = phase == 0 || stamp.allocationEpoch > previous.allocationEpoch
                    && stamp.mainColorVersion > previous.mainColorVersion && stamp.mainDepthVersion > previous.mainDepthVersion
                    && stamp.colorStorageEpoch > previous.colorStorageEpoch && stamp.depthStorageEpoch > previous.depthStorageEpoch;
            if (!dimensionsMatch || !completed || !allocated) { stable = null; state = State.RESIZING; return false; }
            if (!stamp.equals(stable)) { stable = stamp; stableSince = now; state = State.SETTLING; }
            return seconds(now, stableSince) >= SETTLE;
        }
        void capture(long now) { if (state != State.SETTLING) throw new IllegalStateException("Not settled"); state = State.CAPTURING; since = now; }
        boolean captured(long now, boolean dimensionsMatch, ResourceStamp stamp) {
            if (state != State.CAPTURING) throw new IllegalStateException("Not capturing");
            if (!dimensionsMatch || !stamp.equals(stable)) throw new IllegalStateException("Dimensions/resources changed during capture");
            return seconds(now, since) >= CAPTURE;
        }
        void next() { if (++phase == ORDER.size()) state = State.RESTORING; }
        static double seconds(long now, long start) { if (now < start) throw new IllegalStateException("Clock reversed"); return (now - start) * 1e-9; }
    }

    private final Path directory;
    private final EvidenceSource evidence;
    private final WorkloadInputs inputs;
    private final JsonObject report = new JsonObject();
    private final JsonArray phases = new JsonArray(), boundaries = new JsonArray();
    private final Timeline timeline;
    private final boolean initialHideGui;
    private final List<GpuTimers.Sample> gpu = new ArrayList<>();
    private final List<CpuTimers.Sample> cpu = new ArrayList<>();
    private JsonObject initialInputs, initialArtifacts, current, captureInputs, captureTemporal;
    private WindowStateTracker windows;
    private Object world, camera;
    private String liveScene;
    private long startFrame, lastFrame;
    private long restoreStarted;
    private ResourceStamp restoreBefore;
    private boolean failed, done;

    static boolean requested() { return Boolean.getBoolean("shaderlod.dev.physicalResolution"); }

    PhysicalResolutionSession(Path directory, EvidenceSource evidence) {
        requireProperties();
        this.directory = directory.toAbsolutePath().normalize();
        this.evidence = java.util.Objects.requireNonNull(evidence);
        inputs = new WorkloadInputs(this.directory);
        var client = Minecraft.getInstance();
        initialHideGui = client.options.hideGui;
        report.addProperty("schema", 1);
        report.addProperty("session_id", UUID.randomUUID().toString());
        report.addProperty("created_at_utc", java.time.Instant.now().toString());
        report.addProperty("diagnostic_only", true);
        report.addProperty("quality_changing", true);
        report.addProperty("optimization_acceptance", false);
        report.addProperty("method", "Original native-TAA Photon; one JVM; actual physical output native/reduced/native; resized histories explicitly qualified");
        report.addProperty("limits", "Output quality changes; no pass attribution, native reconstruction benefit, phase-synchronized temporal input, repeated gain or quality acceptance");
        report.addProperty("warmup_seconds", WARMUP);
        report.addProperty("settle_seconds", SETTLE);
        report.addProperty("capture_seconds", CAPTURE);
        report.add("phase_order", json().toJsonTree(ORDER));
        var properties = new JsonObject();
        for (String name : List.of("physicalResolution", "gpuTiming", "cpuTiming", "gpuPassTiming", "gpuAttribution", "cpuLayerTiming", "gpuSampleInterval", "fixedWetness", "initialWarmup", "benchmarkSettleSeconds", "measurementSeconds", "performanceStack", "includeDh", "measureOnly"))
            properties.addProperty(name, System.getProperty("shaderlod.dev." + name, "unspecified"));
        for (String name : OBSERVERS) properties.addProperty(name, System.getProperty("shaderlod.dev." + name, "false"));
        report.add("instrumentation_properties", properties);
        report.add("phases", phases); report.add("resource_boundaries", boundaries);
        try {
            if (Files.exists(this.directory.resolve("physical-resolution.json"))) throw new IllegalStateException("Use a fresh profile; report already exists");
            client.options.hideGui = true;
            initialInputs = boundaryInputs(); validateOriginal(initialInputs);
            initialArtifacts = LoadedArtifactEvidence.capture(); LoadedArtifactEvidence.requireValid(initialArtifacts);
            report.add("initial_inputs", initialInputs); report.add("loaded_artifact_evidence_initial", initialArtifacts);
            report.add("shader_temporal_inputs_initial", inputs.temporalInputs());
            report.add("resource_initial", evidence.snapshot());
            if (!matches(NATIVE, sizes())) throw new IllegalStateException("Startup must observe exact logical1488x836/physical2976x1672 main/GLFW output");
            world = client.level; camera = client.getCameraEntity();
            timeline = new Timeline(System.nanoTime());
            write("warming", null);
        } catch (Exception failure) {
            client.options.hideGui = initialHideGui;
            throw new IllegalStateException("Physical resolution startup rejected before resize", failure);
        }
    }

    String status() { return "Physical resolution " + Math.min(timeline.phase + 1, 3) + "/3 " + timeline.state; }

    void tick() {
        var drainedGpu = GpuTimers.drainSamples(); var drainedCpu = CpuTimers.drainSamples();
        if (done) return;
        long now = System.nanoTime();
        try {
            if (timeline.state == Timeline.State.RESTORING || timeline.state == Timeline.State.FAILED) { restoreTick(now); return; }
            if (Minecraft.getInstance().level != world || Minecraft.getInstance().getCameraEntity() != camera || liveScene != null && !liveScene.equals(scene()))
                throw new IllegalStateException("World/camera/settings/weather changed between sampled ticks");
            if (timeline.state == Timeline.State.WARMING) {
                if (timeline.warmed(now)) {
                    // ComparisonClient applies the prepared control during startup. Latch after warmup.
                    var warmedInputs = boundaryInputs(); validateOriginal(warmedInputs);
                    for (String field : List.of("actual_shader_sha256", "effective_shader_options", "configuration_files", "source_fingerprint", "loaded_artifact_evidence"))
                        if (!initialInputs.get(field).equals(warmedInputs.get(field))) throw new IllegalStateException("Startup source/options/config/artifacts changed: " + field);
                    initialInputs = warmedInputs; report.add("stable_input_reference", initialInputs);
                    liveScene = scene(); requestPhase(System.nanoTime());
                }
                return;
            }
            var stamp = evidence.resourceStamp(); var size = sizes();
            boolean matched = matchesResources(ORDER.get(timeline.phase), size, stamp);
            var window = window();
            requireWindow(window);
            if (timeline.state != Timeline.State.CAPTURING) {
                windows.observeWarmup(window, elapsed(now), GpuTimers.frameId());
                if (timeline.observe(now, matched, stamp)) beginCapture();
                return;
            }
            windows.observeCapture(window, elapsed(now), GpuTimers.frameId());
            boolean finished = timeline.captured(now, matched, stamp);
            gpu.addAll(drainedGpu); cpu.addAll(drainedCpu);
            lastFrame = GpuTimers.frameId();
            if (finished) {
                finishCapture(now);
                timeline.next();
                if (timeline.state == Timeline.State.RESTORING) startRestoration(now);
                else requestPhase(System.nanoTime());
            }
        } catch (Exception failure) {
            if (timeline.state == Timeline.State.RESTORING) {
                failed = true; finishTerminal(false, failure.toString()); return;
            }
            failed = true;
            report.addProperty("failure", failure.toString());
            if (current != null) { current.addProperty("status", "failed"); current.add("gpu_samples_partial", json().toJsonTree(gpu)); current.add("cpu_samples_partial", json().toJsonTree(cpu)); }
            timeline.state = Timeline.State.FAILED;
            try { startRestoration(now); } catch (Exception restoreFailure) { finishTerminal(false, restoreFailure.toString()); }
        }
    }

    private void requestPhase(long now) throws Exception {
        JsonObject observed = boundaryInputs(); validateStable(observed);
        current = new JsonObject(); phases.add(current);
        current.addProperty("index", timeline.phase); current.addProperty("status", "resizing");
        current.add("requested_extent", json().toJsonTree(ORDER.get(timeline.phase)));
        current.add("input_state_before_resize", observed);
        current.add("resource_before_resize", evidence.snapshot());
        timeline.request(now, evidence.resourceStamp());
        evidence.armBoundary("phase-" + timeline.phase);
        resize(ORDER.get(timeline.phase));
        windows = new WindowStateTracker(); gpu.clear(); cpu.clear(); GpuTimers.resetSamples();
        write("resizing", null);
    }

    private void beginCapture() throws Exception {
        captureInputs = boundaryInputs(); validateStable(captureInputs);
        captureTemporal = inputs.temporalInputs(); WorkloadInputs.validateWetness(captureTemporal, false);
        current.add("resource_capture_start", evidence.snapshot());
        current.add("observed_size_start", json().toJsonTree(sizes()));
        current.add("input_state_start", captureInputs);
        current.add("shader_temporal_inputs_start", captureTemporal);
        ShaderInputTracker.beginMeasurement();
        GpuTimers.resetSamples(); gpu.clear(); cpu.clear(); startFrame = GpuTimers.frameId();
        timeline.capture(System.nanoTime());
        current.addProperty("capture_started_at_utc", java.time.Instant.now().toString());
        current.addProperty("status", "capturing");
        // No report writes, hashing, screenshots, encoders or readbacks inside the capture interval.
    }

    private void finishCapture(long now) throws Exception {
        current.addProperty("elapsed_seconds", Timeline.seconds(now, timeline.since));
        current.addProperty("render_frames", lastFrame - startFrame);
        current.add("gpu_samples", json().toJsonTree(gpu)); current.add("cpu_samples", json().toJsonTree(cpu));
        current.add("gpu_timing_snapshot", json().toJsonTree(GpuTimers.snapshot()));
        current.add("cpu_timing_snapshot", json().toJsonTree(CpuTimers.snapshot()));
        current.add("window_state", json().toJsonTree(windows.snapshot()));
        current.add("resource_capture_end", evidence.snapshot());
        current.add("observed_size_end", json().toJsonTree(sizes()));
        // Seal timing epoch BEFORE doing boundary I/O.
        GpuTimers.resetSamples();
        JsonObject end = boundaryInputs(); validateStable(end);
        if (!captureInputs.equals(end)) throw new IllegalStateException("Complete within-phase input state changed");
        current.add("input_state_end", end);
        var temporal = inputs.temporalInputs(); WorkloadInputs.validateWetness(temporal, true);
        current.add("shader_temporal_inputs_end", temporal);
        for (String name : List.of("day_time_source", "world_time_source", "world_day_source", "photon_world_age_source", "moon_phase_source", "rain_strength_source", "clock_policy"))
            if (!captureTemporal.get(name).equals(temporal.get(name))) throw new IllegalStateException("Fixed temporal source drift: " + name);
        for (String name : List.of("frame_counter_source", "frame_time_counter_source"))
            if (!Double.isFinite(temporal.get(name).getAsDouble()) || temporal.get(name).getAsDouble() <= captureTemporal.get(name).getAsDouble()) throw new IllegalStateException("Normal clock did not advance: " + name);
        validateTiming(gpu, cpu, lastFrame - startFrame);
        current.addProperty("status", "complete");
        boundaries.add(evidence.snapshot());
        write("between-phases", null);
    }

    private void startRestoration(long now) {
        restoreStarted = now;
        timeline.state = Timeline.State.RESTORING;
        restoreBefore = evidence.resourceStamp();
        evidence.armBoundary("restore-native"); resize(NATIVE); GpuTimers.resetSamples();
        Minecraft.getInstance().options.hideGui = initialHideGui;
        write(failed ? "failed-restoring" : "restoring", null);
    }
    private void restoreTick(long now) {
        var stamp = evidence.resourceStamp();
        if (matchesResources(NATIVE, sizes(), stamp) && stamp.completedBoundaryEpoch > restoreBefore.completedBoundaryEpoch
                && stamp.completedAllocationEpoch == stamp.allocationEpoch) {
            report.add("resource_restored", evidence.snapshot());
            report.add("observed_size_restored", json().toJsonTree(sizes()));
            try {
                var restoredInputs = boundaryInputs();
                // GUI policy is the sole restoration difference; all original controls remain fixed.
                report.add("input_state_restored", restoredInputs);
                var comparison = restoredInputs.deepCopy(); comparison.addProperty("hide_gui", true);
                validateStable(comparison);
                finishTerminal(true, null);
            } catch (Exception error) { failed = true; finishTerminal(true, error.toString()); }
        } else if (Timeline.seconds(now, restoreStarted) >= RESIZE_TIMEOUT) finishTerminal(false, "Native output restoration timed out");
    }
    private void finishTerminal(boolean restored, String failure) {
        done = true;
        Minecraft.getInstance().options.hideGui = initialHideGui;
        timeline.state = failed || !restored ? Timeline.State.FAILED : Timeline.State.COMPLETE;
        report.addProperty("native_dimensions_restored", restored);
        report.addProperty("gui_restored", Minecraft.getInstance().options.hideGui == initialHideGui);
        report.addProperty("main_mod_flag_modified", false);
        write(timeline.state == Timeline.State.COMPLETE ? "complete" : "failed", failure);
        if (Boolean.getBoolean("shaderlod.dev.measureOnly")) Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }

    private JsonObject boundaryInputs() throws Exception {
        JsonObject state = inputs.benchmarkInputState();
        var client = Minecraft.getInstance();
        state.addProperty("fov", client.options.fov().get()); state.addProperty("camera_type", client.options.getCameraType().name());
        state.addProperty("dh_present", ModList.get().isLoaded("distanthorizons"));
        state.add("effect_controls", json().toJsonTree(com.pp.shaderlod.ShaderLodEffectConfig.snapshot()));
        state.add("configuration_files", ConfigurationFilesEvidence.capture(directory));
        var fingerprint = PackSourceReader.read(directory.resolve("shaderpacks").resolve(Iris.getCurrentPackName()));
        state.add("source_fingerprint", json().toJsonTree(fingerprint));
        var artifacts = LoadedArtifactEvidence.capture(); LoadedArtifactEvidence.requireValid(artifacts);
        state.add("loaded_artifact_evidence", artifacts);
        return state;
    }
    static void validateOriginal(JsonObject state) {
        if (!state.get("actual_shader_sha256").getAsString().equals(ORIGINAL)) throw new IllegalStateException("Pinned original Photon archive required");
        if (!state.get("mod_present").getAsBoolean() || !state.get("shaders_in_use").getAsBoolean() || state.get("fallback").getAsBoolean()
                || !state.get("hide_gui").getAsBoolean()) throw new IllegalStateException("Original/main OFF hidden-HUD Iris runtime required");
        var options = state.getAsJsonObject("effective_shader_options");
        if (options.size() != 366 || !options.has("TAAU") || !options.get("TAAU").getAsString().equals("false")
                || !options.has("TAA") || !options.get("TAA").getAsString().equals("true")) throw new IllegalStateException("Complete366options/native TAAU=false required");
        for (String flag : List.of("terrain_configured", "terrain_confirmation", "terrain_active", "shadow_culling_configured", "shadow_culling_active",
                "shadow_lists_configured", "shadow_lists_active", "shadow_coherence_configured", "shadow_coherence_active"))
            if (state.get(flag).getAsBoolean()) throw new IllegalStateException("Unrelated main feature must remain OFF: " + flag);
        for (String flag : List.of("enabled", "debug")) {
            if (state.getAsJsonObject("lod_settings").get(flag).getAsBoolean()
                    || state.getAsJsonObject("loaded_semantic_config").getAsJsonObject("lod").get(flag).getAsBoolean())
                throw new IllegalStateException("Loaded main LOD/debug must stay OFF");
        }
        if (!state.getAsJsonObject("configuration_files").get("complete").getAsBoolean()) throw new IllegalStateException("Incomplete complete configuration evidence");
    }
    static JsonObject fixedInputs(JsonObject observed) {
        JsonObject fixed = observed.deepCopy();
        // Only the explicitly authorized output extent varies. Nothing else is removed.
        fixed.remove("framebuffer_width"); fixed.remove("framebuffer_height");
        return fixed;
    }
    private void validateStable(JsonObject observed) {
        validateOriginal(observed);
        if (!fixedInputs(initialInputs).equals(fixedInputs(observed))) throw new IllegalStateException("Complete original source/options/config/artifacts/world/camera input drift");
    }
    static void validateTiming(List<GpuTimers.Sample> gpu, List<CpuTimers.Sample> cpu, long frames) {
        boolean requested = Boolean.parseBoolean(System.getProperty("shaderlod.dev.gpuTiming", "true"));
        if (frames < 100) throw new IllegalStateException("Insufficient rendered capture frames");
        if (requested && gpu.size() < 60 || !requested && !gpu.isEmpty()) throw new IllegalStateException("GPU sampling coverage/policy failed");
        for (var sample : gpu) if (!GpuTimers.FRAME.equals(sample.scope()) || !Double.isFinite(sample.milliseconds()) || sample.milliseconds() <= 0)
            throw new IllegalStateException("Non-full-frame or invalid GPU sample");
        var coarse = List.of("run-tick", "render-and-blit", "swap-and-events", "frame-limiter", "game-render");
        if (cpu.stream().noneMatch(row -> row.scope().equals("run-tick"))
                || cpu.stream().anyMatch(row -> !coarse.contains(row.scope()) || !Double.isFinite(row.wallMilliseconds()) || row.wallMilliseconds() <= 0
                        || row.threadMilliseconds() != null && (!Double.isFinite(row.threadMilliseconds()) || row.threadMilliseconds() < 0))
                || cpu.stream().map(CpuTimers.Sample::epoch).distinct().count() != 1
                || requested && gpu.stream().map(GpuTimers.Sample::epoch).distinct().count() != 1)
            throw new IllegalStateException("Finite coarse CPU and full-frame GPU single-epoch samples required");
    }
    static void requireProperties() {
        for (String session : List.of("benchmark", "measureWorkload", "cameraQualityOnly"))
            if (Boolean.getBoolean("shaderlod.dev." + session)) throw new IllegalStateException("Mutually exclusive physical resolution session: " + session);
        if (!System.getProperty("shaderlod.dev.cameraQuality", "").isBlank()) throw new IllegalStateException("Camera quality conflicts");
        for (String observer : OBSERVERS) if (Boolean.getBoolean("shaderlod.dev." + observer)) throw new IllegalStateException("Observer must be OFF: " + observer);
        for (String property : List.of("gpuPassTiming", "gpuAttribution", "cpuLayerTiming"))
            if (!System.getProperty("shaderlod.dev." + property, "true").equals("false")) throw new IllegalStateException("Explicit coarse/full-frame mode required: " + property);
        if (!System.getProperty("shaderlod.dev.gpuSampleInterval", "").equals("5") || !System.getProperty("shaderlod.dev.cpuTiming", "true").equals("true"))
            throw new IllegalStateException("N5 and coarseCPU required");
        for (var entry : java.util.Map.of("initialWarmup", "30", "benchmarkSettleSeconds", "8", "measurementSeconds", "20").entrySet())
            if (!System.getProperty("shaderlod.dev." + entry.getKey(), entry.getValue()).equals(entry.getValue())) throw new IllegalStateException("Fixed physical recipe differs: " + entry.getKey());
    }

    private String scene() {
        var c = Minecraft.getInstance(); var e = c.getCameraEntity(); var w = c.getWindow();
        return List.of(e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), c.options.fov().get(), c.options.getCameraType(),
                c.level.dimension(), c.level.getDayTime(), c.level.getRainLevel(1), c.level.getThunderLevel(1), c.options.renderDistance().get(),
                c.options.getEffectiveRenderDistance(), c.options.framerateLimit().get(), c.options.enableVsync().get(), c.options.pauseOnLostFocus,
                w.isFullscreen(), ShaderLodConfig.ENABLED.get()).toString();
    }
    private static void resize(Extent extent) {
        var w = Minecraft.getInstance().getWindow();
        w.setWindowed(extent.logicalWidth, extent.logicalHeight);
        GLFW.glfwSetWindowSize(w.getWindow(), extent.logicalWidth, extent.logicalHeight);
        // Normal GLFW event/callback -> Minecraft resizeDisplay -> Iris resource lifecycle, never direct target mutation/reload.
    }
    static boolean matchesResources(Extent extent, ObservedSize size, ResourceStamp stamp) {
        return matches(extent, size) && stamp.irisWidth == extent.physicalWidth && stamp.irisHeight == extent.physicalHeight;
    }
    private static ObservedSize sizes() {
        var c = Minecraft.getInstance(); var w = c.getWindow(); var main = c.getMainRenderTarget();
        try (var stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1); var height = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(w.getWindow(), width, height); int logicalWidth = width.get(0), logicalHeight = height.get(0);
            GLFW.glfwGetFramebufferSize(w.getWindow(), width, height);
            return new ObservedSize(logicalWidth, logicalHeight, width.get(0), height.get(0), w.getWidth(), w.getHeight(), main.width, main.height, main.viewWidth, main.viewHeight);
        }
    }
    private static WindowStateTracker.State window() {
        var c = Minecraft.getInstance(); var w = c.getWindow();
        return WindowStateTracker.readWindow(w.getWindow(), w.getScreenWidth(), w.getScreenHeight(), w.getWidth(), w.getHeight(),
                new WindowStateTracker.Pacing(c.options.pauseOnLostFocus, w.isFullscreen(), c.options.framerateLimit().get(), c.options.enableVsync().get()));
    }
    private static void requireWindow(WindowStateTracker.State state) {
        if (!state.availability().equals("available") || !state.focused().equals("true") || !state.visible().equals("true") || !state.iconified().equals("false")
                || state.pacing().fullscreen() || state.pacing().vsync() || state.pacing().configured_framerate_limit() != 260 || state.pacing().pause_on_lost_focus())
            throw new IllegalStateException("Matched focused window/pacing contract failed; actual=" + json().toJson(state));
    }
    private double elapsed(long now) { return Timeline.seconds(now, timeline.since); }
    private static com.google.gson.Gson json() { return new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create(); }
    private void write(String status, String failure) {
        report.addProperty("status", status); report.addProperty("written_at_utc", java.time.Instant.now().toString());
        if (failure != null) report.addProperty("restoration_failure", failure);
        try { Files.writeString(directory.resolve("physical-resolution.json"), json().toJson(report)); }
        catch (Exception error) { throw new IllegalStateException("Cannot persist physical resolution diagnostic", error); }
    }
}
