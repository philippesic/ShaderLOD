package com.pp.shaderlod.dev.timing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.dev.WorkloadInputs;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.IrisRuntimeAccess;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.SourceFingerprint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** One default-off full native scene-depth capture. No additional render pass or timing queries. */
public final class PixelCoverageProbe {
    public static final String SCENE_PROPERTY = "shaderlod.dev.pixelCoverageSceneSha256";
    private static final String STAGE = "world0/deferred4";
    private static final String MEMBER = "shaders/world0/deferred4.fsh";
    private static final String MARKER = "shaderlod_live_pixel_scale";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson CANONICAL = new GsonBuilder().disableHtmlEscaping().create();
    private static final Map<Program, Compiled> PROGRAMS = new IdentityHashMap<>();
    private static final Map<WorldRenderingPipeline, Pipeline> PIPELINES = new IdentityHashMap<>();
    private static final Map<String, Integer> PACK_FIELDS = packFields();
    private static long nextGeneration, nextDraw;
    private static Context context;
    private static Capture capture;
    private PixelCoverageProbe() {}

    public static boolean enabled() { return Boolean.getBoolean(PixelCoverageEvidence.PROPERTY); }

    /** Call before pipeline construction. Off has no GL, archive or file work. */
    public static void initialize() { if (enabled()) requireProperties(System.getProperties()); }

