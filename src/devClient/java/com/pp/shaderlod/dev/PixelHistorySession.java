package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/** Diagnostic only. Exact rendered-frame linkage and lossless bounded PNGs; no speed evidence. */
public final class PixelHistorySession {
    public static final String PROPERTY = "shaderlod.dev.pixelHistoryCapture";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<Program, Compiled> PROGRAMS = new IdentityHashMap<>();
    static final String[] SCALE_UNIFORMS = {"shaderlodScaleHistoryVersion", "shaderlodPreviousEnabled", "shaderlodScaleTransition"};
    private static final String[] UNIFORMS = {"shaderlodBridgeVersion", "shaderlodEnabled", "shaderlodTemporalVersion", "shaderlodHistoryReset"};
    private static Context context;
    private static PixelHistorySession active;
    private final Path gameDirectory, output;
    private final WorkloadInputs inputs;
    private final PixelHistoryTimeline timeline = new PixelHistoryTimeline();
    private final JsonObject report = new JsonObject();
    private final JsonArray frames = new JsonArray();
    private final Map<String, Draw> draws = new LinkedHashMap<>();
    private final boolean originalEnabled, originalGui;
    private final Object world, camera, pipeline;
    private final JsonObject initial;
    private final String expectedArchive, expectedOptions;
    private final boolean cloudRemap, taaPreserve;
    private final ControlIdentity controlIdentity;
    private long warmedAt;
    private int drawCounter = -1, lastComplete = -1;
    private boolean armed, finished;
    private String fixedPose;
    private long fixedDayTime;
    private float fixedRain, fixedThunder;
    private int fixedWidth, fixedHeight;