    static void requireProperties(Properties properties) {
        if (!PixelCoverageEvidence.enabled(properties)) return;
        if (!Boolean.parseBoolean(properties.getProperty("shaderlod.dev.measureWorkload", "false"))
                || properties.getProperty("shaderlod.dev.compare", "").isBlank())
            throw new IllegalArgumentException("PIXEL_COVERAGE_REQUIRES_FIXED_WORKLOAD");
        for (String property : List.of("workTrace", "spatialLayoutProbe", "cloudSamplerProbe"))
            if (Boolean.parseBoolean(properties.getProperty("shaderlod.dev." + property, "false")))
                throw new IllegalArgumentException("PIXEL_COVERAGE_FORBIDS_OTHER_NATIVE_PROBE_" + property);
        double warmup = Double.parseDouble(properties.getProperty("shaderlod.dev.initialWarmup", "60"));
        if (!Double.isFinite(warmup) || warmup < 30) throw new IllegalArgumentException("PIXEL_COVERAGE_REQUIRES_30_SECOND_WARMUP");
        if (!properties.getProperty(SCENE_PROPERTY, "").matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("PIXEL_COVERAGE_REQUIRES_SOURCE_SCENE_SHA256");
    }

    /** Exact createProgram input object and returned Program; NEVER query GL during construction. */
    public static void compiledProgram(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
                                       ProgramSource source, Program program, Map<?, String> transformed,
                                       RenderTargets targets) {
        if (!enabled() || group != CompositePass.DEFERRED || source == null || program == null
                || !"deferred4".equals(source.getName())) return;
        try {
            var stages = new TreeMap<String, String>();
            transformed.forEach((key, value) -> { if (value != null) stages.put(key.toString(), value); });
            var hashes = new TreeMap<String, String>();
            stages.forEach((key, value) -> hashes.put(key, digest(value)));
            var pack = source.getParent().getPack();
            var compiled = new Compiled(renderer, pipeline, targets, pack, source, program, source.getName(),
                    source.getDirectives().getDrawBuffers().clone(), options(pack.getShaderPackOptions()),
                    digest(source.getVertexSource().orElseThrow()), digest(source.getFragmentSource().orElseThrow()),
                    Map.copyOf(hashes), stages.containsKey("VERTEX") && stages.containsKey("FRAGMENT")
                    && stages.get("VERTEX").contains(MARKER) && stages.get("FRAGMENT").contains(MARKER));
            PROGRAMS.put(program, compiled);
            PIPELINES.computeIfAbsent(pipeline, ignored -> new Pipeline(++nextGeneration));
        } catch (RuntimeException failure) {
            // Unknown source remains uncapturable, rather than guessing a stage from a numeric GL ID.
            if (capture != null) capture.fail("SOURCE_ASSOCIATION_FAILED: " + failure);
        }
    }

    /** Root passes the actual existing Iris Pass list; accessor identities are pinned to Iris 1.8.12. */
    public static void enterRenderer(Object renderer, WorldRenderingPipeline pipeline, CompositePass group, List<?> passes) {
        if (!enabled()) return;
        context = null;
        if (group != CompositePass.DEFERRED) return;
        var state = PIPELINES.get(pipeline);
        if (state == null) return;
        if (state.firstRendererNanos == 0) state.firstRendererNanos = System.nanoTime();
        if (capture == null || !capture.armed) return;
        try {
            Compiled selected = null;
            for (Object object : passes) {
                var pass = (SpatialPassAccessor) object;
                var compiled = PROGRAMS.get(pass.shaderlod$program());
                if (compiled == null) continue;
                if (selected != null || compiled.renderer != renderer || compiled.pipeline != pipeline
                        || !compiled.name.equals(pass.shaderlod$name())
                        || !Arrays.equals(compiled.drawBuffers, pass.shaderlod$drawBuffers()))
                    throw new IllegalStateException("ACTUAL_PASS_SOURCE_PROGRAM_ASSOCIATION_MISMATCH");
                selected = compiled;
            }
            if (selected == null) throw new IllegalStateException("NO_ASSOCIATED_DEFERRED4_PASS");
            context = new Context(selected, state);
        } catch (RuntimeException failure) { capture.fail("RENDERER_ASSOCIATION_FAILED: " + failure); capture.armed = false; }
    }

    public static void leaveRenderer() { if (enabled()) context = null; }

    /** WorkloadSession calls after its fixed warmup, while GPU/CPU timing remains explicitly off. */
    public static void beginCapture(Path directory) {
        if (!enabled()) return;
        requireProperties(System.getProperties());
        if (capture != null) throw new IllegalStateException("PIXEL_COVERAGE_ALREADY_STARTED");
        capture = new Capture(directory);
        try {
            Files.createDirectories(directory);
            if (Files.exists(capture.output) || Files.exists(capture.raw)) throw new IOException("REFUSE_CAPTURE_OVERWRITE");
            capture.allowWrite = true;
            capture.inputs = new WorkloadInputs(directory);
            capture.initialInputs = capture.inputs.livePixelInputState();
            capture.expectedGlobal = capture.initialInputs.getAsJsonObject("lod_settings").get("enabled").getAsBoolean() ? 1 : 0;
            WorkloadInputs.validateLivePixel(capture.initialInputs, capture.expectedGlobal == 1);
            capture.sceneReceipt = sceneReceipt(capture.initialInputs, System.getProperty(SCENE_PROPERTY));
            capture.control = readControl(directory);
            capture.requestBytes = Files.readAllBytes(directory.resolve("workload-request.json"));
            capture.request = JsonParser.parseString(new String(capture.requestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            capture.controlsReceipt = controlsReceipt(capture.initialInputs, capture.control, capture.expectedGlobal);
            var source = currentPack();
            capture.pack = source.identity();
            capture.sourcePath = source.source();
            capture.fingerprint = PackSourceReader.read(source.source());
            requireSource(capture.fingerprint, source.effectiveOptions());
            capture.sourceOptions = source.effectiveOptions();
            capture.world = Minecraft.getInstance().level;
            capture.camera = Minecraft.getInstance().getCameraEntity();
            if (capture.world == null || capture.camera == null) throw new IllegalStateException("NO_WORLD_OR_CAMERA");
            capture.armed = true;
        } catch (Exception failure) { capture.fail("ARM_FAILED: " + failure); }
        if (!capture.armed) save();
    }

    /** Actual quad HEAD: Program.use and CustomUniforms.push have completed, opaque GBuffer is complete. */
    public static void beforeQuadDraw() {
        if (!enabled() || capture == null || !capture.armed || context == null) return;
        var selected = context.compiled;
        try {
            int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (currentProgram != selected.program.getProgramId()) return; // Other real deferred passes are outside scope.
            capture.armed = false; // Exactly one attempt, including guard/read/restore failures.
            requireProperties(System.getProperties());
            capture.elapsedWarmupSeconds = (System.nanoTime() - context.pipeline.firstRendererNanos) * 1e-9;
            if (context.pipeline.firstRendererNanos == 0 || capture.elapsedWarmupSeconds < 30)
                throw new IllegalStateException("ACTUAL_RENDERER_WARMUP_LESS_THAN_30_SECONDS");
            var actual = capture.inputs.livePixelInputState();
            WorkloadInputs.validateLivePixel(actual, capture.expectedGlobal == 1);
            capture.drawInputs = actual;
            var boundaryChecks = new JsonObject();
            boundaryChecks.addProperty("sceneReceiptEqualsArmed", capture.sceneReceipt.equals(sceneReceipt(actual, System.getProperty(SCENE_PROPERTY))));
            boundaryChecks.addProperty("completeOtherControlsEqualArmed", capture.controlsReceipt.equals(controlsReceipt(actual, readControl(capture.directory), capture.expectedGlobal)));
            boundaryChecks.addProperty("workloadRequestBytesEqualArmed", Arrays.equals(capture.requestBytes, Files.readAllBytes(capture.directory.resolve("workload-request.json"))));
            capture.drawBoundaryPredicateChecks = boundaryChecks;
            requireChecks(boundaryChecks, "SCENE_OR_COMPLETE_CONTROLS_CHANGED_AFTER_ARM");
            var pack = currentPack();
            var fingerprint = PackSourceReader.read(pack.source());
            requireSource(fingerprint, pack.effectiveOptions());
            var client = Minecraft.getInstance();
            var actualPipeline = Iris.getPipelineManager().getPipelineNullable();
            var dimension = Iris.getCurrentDimension();
            var sourcePack = selected.source.getParent().getPack();
            var camera = client.getCameraEntity();
            var checks = new JsonObject();
            checks.addProperty("currentPackIsArmedPackObject", pack.identity() == capture.pack);
            checks.addProperty("currentPackIsCompiledPackObject", pack.identity() == selected.pack);
            checks.addProperty("sourceParentPackIsCurrentPackObject", sourcePack == pack.identity());
            checks.addProperty("completeSourceFingerprintEqualsArmed", fingerprint.equals(capture.fingerprint));
            checks.addProperty("compiledOptionsEqualArmed", selected.options.equals(capture.sourceOptions));
            checks.addProperty("currentOptionsEqualArmed", pack.effectiveOptions().equals(capture.sourceOptions));
            checks.addProperty("actualRenderTargetsPresent", selected.targets != null);
            checks.addProperty("currentPipelineIsCompiledPipelineObject", actualPipeline == selected.pipeline);
            checks.addProperty("currentDimensionIsOverworldValue", isOverworld(dimension));
            checks.addProperty("fallbackAbsent", !Iris.isFallback());
            checks.addProperty("currentWorldIsArmedWorldObject", client.level == capture.world);
            checks.addProperty("currentCameraIsArmedCameraObject", camera == capture.camera);
            checks.addProperty("markerInBothTransformedStages", selected.marker);
            checks.addProperty("transformedVertexPresent", selected.transformedHashes.containsKey("VERTEX"));
            checks.addProperty("transformedFragmentPresent", selected.transformedHashes.containsKey("FRAGMENT"));
            capture.drawPredicateChecks = checks;
            var observed = identityObservations(pack.identity(), capture.pack, selected.pack, sourcePack,
                    actualPipeline, selected.pipeline, client.level, capture.world, camera, capture.camera, dimension);
            observed.addProperty("actualSourceSha256", fingerprint.sourceSha256());
            observed.addProperty("armedSourceSha256", capture.fingerprint.sourceSha256());
            observed.addProperty("actualMembersSha256", digestMap(fingerprint.memberSha256()));
            observed.addProperty("armedMembersSha256", digestMap(capture.fingerprint.memberSha256()));
            observed.addProperty("currentOptionsSha256", digestMap(pack.effectiveOptions()));
            observed.addProperty("compiledOptionsSha256", digestMap(selected.options));
            observed.addProperty("armedOptionsSha256", digestMap(capture.sourceOptions));
            observed.add("transformedStageKeys", JSON.toJsonTree(selected.transformedHashes.keySet()));
            capture.drawObserved = observed;
            // Retain the actual transformed/source hashes even when an identity predicate rejects the draw.
            capture.compiledProvenance = JSON.toJsonTree(new CompiledProvenance(selected.name, selected.preVertexHash,
                    selected.preFragmentHash, selected.transformedHashes, selected.drawBuffers)).getAsJsonObject();
            requireChecks(checks, "LIVE_SOURCE_OPTIONS_PIPELINE_WORLD_CAMERA_OR_TRANSFORM_CHANGED");
            int core = scalarInt(currentProgram, "shaderlodBridgeVersion");
            int global = scalarInt(currentProgram, "shaderlodEnabled");
            if (core != 2 || global != capture.expectedGlobal) throw new IllegalStateException("LINKED_CORE_GLOBAL_MISMATCH");
            var target = client.getMainRenderTarget();
            capture.contract = new PixelCoverageEvidence.Contract(fingerprint.sourceSha256(), digestMap(pack.effectiveOptions()),
                    fingerprint.memberSha256().get(MEMBER), selected.transformedHashes.get("VERTEX"),
                    selected.transformedHashes.get("FRAGMENT"), STAGE, MARKER, digestJson(capture.sceneReceipt),
                    digestJson(capture.controlsReceipt), target.width, target.height, global);
            capture.draw = new PixelCoverageEvidence.Draw(capture.contract.sourceSha256(), capture.contract.optionsSha256(),
                    capture.contract.memberSha256(), capture.contract.transformedVertexSha256(), capture.contract.transformedFragmentSha256(),
                    STAGE, MARKER, capture.contract.sceneCameraSha256(), capture.contract.otherControlsSha256(),
                    selected.program.getProgramId(), currentProgram, context.pipeline.generation, SystemTimeUniforms.COUNTER.getAsInt(),
                    ++nextDraw, core, global, true, selected.marker, true, true);
            capture.pipeline = selected.pipeline;
            readDepth(selected.targets);
        } catch (Exception failure) { capture.armed = false; capture.fail("DRAW_CAPTURE_FAILED: " + failure); }
        save();
    }

    private static void readDepth(RenderTargets targets) throws IOException {
        int texture = targets.getDepthTexture();
        if (texture <= 0) throw new IllegalStateException("NO_CURRENT_RENDER_TARGETS_DEPTH_OBJECT");
        var before = state();
        capture.before = before;
        if (before.currentProgram() != capture.draw.currentProgram()
                || before.drawFramebuffer() <= 0
                || !before.viewport().equals(List.of(0, 0, capture.contract.width(), capture.contract.height())))
            throw new IllegalStateException("UNKNOWN_ACTUAL_NATIVE_DEFERRED4_VIEWPORT_OR_FBO");
        FloatBuffer buffer = null;
        float[] depth = null;
        var errors = new ArrayList<String>();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); // On the already-active unit; no guessed sampler.
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            capture.texture = new PixelCoverageEvidence.Texture(texture, "IRIS_RENDER_TARGETS_CURRENT_DEPTH", 0,
                    format, width, height, targets.getDepthTexture(), true);
            if (width != capture.contract.width() || height != capture.contract.height()
                    || !List.of(0x81A5, 0x81A6, 0x81A7, 0x8CAC, 0x88F0, 0x8CAD).contains(format))
                throw new IllegalStateException("UNKNOWN_OR_NON_NATIVE_SCENE_DEPTH_STORAGE");
            buffer = MemoryUtil.memAllocFloat(Math.multiplyExact(width, height));
            PixelCoverageEvidence.fillSentinel(buffer, width, height);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            PACK_FIELDS.forEach((name, token) -> GL11.glPixelStorei(token, name.equals("ALIGNMENT") ? 1 : 0));
            capture.readbackAttempted = true;
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buffer);
            capture.readbackReturned = true;
        } catch (RuntimeException failure) {
            errors.add("READ_OR_SETUP_FAILED: " + failure);
        } finally {
            for (var field : PACK_FIELDS.entrySet()) attempt(errors, "PACK_" + field.getKey(),
                    () -> GL11.glPixelStorei(field.getValue(), before.packState().get(field.getKey())));
            attempt(errors, "PACK_BUFFER", () -> GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, before.pixelPackBuffer()));
            attempt(errors, "ACTIVE_TEXTURE", () -> GL13.glActiveTexture(before.activeTexture()));
            attempt(errors, "TEXTURE_BINDING_2D", () -> GL11.glBindTexture(GL11.GL_TEXTURE_2D, before.textureBinding2D()));
            try { capture.after = state(); }
            catch (RuntimeException failure) { errors.add("POST_RESTORE_STATE_UNAVAILABLE: " + failure); }
            // Copy even failed/sentinel lanes, preserving a raw unsuccessful read. Never substitute a clear plane.
            if (buffer != null) {
                try { depth = new float[buffer.capacity()]; buffer.get(0, depth); }
                finally { MemoryUtil.memFree(buffer); }
            }
            capture.restoreErrors = List.copyOf(errors);
        }
        if (depth == null) throw new IllegalStateException("MISSING_DEPTH_PLANE: " + errors);
        if (capture.after == null) {
            capture.failedRaw = writeFailedRaw(capture.raw, depth, capture.contract.width(), capture.contract.height());
            throw new IllegalStateException("MISSING_POST_RESTORE_STATE: " + errors);
        }
        capture.restoration = new PixelCoverageEvidence.Restoration(before, capture.after, errors);
        try {
            capture.evidence = PixelCoverageEvidence.rawCapture(capture.raw, capture.contract, capture.draw,
                    capture.texture, depth, capture.restoration, capture.readbackReturned);
        } catch (IllegalArgumentException failure) {
            capture.failedRaw = writeFailedRaw(capture.raw, depth, capture.contract.width(), capture.contract.height());
            throw failure;
        }
    }

    public static void endCapture() {
        if (!enabled() || capture == null) return;
        if (capture.armed) capture.fail("CAPTURE_WINDOW_ENDED_WITHOUT_ASSOCIATED_DEFERRED4_DRAW");
        capture.armed = false; context = null;
        try {
            requireProperties(System.getProperties());
            if (capture.inputs != null && capture.initialInputs != null) {
                var terminal = capture.inputs.livePixelInputState();
                capture.terminalInputs = terminal;
                WorkloadInputs.validateLivePixel(terminal, capture.expectedGlobal == 1);
                var pack = currentPack();
                var fingerprint = PackSourceReader.read(pack.source());
                var client = Minecraft.getInstance();
                var actualPipeline = Iris.getPipelineManager().getPipelineNullable();
                var dimension = Iris.getCurrentDimension();
                var camera = client.getCameraEntity();
                var checks = new JsonObject();
                checks.addProperty("sceneReceiptEqualsArmed", capture.sceneReceipt.equals(sceneReceipt(terminal, System.getProperty(SCENE_PROPERTY))));
                checks.addProperty("completeOtherControlsEqualArmed", capture.controlsReceipt.equals(controlsReceipt(terminal, readControl(capture.directory), capture.expectedGlobal)));
                checks.addProperty("workloadRequestBytesEqualArmed", Arrays.equals(capture.requestBytes, Files.readAllBytes(capture.directory.resolve("workload-request.json"))));
                checks.addProperty("currentPackIsArmedPackObject", pack.identity() == capture.pack);
                checks.addProperty("completeSourceFingerprintEqualsArmed", capture.fingerprint.equals(fingerprint));
                checks.addProperty("currentWorldIsArmedWorldObject", client.level == capture.world);
                checks.addProperty("currentCameraIsArmedCameraObject", camera == capture.camera);
                checks.addProperty("currentDimensionIsOverworldValue", isOverworld(dimension));
                checks.addProperty("fallbackAbsent", !Iris.isFallback());
                checks.addProperty("currentPipelineIsCapturedPipelineObjectIfCaptureEstablished", capture.pipeline == null || actualPipeline == capture.pipeline);
                capture.terminalPredicateChecks = checks;
                var observed = identityObservations(pack.identity(), capture.pack, null, null,
                        actualPipeline, capture.pipeline, client.level, capture.world, camera, capture.camera, dimension);
                observed.addProperty("capturePipelineEstablished", capture.pipeline != null);
                observed.addProperty("actualSourceSha256", fingerprint.sourceSha256());
                observed.addProperty("armedSourceSha256", capture.fingerprint.sourceSha256());
                observed.addProperty("actualMembersSha256", digestMap(fingerprint.memberSha256()));
                observed.addProperty("armedMembersSha256", digestMap(capture.fingerprint.memberSha256()));
                capture.terminalObserved = observed;
                requireChecks(checks, "TERMINAL_CAPTURE_CONTRACT_CHANGED");
            }
        } catch (Exception failure) { capture.fail("TERMINAL_CHECK_FAILED: " + failure); }
        capture.finished = true;
        save();
    }

    public static JsonObject report() {
        var result = new JsonObject();
        result.addProperty("kind", "pinned_iris_photon_full_native_depth_coverage");
        result.addProperty("diagnosticOnly", true); result.addProperty("performanceEvidence", false);
        result.addProperty("defaultOff", true);
        result.addProperty("status", capture == null ? "UNKNOWN_CAPTURE_NOT_STARTED"
                : capture.failures.isEmpty() && capture.evidence != null ? "OBSERVATIONS_ONLY" : "UNKNOWN");
        if (capture == null) return result;
        result.addProperty("captureFinished", capture.finished);
        result.addProperty("armed", capture.armed);
        result.addProperty("rawDepthDestination", capture.raw.toString());
        result.addProperty("actualRendererWarmupSeconds", capture.elapsedWarmupSeconds);
        result.addProperty("readbackAttempted", capture.readbackAttempted);
        result.addProperty("readbackReturned", capture.readbackReturned);
        result.add("failures", capture.failures.deepCopy());
        result.add("initialInputs", JSON.toJsonTree(capture.initialInputs));
        result.add("drawInputs", JSON.toJsonTree(capture.drawInputs));
        result.add("terminalInputs", JSON.toJsonTree(capture.terminalInputs));
        result.add("sceneReceipt", JSON.toJsonTree(capture.sceneReceipt));
        result.add("otherControlsReceipt", JSON.toJsonTree(capture.controlsReceipt));
        result.add("sourceFingerprint", JSON.toJsonTree(capture.fingerprint));
        if (capture.sourcePath != null) result.addProperty("actualSourcePath", capture.sourcePath.toString());
        result.addProperty("actualProfilePath", capture.directory.toAbsolutePath().normalize().toString());
        result.add("actualControl", JSON.toJsonTree(capture.control));
        result.add("actualWorkloadRequest", JSON.toJsonTree(capture.request));
        if (capture.requestBytes != null) result.addProperty("actualWorkloadRequestSha256", digest(capture.requestBytes));
        result.add("compiledProgramProvenance", JSON.toJsonTree(capture.compiledProvenance));
        result.add("drawBoundaryPredicateChecks", JSON.toJsonTree(capture.drawBoundaryPredicateChecks));
        result.add("drawPredicateChecks", JSON.toJsonTree(capture.drawPredicateChecks));
        result.add("drawObserved", JSON.toJsonTree(capture.drawObserved));
        result.add("terminalPredicateChecks", JSON.toJsonTree(capture.terminalPredicateChecks));
        result.add("terminalObserved", JSON.toJsonTree(capture.terminalObserved));
        result.add("contract", JSON.toJsonTree(capture.contract));
        result.add("draw", JSON.toJsonTree(capture.draw));
        result.add("texture", JSON.toJsonTree(capture.texture));
        result.add("before", JSON.toJsonTree(capture.before)); result.add("after", JSON.toJsonTree(capture.after));
        result.add("restoreErrors", JSON.toJsonTree(capture.restoreErrors));
        result.add("rawCoverage", JSON.toJsonTree(capture.evidence));
        result.add("failedRaw", JSON.toJsonTree(capture.failedRaw));
        result.addProperty("stageAssociation", "Actual createProgram ProgramSource->Program; DEFERRED Pass name/program/drawBuffers; fullscreen quad HEAD after uniforms, before deferred4/upscale");
        result.addProperty("sourceSceneReceiptScope", "Operator-supplied copied-save/source scene SHA-256 plus actual fixed pose/time/weather/FOV; caller must audit world provenance");
        result.addProperty("limits", "Depth footprint only; no fragment invocation, draw count, whole-pack allocation, motion quality or speed claim. OFF 1.0 fixed TAAU is not original native.");
        return result;
    }

    private static void save() {
        if (!capture.allowWrite) return;
        try {
            Files.writeString(capture.output, JSON.toJson(report()), capture.wroteReport
                    ? new StandardOpenOption[]{StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                    : new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE});
            capture.wroteReport = true;
        }
        catch (IOException failure) { throw new IllegalStateException("Cannot preserve pixel coverage report", failure); }
    }

    private static JsonObject readControl(Path directory) throws IOException {
        return JsonParser.parseString(Files.readString(directory.getParent().resolve("control.json"))).getAsJsonObject();
    }

    /** Only the separately checked control/global boolean is omitted; preserve all other requested control fields. */
    static JsonObject controlsReceipt(JsonObject inputs, JsonObject control, int global) {
        var field = control.get("enabled");
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isBoolean()
                || field.getAsBoolean() != (global == 1)) throw new IllegalArgumentException("REQUESTED_GLOBAL_CONTROL_MISMATCH");
        var normalizedControl = control.deepCopy(); normalizedControl.remove("enabled");
        var result = WorkloadInputs.withoutFeature(inputs, "live_pixel");
        // The full original control and its runtime input SHA remain exported separately.
        result.remove("control_sha256");
        result.add("requestedControlExceptAuthorizedGlobal", normalizedControl);
        return result;
    }

    static JsonObject sceneReceipt(JsonObject inputs, String sourceSceneHash) {
        if (sourceSceneHash == null || !sourceSceneHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("MISSING_SOURCE_SCENE_HASH");
        var result = new JsonObject(); result.addProperty("sourceSceneSha256", sourceSceneHash);
        for (String field : List.of("dimension", "day_time", "x", "y", "z", "yaw", "pitch", "rain", "thunder",
                "framebuffer_width", "framebuffer_height", "live_pixel_fov", "live_pixel_camera_type")) {
            if (!inputs.has(field) || inputs.get(field).isJsonNull()) throw new IllegalArgumentException("MISSING_SCENE_FIELD_" + field);
            result.add(field, inputs.get(field).deepCopy());
        }
        return result;
    }

    /** Iris constructs a new NamespacedId on each loaded-world query; exact value equality is required. */
    static boolean isOverworld(NamespacedId actual) { return DimensionId.OVERWORLD.equals(actual); }

    private static void requireChecks(JsonObject checks, String reason) {
        var failures = new ArrayList<String>();
        checks.entrySet().forEach(entry -> { if (!entry.getValue().getAsBoolean()) failures.add(entry.getKey()); });
        if (!failures.isEmpty()) throw new IllegalStateException(reason + ": " + failures);
    }

    private static JsonObject identityObservations(Object actualPack, Object armedPack, Object compiledPack, Object sourcePack,
                                                   Object actualPipeline, Object associatedPipeline, Object actualWorld, Object armedWorld,
                                                   Object actualCamera, Object armedCamera, NamespacedId dimension) {
        var observed = new JsonObject();
        observed.addProperty("currentPackObject", objectLabel(actualPack)); observed.addProperty("armedPackObject", objectLabel(armedPack));
        observed.addProperty("compiledPackObject", objectLabel(compiledPack)); observed.addProperty("sourceParentPackObject", objectLabel(sourcePack));
        observed.addProperty("currentPipelineObject", objectLabel(actualPipeline)); observed.addProperty("associatedPipelineObject", objectLabel(associatedPipeline));
        observed.addProperty("currentWorldObject", objectLabel(actualWorld)); observed.addProperty("armedWorldObject", objectLabel(armedWorld));
        observed.addProperty("currentCameraObject", objectLabel(actualCamera)); observed.addProperty("armedCameraObject", objectLabel(armedCamera));
        observed.addProperty("currentDimensionClass", dimension == null ? "null" : dimension.getClass().getName());
        observed.addProperty("currentDimensionValue", dimension == null ? "null" : dimension.toString());
        observed.addProperty("expectedDimensionValue", DimensionId.OVERWORLD.toString());
        observed.addProperty("dimensionSameObjectAsConstant", dimension == DimensionId.OVERWORLD);
        observed.addProperty("objectLabelScope", "Class plus identityHashCode is supplementary and can collide; predicate booleans use actual reference/value comparisons");
        return observed;
    }

    private static String objectLabel(Object object) {
        return object == null ? "null" : object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    static void requireSource(SourceFingerprint source, Map<String, String> options) {
        if (!WorkloadInputs.LIVE_PIXEL_SHA256.equals(source.sourceSha256()) || source.memberSha256().size() != 533
                || !WorkloadInputs.LIVE_PIXEL_MEMBERS_SHA256.equals(digestMap(source.memberSha256()))
                || options.size() != 368 || !WorkloadInputs.LIVE_PIXEL_OPTIONS_SHA256.equals(digestMap(options))
                || !source.memberSha256().containsKey(MEMBER)) throw new IllegalArgumentException("UNPINNED_COMPLETE_LIVE_PIXEL_SOURCE_OR_OPTIONS");
    }

    private static AdapterRuntimeBinding.Pack currentPack() {
        var pack = new IrisRuntimeAccess(Set.of()).currentPack().orElseThrow(() -> new IllegalStateException("NO_ACTIVE_PACK"));
        return new AdapterRuntimeBinding.Pack(pack.identity(), pack.source(), options(Iris.getCurrentPack().orElseThrow().getShaderPackOptions()));
    }

    private static Map<String, String> options(net.irisshaders.iris.shaderpack.option.ShaderPackOptions options) {
        var keys = new java.util.HashSet<>(options.getOptionSet().getBooleanOptions().keySet());
        keys.addAll(options.getOptionSet().getStringOptions().keySet());
        return IrisRuntimeAccess.effectiveOptions(options, keys);
    }

    private static int scalarInt(int program, String name) {
        int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        if (count < 0 || count > 4096) throw new IllegalStateException("UNKNOWN_UNIFORM_INVENTORY");
        try (var stack = MemoryStack.stackPush()) {
            var size = stack.mallocInt(1); var type = stack.mallocInt(1);
            for (int i = 0; i < count; i++) {
                if (!name.equals(GL20.glGetActiveUniform(program, i, 1024, size, type))) continue;
                int location = GL20.glGetUniformLocation(program, name);
                if (size.get(0) != 1 || type.get(0) != GL11.GL_INT || location < 0)
                    throw new IllegalStateException("UNKNOWN_LINKED_SCALAR_INT_" + name);
                return GL20.glGetUniformi(program, location);
            }
        }
        throw new IllegalStateException("MISSING_LINKED_SCALAR_INT_" + name);
    }

    private static PixelCoverageEvidence.State state() {
        try (var stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            var pack = new LinkedHashMap<String, Integer>();
            PACK_FIELDS.forEach((name, token) -> pack.put(name, GL11.glGetInteger(token)));
            return new PixelCoverageEvidence.State(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    List.of(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3)),
                    GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                    GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING), pack);
        }
    }

    private static Map<String, Integer> packFields() {
        var fields = new LinkedHashMap<String, Integer>();
        fields.put("ALIGNMENT", GL11.GL_PACK_ALIGNMENT); fields.put("ROW_LENGTH", GL11.GL_PACK_ROW_LENGTH);
        fields.put("SKIP_ROWS", GL11.GL_PACK_SKIP_ROWS); fields.put("SKIP_PIXELS", GL11.GL_PACK_SKIP_PIXELS);
        fields.put("SWAP_BYTES", GL11.GL_PACK_SWAP_BYTES); fields.put("LSB_FIRST", GL11.GL_PACK_LSB_FIRST);
        fields.put("IMAGE_HEIGHT", GL12.GL_PACK_IMAGE_HEIGHT); fields.put("SKIP_IMAGES", GL12.GL_PACK_SKIP_IMAGES);
        return java.util.Collections.unmodifiableMap(fields);
    }
    private static void attempt(List<String> errors, String label, Runnable operation) {
        try { operation.run(); } catch (RuntimeException failure) { errors.add("RESTORE_" + label + "_FAILED: " + failure); }
    }
    private static Map<String, Object> writeFailedRaw(Path raw, float[] depth, int width, int height) throws IOException {
        var bytes = ByteBuffer.allocate(Math.multiplyExact(depth.length, 4)).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : depth) bytes.putFloat(value);
        Files.write(raw, bytes.array(), StandardOpenOption.CREATE_NEW);
        return Map.of("path", raw.toString(), "sha256", digest(bytes.array()), "bytes", bytes.capacity(), "width", width,
                "height", height, "format", "GL_DEPTH_COMPONENT/GL_FLOAT:f32le", "origin", "lower-left", "failedCapture", true);
    }
    static String digestMap(Map<String, String> values) { return digest(CANONICAL.toJson(new TreeMap<>(values))); }
    private static String digestJson(JsonObject value) { return WorkloadInputs.canonicalJsonHash(value); }
    private static String digest(String value) { return digest(value.getBytes(StandardCharsets.UTF_8)); }
    private static String digest(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, RenderTargets targets, Object pack,
                            ProgramSource source, Program program, String name, int[] drawBuffers, Map<String, String> options,
                            String preVertexHash, String preFragmentHash, Map<String, String> transformedHashes, boolean marker) {}
    private record CompiledProvenance(String actualPassName, String preTransformVertexSha256, String preTransformFragmentSha256,
                                      Map<String, String> actualTransformPatcherStageSha256, int[] actualDrawBuffers) {}
    private record Context(Compiled compiled, Pipeline pipeline) {}
    private static final class Pipeline {
        final long generation; long firstRendererNanos;
        Pipeline(long generation) { this.generation = generation; }
    }
    private static final class Capture {
        final Path directory, output, raw; final JsonArray failures = new JsonArray();
        boolean armed, finished, readbackAttempted, readbackReturned, allowWrite, wroteReport; double elapsedWarmupSeconds; int expectedGlobal;
        Object pack, world, camera; Path sourcePath; WorkloadInputs inputs; SourceFingerprint fingerprint;
        WorldRenderingPipeline pipeline; byte[] requestBytes;
        Map<String, String> sourceOptions; Map<String, Object> failedRaw; List<String> restoreErrors = List.of();
        JsonObject initialInputs, drawInputs, terminalInputs, sceneReceipt, controlsReceipt, compiledProvenance, control, request;
        JsonObject drawBoundaryPredicateChecks, drawPredicateChecks, drawObserved, terminalPredicateChecks, terminalObserved;
        PixelCoverageEvidence.Contract contract; PixelCoverageEvidence.Draw draw; PixelCoverageEvidence.Texture texture;
        PixelCoverageEvidence.State before, after; PixelCoverageEvidence.Restoration restoration; PixelCoverageEvidence.Capture evidence;
        Capture(Path directory) { this.directory = directory; output = directory.resolve("pixel-coverage-evidence.json"); raw = directory.resolve("pixel-coverage-depth.f32le"); }
        void fail(String reason) { if (failures.size() < 32) failures.add(reason); }
    }
}