    public static boolean requested() { return Boolean.getBoolean(PROPERTY); }
    public static PixelHistorySession createIfRequested(Minecraft client) {
        if (!requested()) return null;
        if (client.level == null || client.player == null || !client.player.isSpectator() || client.screen != null || client.isPaused()
                || Iris.getPipelineManager().getPipelineNullable() == null) return null;
        try {
            if (active != null) throw new IllegalStateException("HISTORY_SESSION_ALREADY_EXISTS");
            requireProperties();
            if (ShaderLodConfig.ENABLED.get()) throw new IllegalStateException("HISTORY_REQUIRES_INITIAL_GLOBAL_OFF");
            active = new PixelHistorySession(client);
            return active;
        } catch (Exception failure) { throw new IllegalStateException("Cannot start pixel history capture", failure); }
    }
    static void requireProperties() {
        if (!requested()) return;
        for (String flag : List.of("gpuTiming", "cpuTiming"))
            if (!"false".equals(System.getProperty("shaderlod.dev." + flag))) throw new IllegalStateException("HISTORY_REQUIRES_EXPLICIT_FALSE_" + flag);
        for (String flag : List.of("gpuTiming", "cpuTiming", "benchmark", "measureWorkload", "workTrace", "spatialLayoutProbe", "pixelCoverage", "cloudSamplerProbe", "shadowObserve", "shadowCoherenceOracle", "dhDrawEvidence",
                "cpuLayerTiming", "gpuPassTiming", "gpuAttribution", "workTraceOnly", "pixelCoverageOnly", "spatialLayoutOnly", "benchmarkOnly", "cameraQualityOnly"))
            if (Boolean.getBoolean("shaderlod.dev." + flag)) throw new IllegalStateException("HISTORY_FORBIDS_" + flag);
        if (!System.getProperty("shaderlod.dev.cameraQuality", "").isBlank()) throw new IllegalStateException("HISTORY_FORBIDS_CAMERA_SESSION");
    }
    private PixelHistorySession(Minecraft client) throws Exception {
        Path game = client.gameDirectory.toPath().toRealPath();
        gameDirectory = game;
        controlIdentity = readControlIdentity(game.getParent().resolve("control.json"));
        Path root = game.resolve("pixel-history");
        if (Files.isSymbolicLink(root)) throw new IllegalStateException("SYMLINK_OUTPUT_ROOT");
        Files.createDirectories(root);
        if (!root.toRealPath().getParent().equals(game)) throw new IllegalStateException("OUTPUT_ESCAPED_PROFILE");
        output = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
        inputs = new WorkloadInputs(game);
        expectedArchive = System.getProperty("shaderlod.dev.pixelHistoryArchiveSha256", "");
        expectedOptions = System.getProperty("shaderlod.dev.pixelHistoryOptionsSha256", "");
        if (!expectedArchive.matches("[0-9a-f]{64}")) throw new IllegalStateException("EXPECTED_ARCHIVE_HASH_REQUIRED");
        originalEnabled = ShaderLodConfig.ENABLED.get(); originalGui = client.options.hideGui;
        world = client.level; camera = client.getCameraEntity(); pipeline = Iris.getPipelineManager().getPipelineNullable();
        initial = inputs.benchmarkInputState();
        initial.add("configuration_files", ConfigurationFilesEvidence.capture(game));
        requireSource(initial);
        cloudRemap = optionEnabled(initial.getAsJsonObject("effective_shader_options"), "SHADERLOD_CLOUD_HISTORY_REMAP");
        taaPreserve = optionEnabled(initial.getAsJsonObject("effective_shader_options"), "SHADERLOD_TAA_SCALE_PRESERVE");
        report.addProperty("schema", 1);
        report.addProperty("created_at_utc", Instant.now().toString());
        report.addProperty("evidence_kind", "exact-render-frame history linkage and quality captures; readback/encoding stalls invalidate timing use");
        report.addProperty("capture_seam", "Minecraft.runTick before Window.updateDisplay, after GameRenderer.render and mainRenderTarget.blitToScreen");
        report.addProperty("switch_seam", "same pre-swap seam after current image readback; next Iris notifier observes config change");
        report.addProperty("settled_policy", "+60 consecutive Iris render frames, not elapsed-time or converged-history assertion");
        report.addProperty("archive_sha256", expectedArchive);
        report.addProperty("expected_options_sha256", expectedOptions);
        report.addProperty("options_pin_policy", expectedOptions.isBlank() ? "legacy exact368" : "complete actual Iris map canonical SHA256");
        report.addProperty("cloud_history_remap_option", cloudRemap);
        report.addProperty("taa_scale_preserve_option", taaPreserve);
        report.add("control_file_initial", JSON.toJsonTree(controlIdentity));
        report.addProperty("iris_jar_sha256", "3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83");
        report.add("input_state_initial", initial);
        report.add("loaded_artifact_evidence_initial", LoadedArtifactEvidence.capture());
        report.add("frames", frames);
        report.addProperty("png_encoding", FastPngWriter.ENCODING);
        report.addProperty("native_image_ownership", "one synchronous try-with-resources image at a time; max4096x2160/max64MiB PNG; 11 captures");
        var classEvidence = new JsonObject();
        for (Class<?> type : List.of(PixelHistorySession.class, PixelHistoryTimeline.class, FastPngWriter.class,
                ConfigurationFilesEvidence.class, LoadedArtifactEvidence.class,
                com.pp.shaderlod.dev.mixin.DevEvidenceMixinPlugin.class, ComparisonClient.class)) {
            var evidence = new JsonObject();
            var resource = type.getResource(type.getSimpleName() + ".class");
            evidence.addProperty("resource_url", resource == null ? null : resource.toExternalForm());
            try { var code = type.getProtectionDomain().getCodeSource(); evidence.addProperty("code_source_location", code == null ? null : code.getLocation().toExternalForm()); }
            catch (RuntimeException unavailable) { evidence.addProperty("code_source_error", unavailable.toString()); }
            try (var stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
                if (stream != null) evidence.addProperty("sha256", hash(stream.readAllBytes()));
                else evidence.addProperty("error", "class resource unavailable");
            }
            classEvidence.add(type.getName(), evidence);
        }
        // Never load a Mixin class directly: observe its resource through the actual harness loader.
        for (String name : List.of("PixelHistoryFrameMixin", "SpatialCompositeMixin", "SpatialQuadMixin", "SpatialPassAccessor")) {
            String qualified = "com.pp.shaderlod.dev.mixin." + name;
            var evidence = new JsonObject();
            var resource = PixelHistorySession.class.getClassLoader().getResource(qualified.replace('.', '/') + ".class");
            evidence.addProperty("resource_url", resource == null ? null : resource.toExternalForm());
            evidence.addProperty("scope", "actual harness-classloader Mixin resource; no direct Mixin class load or VM-transformation proof");
            if (resource != null) try (var stream = resource.openStream()) { evidence.addProperty("sha256", hash(stream.readAllBytes())); }
            else evidence.addProperty("error", "Mixin resource unavailable");
            classEvidence.add(qualified, evidence);
        }
        report.add("class_resource_evidence", classEvidence);
        report.addProperty("class_resource_scope", "actual runtime resource bytes/location; not transformed VM bytecode");
        write("waiting_for_fixed_scene");
        try { requireCompleteBoundaries(initial, report.getAsJsonObject("loaded_artifact_evidence_initial")); }
        catch (RuntimeException failure) { report.addProperty("failure", failure.toString()); write("rejected_initial_boundary"); throw failure; }
    }
    /** Client ticks only arm warmup; transition position is never derived from ticks. */
    public void tick(Minecraft client) {
        if (finished) return;
        try {
            if (client.level == null || client.player == null || !client.player.isSpectator()
                    || client.screen != null || client.isPaused()) { warmedAt = 0; return; }
            if (warmedAt == 0) { warmedAt = System.nanoTime(); client.options.hideGui = true; write("warming"); }
            requireOwner(client);
            if (!armed && (System.nanoTime() - warmedAt) * 1e-9 >= 60) { armed = true; write("capturing"); }
        } catch (Exception failure) { fail(client, failure); }
    }
    public static void compiledProgram(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
                                       ProgramSource source, Program program, Map<?, String> transformed) {
        if (!requested() || source == null || program == null) return;
        String stage = stage(group, source.getName());
        if (stage == null) return;
        var hashes = new TreeMap<String, String>();
        transformed.forEach((key, value) -> { if (value != null) hashes.put(key.toString(), hash(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); });
        var raw = new JsonObject();
        raw.addProperty("stage", stage); raw.addProperty("source_name", source.getName());
        raw.addProperty("vertex_sha256", hash(source.getVertexSource().orElseThrow().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        raw.addProperty("fragment_sha256", hash(source.getFragmentSource().orElseThrow().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        raw.add("transformed_sha256", JSON.toJsonTree(hashes));
        raw.addProperty("consumer_marker_in_fragment", transformed.entrySet().stream().anyMatch(e -> "FRAGMENT".equals(e.getKey().toString()) && e.getValue().contains("shaderlod_live_pixel_history_reset")));
        PROGRAMS.put(program, new Compiled(renderer, pipeline, source.getName(), stage, program, source.getDirectives().getDrawBuffers().clone(), raw));
    }
    private static String stage(CompositePass group, String name) {
        if (group == CompositePass.DEFERRED && name.equals("deferred2")) return "deferred2";
        if (group == CompositePass.COMPOSITE && name.equals("composite4")) return "composite4";
        if (group == CompositePass.DEFERRED && name.equals("deferred4")) return "sampler:deferred4";
        if (group == CompositePass.COMPOSITE && name.equals("composite1")) return "sampler:composite1";
        return null;
    }
    public static void enterRenderer(Object renderer, WorldRenderingPipeline pipeline, CompositePass group, List<?> passes) {
        if (!requested()) return;
        context = null;
        if (active == null || !active.armed || active.finished) return;
        try {
            var selected = new IdentityHashMap<Program, Compiled>();
            for (Object object : passes) {
                var pass = (SpatialPassAccessor)object;
                var compiled = PROGRAMS.get(pass.shaderlod$program());
                if (compiled == null) continue;
                if (compiled.renderer != renderer || compiled.pipeline != pipeline
                        || !compiled.name.equals(pass.shaderlod$name()) || !Arrays.equals(compiled.drawBuffers, pass.shaderlod$drawBuffers()))
                    throw new IllegalStateException("ACTUAL_PASS_SOURCE_IDENTITY_MISMATCH");
                selected.put(compiled.program, compiled);
            }
            context = new Context(pipeline, selected);
        } catch (Exception failure) { active.fail(Minecraft.getInstance(), failure); }
    }
    public static void leaveRenderer() { if (requested()) context = null; }
    /** Quad HEAD follows pinned Program.use and CustomUniforms.push; no uniform supplier re-evaluation. */
    public static void beforeQuadDraw() {
        if (!requested() || active == null || !active.armed || active.finished || context == null) return;
        try {
            int programId = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            Compiled selected = null;
            for (var compiled : context.programs.values()) if (compiled.program.getProgramId() == programId) {
                if (selected != null) throw new IllegalStateException("AMBIGUOUS_REAL_PROGRAM_ID");
                selected = compiled;
            }
            if (selected == null) return;
            if (selected.pipeline != active.pipeline) throw new IllegalStateException("PIPELINE_CHANGED_AT_CONSUMER");
            if (selected.stage.startsWith("sampler:")) {
                String name = selected.stage.substring("sampler:".length());
                if (!active.report.has("cloud_sampling_sampler_states")) active.report.add("cloud_sampling_sampler_states", new JsonObject());
                var states = active.report.getAsJsonObject("cloud_sampling_sampler_states");
                if (!states.has(name)) {
                    var state = selected.source.deepCopy();
                    state.addProperty("program_id", programId);
                    state.addProperty("iris_frame_counter", SystemTimeUniforms.COUNTER.getAsInt());
                    state.addProperty("actual_pass_program_identity_verified", true);
                    state.add("sampler_state", com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.historySamplerState());
                    states.add(name, state);
                }
                return;
            }
            active.observe(selected, programId);
        } catch (Exception failure) { active.fail(Minecraft.getInstance(), failure); }
    }
    private void observe(Compiled selected, int programId) {
        if (selected.pipeline != pipeline || !selected.source.get("consumer_marker_in_fragment").getAsBoolean()) throw new IllegalStateException("PIPELINE_OR_HISTORY_SOURCE_CHANGED");
        int counter = SystemTimeUniforms.COUNTER.getAsInt();
        if (drawCounter != counter) {
            if (!draws.isEmpty()) throw new IllegalStateException("MISSING_POST_RENDER_COMPLETION");
            drawCounter = counter;
        }
        if (draws.containsKey(selected.stage)) throw new IllegalStateException("DUPLICATE_CONSUMER_DRAW");
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) throw new IllegalStateException("PROGRAM_NOT_LINKED");
        Readback readback = readUniforms(programId);
        Map<String, Integer> values = readback.core;
        var raw = selected.source.deepCopy();
        raw.addProperty("program_id", programId);
        raw.addProperty("iris_frame_counter", counter);
        raw.addProperty("actual_pass_program_identity_verified", true);
        if (selected.stage.equals("deferred2") && !report.has("cloud_history_sampler_state")) {
            report.add("cloud_history_sampler_state", com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.historySamplerState());
            report.addProperty("cloud_history_sampler_frame", counter);
        }
        raw.add("linked_integer_uniforms", JSON.toJsonTree(values));
        raw.add("core_uniform_readback", observationsJson(readback.coreObservations));
        var extension = new JsonObject(); extension.addProperty("schema", 1);
        extension.addProperty("required_consumer", extensionRequired(selected.stage, cloudRemap, taaPreserve));
        extension.add("uniforms", observationsJson(readback.scale));
        raw.add("scale_history_uniforms", extension);
        // Retain actual unknown/wrong-type records before rejecting the original required inputs.
        draws.put(selected.stage, new Draw(null, readback.scale, raw));
        if (values.size() != UNIFORMS.length) throw new IllegalStateException("CORE_UNIFORM_READBACK_UNKNOWN");
        draws.put(selected.stage, new Draw(new PixelHistoryTimeline.Values(values.get(UNIFORMS[0]), values.get(UNIFORMS[1]), values.get(UNIFORMS[2]), values.get(UNIFORMS[3])), readback.scale, raw));
    }
    private static Readback readUniforms(int program) {
        int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        int length = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
        if (count < 0 || count > 4096 || length < 0 || length > 4096 || (count > 0 && length < 1)) throw new IllegalStateException("UNBOUNDED_UNIFORM_INVENTORY");
        var inventory = new LinkedHashMap<String, UniformShape>();
        try (var stack = MemoryStack.stackPush()) {
            var size = stack.mallocInt(1); var type = stack.mallocInt(1);
            for (int i = 0; i < count; i++) {
                String name = GL20.glGetActiveUniform(program, i, length, size, type);
                if (inventory.put(name, new UniformShape(type.get(0), size.get(0))) != null)
                    throw new IllegalStateException("DUPLICATE_ACTIVE_UNIFORM_" + name);
            }
        }
        UniformReader reader = new UniformReader() {
            public int location(String name) { return GL20.glGetUniformLocation(program, name); }
            public int integer(int location) { return GL20.glGetUniformi(program, location); }
        };
        var core = new LinkedHashMap<String, Integer>();
        var coreObservations = new LinkedHashMap<String, UniformObservation>();
        var scale = new LinkedHashMap<String, UniformObservation>();
        for (String name : UNIFORMS) {
            var observation = observeUniform(name, inventory, reader); coreObservations.put(name, observation);
            if (observation.status.equals("read")) core.put(name, observation.integerValue);
        }
        for (String name : SCALE_UNIFORMS) scale.put(name, observeUniform(name, inventory, reader));
        return new Readback(core, coreObservations, scale);
    }
    private static JsonObject observationsJson(Map<String, UniformObservation> observations) {
        var result = new JsonObject();
        observations.forEach((name, observed) -> {
            var field = new JsonObject();
            field.addProperty("active_name", observed.activeName); field.addProperty("type", observed.type);
            field.addProperty("size", observed.size); field.addProperty("location", observed.location);
            field.addProperty("integer_value", observed.integerValue); field.addProperty("read_status", observed.status);
            field.addProperty("error", observed.error); result.add(name, field);
        });
        return result;
    }
    interface UniformReader { int location(String name); int integer(int location); }
    record UniformShape(int type, int size) {}
    record UniformObservation(String activeName, Integer type, Integer size, Integer location,
                              Integer integerValue, String status, String error) {}
    static UniformObservation observeUniform(String name, Map<String, UniformShape> inventory, UniformReader reader) {
        String activeName = inventory.containsKey(name) ? name : inventory.containsKey(name + "[0]") ? name + "[0]" : null;
        UniformShape shape = activeName == null ? null : inventory.get(activeName);
        Integer location = null;
        try {
            location = reader.location(name);
            if (shape == null) return new UniformObservation(null, null, null, location, null, "missing_or_inactive", null);
            if (!activeName.equals(name) || shape.type != GL11.GL_INT || shape.size != 1)
                return new UniformObservation(activeName, shape.type, shape.size, location, null, "wrong_type_or_size", null);
            if (location < 0) return new UniformObservation(activeName, shape.type, shape.size, location, null, "location_unknown", null);
            return new UniformObservation(activeName, shape.type, shape.size, location, reader.integer(location), "read", null);
        } catch (RuntimeException failure) {
            return new UniformObservation(activeName, shape == null ? null : shape.type, shape == null ? null : shape.size,
                    location, null, "read_failed", failure.toString());
        }
    }
    static boolean extensionRequired(String stage, boolean cloudRemap, boolean taaPreserve) {
        return cloudRemap && (stage.equals("deferred2") || stage.equals("composite4") && taaPreserve);
    }
    record ScaleValues(int version, int previousEnabled, int transition) {}
    static ScaleValues validateScaleHistory(Map<String, UniformObservation> actual, boolean required,
                                           int global, int phase, int offset) {
        if (!required) return null; // Unconsumed optimized-out fields remain explicitly unknown evidence.
        int[] values = new int[3];
        for (int i = 0; i < SCALE_UNIFORMS.length; i++) {
            var field = actual.get(SCALE_UNIFORMS[i]);
            if (field == null || !field.status.equals("read") || field.integerValue == null
                    || field.type == null || field.type != GL11.GL_INT || field.size == null || field.size != 1
                    || field.location == null || field.location < 0 || !SCALE_UNIFORMS[i].equals(field.activeName))
                throw new IllegalStateException("SCALE_HISTORY_UNIFORM_UNKNOWN_" + SCALE_UNIFORMS[i]);
            values[i] = field.integerValue;
        }
        if (values[0] != 1 || (values[1] != 0 && values[1] != 1) || (values[2] != 0 && values[2] != 1))
            throw new IllegalStateException("SCALE_HISTORY_INVALID_VERSION_OR_INTEGER");
        boolean firstToggle = phase != 0 && offset == 0;
        if (values[1] != (firstToggle ? 1 - global : global) || values[2] != (firstToggle ? 1 : 0))
            throw new IllegalStateException("SCALE_HISTORY_TRANSITION_MISMATCH");
        return new ScaleValues(values[0], values[1], values[2]);
    }
    private record Readback(Map<String, Integer> core, Map<String, UniformObservation> coreObservations,
                            Map<String, UniformObservation> scale) {}
    public static void afterFrame(Minecraft client) {
        if (requested() && active != null && active.armed && !active.finished) active.completeFrame(client);
    }
    private void completeFrame(Minecraft client) {
        try {
            requireOwner(client);
            requireFixedScene(client);
            int counter = SystemTimeUniforms.COUNTER.getAsInt();
            if (counter == lastComplete) throw new IllegalStateException("DUPLICATE_POST_RENDER_FRAME");
            if (counter != drawCounter || draws.size() != 2 || !draws.containsKey("deferred2") || !draws.containsKey("composite4"))
                throw new IllegalStateException("MISSING_SAME_FRAME_CONSUMER_DRAW");
            var frame = new JsonObject();
            frame.addProperty("iris_frame_counter", counter);
            frame.addProperty("frame_time_counter", SystemTimeUniforms.TIMER.getFrameTimeCounter());
            frame.addProperty("config_global_before_switch", ShaderLodConfig.ENABLED.get());
            frame.add("deferred2", draws.get("deferred2").raw); frame.add("composite4", draws.get("composite4").raw);
            frame.add("temporal_inputs", inputs.temporalInputs());
            frame.addProperty("hide_gui", client.options.hideGui);
            frame.addProperty("window_focused", focused(client));
            frames.add(frame); // Retain the failed frame as evidence too.
            PixelHistoryTimeline.Action action = timeline.frame(counter, draws.get("deferred2").values, draws.get("composite4").values);
            frame.addProperty("phase", action.phase()); frame.addProperty("offset", action.offset());
            ScaleValues d2 = validateScaleHistory(draws.get("deferred2").scale, extensionRequired("deferred2", cloudRemap, taaPreserve),
                    draws.get("deferred2").values.global(), action.phase(), action.offset());
            ScaleValues c4 = validateScaleHistory(draws.get("composite4").scale, extensionRequired("composite4", cloudRemap, taaPreserve),
                    draws.get("composite4").values.global(), action.phase(), action.offset());
            if (d2 != null && c4 != null && !d2.equals(c4)) throw new IllegalStateException("SCALE_HISTORY_CONSUMERS_DISAGREE");
            frame.addProperty("scale_history_required_inputs_validated", d2 != null || c4 != null);
            if (action.capture() != null) capture(client, frame, action.capture());
            draws.clear(); lastComplete = counter;
            if (action.switchTo() != null) {
                ShaderLodConfig.ENABLED.set(action.switchTo());
                frame.addProperty("config_global_after_capture", action.switchTo());
                frame.addProperty("switch_applied_after_capture", true);
            }
            if (action.done()) finish(client, "complete_exact_frame_capture");
            else if (action.capture() != null) write("capturing");
        } catch (Exception failure) { fail(client, failure); }
    }
    private void capture(Minecraft client, JsonObject frame, String label) throws Exception {
        var target = client.getMainRenderTarget();
        if ((long)target.width * target.height > FastPngWriter.MAX_PIXELS) throw new IllegalStateException("FRAMEBUFFER_EXCEEDS_BOUND");
        Path png = output.resolve(label + ".png");
        frame.addProperty("png", png.getFileName().toString()); frame.addProperty("readback_status", "pending");
        int existingError = GL11.glGetError(); frame.addProperty("gl_error_before_readback", existingError);
        if (existingError != GL11.GL_NO_ERROR) throw new IllegalStateException("PREEXISTING_GL_ERROR_" + existingError);
        int[] before = readbackState();
        frame.add("gl_readback_state_before", JSON.toJsonTree(before));
        try (var image = new com.mojang.blaze3d.platform.NativeImage(target.width, target.height, false)) {
            // Same RGBA/opaque-alpha/flipY path as pinned Screenshot.takeScreenshot, with owned failure cleanup.
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0);
            for (int setting : PACK_FIELDS) com.mojang.blaze3d.platform.GlStateManager._pixelStore(setting, setting == GL11.GL_PACK_ALIGNMENT ? 4 : 0);
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, true);
            int downloadError = GL11.glGetError(); frame.addProperty("gl_error_after_download", downloadError);
            if (downloadError != GL11.GL_NO_ERROR) throw new IllegalStateException("DOWNLOAD_GL_ERROR_" + downloadError);
            image.flipY();
            if (image.format() != com.mojang.blaze3d.platform.NativeImage.Format.RGBA) throw new IllegalStateException("UNSUPPORTED_SCREENSHOT_FORMAT");
            boolean little = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;
            FastPngWriter.write(png, target.width, target.height, (x, y) -> { int pixel = image.getPixelRGBA(x, y); return little ? pixel : Integer.reverseBytes(pixel); }, 0, 64L * 1024 * 1024);
        } finally {
            for (int i = 0; i < PACK_FIELDS.length; i++) com.mojang.blaze3d.platform.GlStateManager._pixelStore(PACK_FIELDS[i], before[i]);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, before[PACK_FIELDS.length]);
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(before[PACK_FIELDS.length + 2]);
            int[] after = readbackState(); frame.add("gl_readback_state_after", JSON.toJsonTree(after));
            frame.addProperty("gl_readback_state_restored", Arrays.equals(before, after));
            if (!Arrays.equals(before, after)) throw new IllegalStateException("READBACK_GL_STATE_NOT_RESTORED");
        }
        frame.addProperty("png", png.getFileName().toString()); frame.addProperty("png_sha256", hash(Files.readAllBytes(png)));
        frame.addProperty("readback_status", "complete");
        frame.addProperty("width", target.width); frame.addProperty("height", target.height);
        var cam = client.gameRenderer.getMainCamera(); var pose = new JsonObject();
        pose.addProperty("x", cam.getPosition().x); pose.addProperty("y", cam.getPosition().y); pose.addProperty("z", cam.getPosition().z);
        pose.addProperty("yaw", cam.getYRot()); pose.addProperty("pitch", cam.getXRot()); frame.add("actual_camera", pose);
    }
    private static final int[] PACK_FIELDS = { GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS,
            GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST };
    private static int[] readbackState() {
        int[] state = new int[PACK_FIELDS.length + 3];
        for (int i = 0; i < PACK_FIELDS.length; i++) state[i] = GL11.glGetInteger(PACK_FIELDS[i]);
        state[PACK_FIELDS.length] = GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        state[PACK_FIELDS.length + 1] = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        state[PACK_FIELDS.length + 2] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        return state;
    }
    private static boolean focused(Minecraft client) {
        long window = client.getWindow().getWindow();
        return window != 0 && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_FOCUSED) == org.lwjgl.glfw.GLFW.GLFW_TRUE
                && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_VISIBLE) == org.lwjgl.glfw.GLFW.GLFW_TRUE
                && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_ICONIFIED) == org.lwjgl.glfw.GLFW.GLFW_FALSE;
    }
    private void requireOwner(Minecraft client) throws Exception {
        if (!controlIdentity.equals(readControlIdentity(gameDirectory.getParent().resolve("control.json")))) throw new IllegalStateException("CONTROL_FILE_CHANGED");
        if (client.level != world || client.getCameraEntity() != camera || Iris.getPipelineManager().getPipelineNullable() != pipeline
                || client.screen != null || client.isPaused() || !focused(client) || !client.options.hideGui)
            throw new IllegalStateException("WORLD_CAMERA_PIPELINE_WINDOW_GUI_CHANGED");
        if (Iris.isFallback()) throw new IllegalStateException("IRIS_FALLBACK");
    }
    private void requireFixedScene(Minecraft client) throws Exception {
        var cam = client.gameRenderer.getMainCamera();
        var position = cam.getPosition();
        String pose = position.x + "," + position.y + "," + position.z + "," + cam.getYRot() + "," + cam.getXRot();
        float partial = net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.getTickDelta();
        float rain = client.level.getRainLevel(partial), thunder = client.level.getThunderLevel(partial);
        var target = client.getMainRenderTarget();
        if (!Double.isFinite(position.x + position.y + position.z) || !Float.isFinite(cam.getYRot() + cam.getXRot())
                || !Float.isFinite(rain + thunder)) throw new IllegalStateException("NONFINITE_SCENE");
        if (fixedPose == null) {
            fixedPose = pose; fixedDayTime = client.level.getDayTime(); fixedRain = rain; fixedThunder = thunder;
            fixedWidth = target.width; fixedHeight = target.height;
            report.add("input_state_capture_start", inputs.benchmarkInputState());
        } else if (!fixedPose.equals(pose) || fixedDayTime != client.level.getDayTime() || fixedRain != rain || fixedThunder != thunder
                || fixedWidth != target.width || fixedHeight != target.height) throw new IllegalStateException("FIXED_SCENE_CHANGED");
    }
    private void requireSource(JsonObject state) {
        if (!state.has("actual_shader_sha256") || !expectedArchive.equals(state.get("actual_shader_sha256").getAsString())) throw new IllegalStateException("ARCHIVE_MISMATCH");
        var options = state.getAsJsonObject("effective_shader_options");
        requireOptionsPin(options, expectedOptions, state.get("effective_shader_options_sha256").getAsString());
        for (String name : List.of("TAA", "TAAU", "SHADERLOD_LIVE_PIXEL_HISTORY", "SHADERLOD_LIVE_PIXEL_HISTORY_INCLUDED"))
            if (!options.has(name) || !"true".equals(options.get(name).getAsString())) throw new IllegalStateException("HISTORY_OPTION_REQUIRED_" + name);
        if (!options.has("TAAU_RENDER_SCALE") || !"0.75".equals(options.get("TAAU_RENDER_SCALE").getAsString())) throw new IllegalStateException("STATIC_FALLBACK_SCALE_MUST_BE_075");
    }
    static boolean optionEnabled(JsonObject options, String name) {
        return options.has(name) && "true".equals(options.get(name).getAsString());
    }
    static void requireOptionsPin(JsonObject options, String expected, String advertised) {
        if (expected.isBlank()) {
            if (options == null || options.size() != 368) throw new IllegalStateException("COMPLETE_368_OPTIONS_REQUIRED");
            return;
        }
        if (!expected.matches("[0-9a-f]{64}")) throw new IllegalStateException("EXPECTED_OPTIONS_HASH_INVALID");
        if (options == null || options.isEmpty()) throw new IllegalStateException("COMPLETE_OPTIONS_REQUIRED");
        var complete = new TreeMap<String, String>();
        options.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                throw new IllegalStateException("OPTIONS_NOT_ACTUAL_STRING_MAP");
            complete.put(entry.getKey(), entry.getValue().getAsString());
        });
        String canonical = new GsonBuilder().disableHtmlEscaping().create().toJson(complete);
        String actual = hash(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!expected.equals(actual) || !actual.equals(advertised)) throw new IllegalStateException("COMPLETE_OPTIONS_HASH_MISMATCH");
    }
    private void finish(Minecraft client, String status) throws Exception {
        finished = true;
        report.add("control_file_end_before_restore", JSON.toJsonTree(readControlIdentity(gameDirectory.getParent().resolve("control.json"))));
        var end = inputs.benchmarkInputState();
        end.add("configuration_files", ConfigurationFilesEvidence.capture(gameDirectory));
        report.add("input_state_end_before_restore", end);
        report.add("loaded_artifact_evidence_end_before_restore", LoadedArtifactEvidence.capture());
        requireCompleteBoundaries(end, report.getAsJsonObject("loaded_artifact_evidence_end_before_restore"));
        requireSource(report.getAsJsonObject("input_state_end_before_restore"));
        if (!initial.get("effective_shader_options_sha256").equals(report.getAsJsonObject("input_state_end_before_restore").get("effective_shader_options_sha256"))) throw new IllegalStateException("OPTIONS_CHANGED");
        restore(client); write(status);
        if (Boolean.getBoolean("shaderlod.dev.pixelHistoryCaptureOnly")) client.stop();
    }
    private static void requireCompleteBoundaries(JsonObject state, JsonObject artifacts) {
        if (!state.getAsJsonObject("configuration_files").get("complete").getAsBoolean()) throw new IllegalStateException("INCOMPLETE_CONFIGURATION_CAPTURE");
        if (artifacts == null || !artifacts.has("valid") || !artifacts.get("valid").getAsBoolean()) throw new IllegalStateException("INVALID_ACTUAL_ARTIFACT_CAPTURE");
    }
    private void restore(Minecraft client) { ShaderLodConfig.ENABLED.set(originalEnabled); client.options.hideGui = originalGui; report.addProperty("restored_global", originalEnabled); report.addProperty("restored_hide_gui", originalGui); }
    private void fail(Minecraft client, Exception failure) {
        finished = true;
        var pending = new JsonObject(); draws.forEach((stage, draw) -> pending.add(stage, draw.raw));
        report.add("failure_pending_draws", pending); report.addProperty("failure_draw_counter", drawCounter);
        draws.clear(); context = null;
        report.addProperty("failure", failure.toString()); restore(client);
        try { write("failed_exact_frame_capture"); } catch (Exception ignored) { com.mojang.logging.LogUtils.getLogger().error("Cannot write failed history manifest", ignored); }
        if (Boolean.getBoolean("shaderlod.dev.pixelHistoryCaptureOnly")) client.stop();
    }
    private void write(String status) throws Exception {
        report.addProperty("status", status); report.addProperty("updated_at_utc", Instant.now().toString());
        Path temporary = output.resolve("manifest.json.tmp"); Files.writeString(temporary, JSON.toJson(report));
        Files.move(temporary, output.resolve("manifest.json"), StandardCopyOption.REPLACE_EXISTING);
        Path hashTemporary = output.resolve("manifest.sha256.tmp");
        Files.writeString(hashTemporary, hash(Files.readAllBytes(output.resolve("manifest.json"))) + "  manifest.json\n");
        Files.move(hashTemporary, output.resolve("manifest.sha256"), StandardCopyOption.REPLACE_EXISTING);
    }
    private static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception impossible) { throw new AssertionError(impossible); } }
    static ControlIdentity readControlIdentity(Path path) throws Exception {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > 65536) throw new IllegalStateException("CONTROL_FILE_UNKNOWN_OR_TOO_LARGE");
        long modified = Files.getLastModifiedTime(path).toMillis();
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(65537); }
        if (bytes.length > 65536 || Files.size(path) != bytes.length || Files.getLastModifiedTime(path).toMillis() != modified)
            throw new IllegalStateException("CONTROL_FILE_CHANGED_DURING_READ");
        return new ControlIdentity(path.toAbsolutePath().normalize().toString(), modified, bytes.length, hash(bytes));
    }
    record ControlIdentity(String path, long modifiedMillis, int bytes, String sha256) {}
    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, String name, String stage, Program program, int[] drawBuffers, JsonObject source) {}
    private record Context(WorldRenderingPipeline pipeline, Map<Program, Compiled> programs) {}
    private record Draw(PixelHistoryTimeline.Values values, Map<String, UniformObservation> scale, JsonObject raw) {}
}
