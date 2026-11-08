package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.pp.shaderlod.dev.timing.GpuTimers;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Explicit opt-in, development-only camera/capture experiment.
 * No performance result is produced here.
 * Session/frame changes stay on the client thread; one writer also updates
 * synchronized image-lease stages. At most two images/callbacks are pending.
 */
public final class CameraQualitySession {
    /** Shared bounded pose sampler for the authored-world quality caller; no writer or timing. */
    static final class MotionPath {
        private final Config config;
        private final Pose original;
        MotionPath(Minecraft client, JsonObject request) {
            config = Config.parse(request.toString());
            original = Pose.of(client);
            if (!original.near(config.waypoints().getFirst().pose(), .05, .1))
                throw new IllegalArgumentException("Motion must start at the current authored pose");
        }
        double duration() { return config.duration(); }
        void applyAt(Minecraft client, double seconds) { apply(client, config.poseAt(seconds)); }
        void restore(Minecraft client) { apply(client, original); }
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long MAX_PIXELS = 4096L * 2160;
    private static final long MAX_PNG_BYTES = 64L * 1024 * 1024;
    private static final long MAX_SEQUENCE_BYTES = 12L * 1024 * 1024 * 1024;
    private static final boolean LITTLE_ENDIAN_PIXELS = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;
    private final Path gameDirectory, output, source;
    private final String sourceHash;
    private final Config config;
    private final WorkloadInputs inputs;
    private final boolean benchmarkOwned;
    private final com.pp.shaderlod.benchmark.core.CaptureMotionClock motionClock;
    private final JsonObject report = new JsonObject();
    private final JsonArray frames = new JsonArray();
    private final long created = System.nanoTime();
    private final ThreadPoolExecutor writer;
    private final CaptureWriteTracker writes;
    private final boolean closeOwnedClient = Boolean.getBoolean("shaderlod.dev.cameraQualityOnly");
    private UUID owner;
    private Pose originalPose;
    private World initialWorld;
    private CompletableFuture<World> worldCheck;
    private JsonObject initialInputs, captureInputs;
    private Object worldReference, pipelineReference;
    private long warmedAt, motionStarted, drainingAt, lastWorldCheck, lastFingerprint;
    private int warmedTicks, motionTick, nextSlot, renderedFrames, completedWrites, skippedFrames, writerWaitFrames;
    private long completedBytes;
    private boolean moving, draining, drainTimeoutReported, finalized;
    private String failure;

    /** Call once when constructing the dev client, never once per tick. */
    public static CameraQualitySession createIfRequested(Path directory) {
        String requested = System.getProperty("shaderlod.dev.cameraQuality", "");
        if (requested.isBlank()) return null;
        Path output = null;
        try {
            Path game = directory.toRealPath();
            Path root = game.resolve("camera-quality");
            if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("camera-quality must not be a symbolic link");
            Files.createDirectories(root);
            if (!root.toRealPath().getParent().equals(game)) throw new IllegalArgumentException("Output escaped game directory");
            output = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
            Path source = Path.of(requested).toAbsolutePath().normalize();
            if (!Files.isRegularFile(source) || Files.size(source) > 65536) throw new IllegalArgumentException("Request must be a local regular file <= 64 KiB");
            byte[] bytes;
            try (var stream = Files.newInputStream(source)) { bytes = stream.readNBytes(65537); }
            if (bytes.length > 65536) throw new IllegalArgumentException("Request grew beyond 64 KiB");
            String text = new String(bytes, StandardCharsets.UTF_8);
            // Preserve the exact immutable source, including requests rejected by validation.
            Files.write(output.resolve("request.json"), bytes);
            return new CameraQualitySession(game, output, source, hash(bytes), Config.parse(text));
        } catch (Exception exception) {
            LogUtils.getLogger().error("Camera quality request rejected", exception);
            if (output != null) try {
                JsonObject rejected = new JsonObject();
                rejected.addProperty("schema", 1);
                rejected.addProperty("status", "rejected_request");
                rejected.addProperty("failure", exception.toString());
                rejected.addProperty("request_property", requested);
                if (Files.isRegularFile(output.resolve("request.json"))) rejected.addProperty("request_sha256", safeHash(output.resolve("request.json")));
                rejected.addProperty("written_at_utc", Instant.now().toString());
                Files.writeString(output.resolve("manifest.json"), GSON.toJson(rejected));
            } catch (Exception writeFailure) { LogUtils.getLogger().error("Cannot retain camera request rejection", writeFailure); }
            return null;
        }
    }

    /** Explicit neutral-controller integration; no world/config/presentation edits. */
    static CameraQualitySession createForBenchmark(Minecraft client, Path operationRoot, String label, JsonObject request) throws Exception {
        if (!Boolean.getBoolean("shaderlod.dev.qualityMotionRuntime") || !label.matches("[A-Za-z0-9_]{1,48}"))
            throw new IllegalArgumentException("Explicit bounded benchmark quality runtime required");
        Config config = Config.parse(request.toString());
        Path game=client.gameDirectory.toPath().toRealPath(), root=operationRoot.toRealPath();
        if (Files.isSymbolicLink(operationRoot) || !Files.isRegularFile(root.resolve("control.json")))
            throw new IllegalStateException("Owned controller root required");
        if (Files.getFileStore(root).getUsableSpace()<32L*1024*1024*1024)
            throw new IllegalStateException("Quality capture requires the 32 GiB storage reserve");
        var server=client.getSingleplayerServer();
        if (server==null || client.player==null || !server.tickRateManager().isFrozen())
            throw new IllegalStateException("Existing owned frozen server required");
        Path output=Files.createDirectory(root.resolve("quality-"+label));
        Path source=output.resolve("request.json");byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(source,bytes,java.nio.file.StandardOpenOption.CREATE_NEW);
        return new CameraQualitySession(game,output,source,hash(bytes),config,true);
    }

    boolean finalized() { return finalized; }
    JsonObject status() {
        var result=new JsonObject();result.addProperty("finalized",finalized);
        result.addProperty("output_directory",output.toString());
        result.add("status",report.get("status"));
        if(failure!=null)result.addProperty("failure",failure);
        return result;
    }
    void cancel(Minecraft client) { if(!finalized)fail(client,new IllegalStateException("Quality recorder cancelled")); }

    private CameraQualitySession(Path game, Path output, Path source, String sourceHash, Config config) throws Exception {
        this(game,output,source,sourceHash,config,false);
    }
    private CameraQualitySession(Path game, Path output, Path source, String sourceHash, Config config, boolean benchmarkOwned) throws Exception {
        this.gameDirectory = game;
        this.output = output;
        this.source = source;
        this.sourceHash = sourceHash;
        this.config = config;
        this.benchmarkOwned = benchmarkOwned;
        motionClock = new com.pp.shaderlod.benchmark.core.CaptureMotionClock(benchmarkOwned);
        inputs = new WorkloadInputs(game);
        writes = new CaptureWriteTracker(config.maximumInFlightImages());
        var queue = new ArrayBlockingQueue<Runnable>(1) {
            @Override public boolean offer(Runnable task) {
                boolean accepted = super.offer(task);
                // Capacity is exactly one, so every successful offer proves a peak of one,
                // even if the worker dequeues before a later client-thread sample.
                if (accepted) writes.observeExecutorQueue(1);
                return accepted;
            }
        };
        writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, task -> {
            Thread thread = new Thread(task, "ShaderLOD-camera-quality-write");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        report.addProperty("schema", 1);
        report.addProperty("label", config.label());
        report.addProperty("created_at_utc", Instant.now().toString());
        report.addProperty("request_path", source.toString());
        report.addProperty("request_sha256", sourceHash);
        report.addProperty("output_directory", output.toString());
        report.addProperty("evidence_kind", "unmeasured moving-camera quality; PNG readback/write can stall rendering");
        report.addProperty("clock_policy", benchmarkOwned
                ? "20 Hz logical eased waypoint ticks, at most one path tick between renders; catch-up ticks and full PNG leases hold the path, so wall duration can exceed path duration. Actual rendered camera recorded; Iris clocks/history advance normally. Untimed only."
                : "20 Hz eased waypoint ticks; actual Minecraft camera position and rotation recorded per image; Iris clocks and temporal history advance normally");
        report.addProperty("world_policy", benchmarkOwned ? "Owned benchmark already freezes the integrated server; actual tick freeze, mode, day/weather observed without edits by this recorder" : "Existing singleplayer control must freeze daylight/weather; no world rules/time/weather edits by this harness");
        report.addProperty("benchmark_owned",benchmarkOwned);
        report.addProperty("close_owned_client_requested", closeOwnedClient);
        addPngEncoding(report);
        report.addProperty("maximum_png_bytes", MAX_PNG_BYTES);
        report.addProperty("maximum_sequence_png_bytes", MAX_SEQUENCE_BYTES);
        report.addProperty("maximum_framebuffer_pixels", MAX_PIXELS);
        report.addProperty("maximum_in_flight_images", config.maximumInFlightImages());
        report.addProperty("maximum_native_image_slot_bytes", config.maximumInFlightImages() * MAX_PIXELS * 4);
        report.addProperty("writer_threads", 1);
        report.addProperty("writer_executor_queue_capacity", 1);
        report.addProperty("image_lease_policy", "Pending callbacks retain capacity; tracked image slots include reserved readback and waiting/active writer images; NativeImage closes before its callback; one fixed encoder-buffer set");
        report.addProperty("native_pixel_integer_byte_order", java.nio.ByteOrder.nativeOrder().toString());
        report.addProperty("png_pixel_conversion", "Native RGBA byte layout; getPixelRGBA int -> canonical 0xAABBGGRR -> R,G,B,A bytes; no scaling, gamma or alpha conversion");
        report.add("config", GSON.toJsonTree(config));
        var cameraContract = new JsonObject();
        if (config.expectedCameraFluid() == null) cameraContract.add("expected_camera_fluid", com.google.gson.JsonNull.INSTANCE);
        else cameraContract.addProperty("expected_camera_fluid", config.expectedCameraFluid());
        cameraContract.addProperty("reject_full_block_camera", config.rejectFullBlockCamera());
        cameraContract.addProperty("scope", "Optional actual camera FogType match and known full collision cell rejection only; no ray visibility, glint or partial-shape containment proof");
        report.add("camera_contract", cameraContract);
        report.add("frames", frames);
        var mods = new JsonObject();
        for (var mod : ModList.get().getMods()) mods.addProperty(mod.getModId(), mod.getVersion().toString());
        report.add("loaded_mod_versions", mods);
        report.add("loaded_mod_artifacts", artifactIdentities());
        report.add("loaded_artifact_evidence_initial", LoadedArtifactEvidence.capture());
        try (var stream = CameraQualitySession.class.getResourceAsStream("CameraQualitySession.class")) {
            if (stream != null) report.addProperty("harness_class_sha256", hash(stream.readAllBytes()));
        }
        try (var stream = FastPngWriter.class.getResourceAsStream("FastPngWriter.class")) {
            if (stream != null) report.addProperty("png_writer_class_sha256", hash(stream.readAllBytes()));
        }
        try (var stream = WaterSceneSurvey.class.getResourceAsStream("WaterSceneSurvey.class")) {
            if (stream != null) report.addProperty("camera_observer_class_sha256", hash(stream.readAllBytes()));
        }
        try (var stream = CaptureWriteTracker.class.getResourceAsStream("CaptureWriteTracker.class")) {
            if (stream != null) report.addProperty("capture_write_tracker_class_sha256", hash(stream.readAllBytes()));
        }
        report.addProperty("java_version", System.getProperty("java.version"));
        report.addProperty("os_name", System.getProperty("os.name"));
        report.addProperty("os_version", System.getProperty("os.version"));
        writeManifest("waiting_for_prepared_scene");
        LogUtils.getLogger().info("Camera quality session: {}", output);
    }

    /** Integrate after existing control application in ClientTickEvent.Post. */
    public void tick(Minecraft client) {
        if (finalized) return;
        try {
            long now = System.nanoTime();
            if (draining) {
                if (writes.drained()) finish(client);
                else if (!drainTimeoutReported && seconds(now, drainingAt) > 30) {
                    drainTimeoutReported = true;
                    if (failure == null) failure = "Screenshot writer callbacks did not all complete within 30 seconds";
                    report.addProperty("failure", failure);
                    report.addProperty("drain_timeout_reported", true);
                    // Do not finalize/close a client while a queued or active image is still owned.
                    writeManifest("failed_waiting_for_png_callbacks");
                }
                return;
            }
            if (owner == null) {
                if (seconds(now, created) > 120) throw new IllegalStateException("Prepared spectator/frozen scene did not become ready within 120 seconds");
                if (client.level == null || client.player == null || client.getSingleplayerServer() == null || !benchmarkOwned && !client.player.isSpectator()) return;
                if (client.getCameraEntity() != client.player || client.screen != null || client.isPaused()) return;
                if (!config.waypoints().getFirst().pose().near(Pose.of(client), 0.05, 0.1)) return;
                if (worldCheck == null) requestWorldCheck(client, now);
                if (!worldCheck.isDone()) return;
                World observed = worldCheck.join();
                worldCheck = null;
                if (!acceptableWorld(observed)) return;
                JsonObject preparedInputs = qualityInputState();
                // Prepared controls arrive through separate server/player/time packets.
                // Do not freeze the fingerprint until the client reflects the frozen server scene.
                if (!preparedInputs.get("dimension").getAsString().equals(observed.dimension())
                        || preparedInputs.get("day_time").getAsLong() != observed.dayTime()) {
                    report.addProperty("waiting_for_client_scene_sync", true);
                    return;
                }
                report.addProperty("waiting_for_client_scene_sync", false);
                owner = client.player.getUUID();
                originalPose = Pose.of(client);
                initialWorld = observed;
                initialInputs = preparedInputs;
                requirePipeline(initialInputs);
                worldReference = client.level;
                pipelineReference = Iris.getPipelineManager().getPipelineNullable();
                if (pipelineReference == null) throw new IllegalStateException("Prepared Iris pipeline missing");
                report.add("input_state_initial", initialInputs);
                report.add("server_world_initial", GSON.toJsonTree(initialWorld));
                report.addProperty("owned_player_uuid", owner.toString());
                warmedAt = now;
                apply(client, config.waypoints().getFirst().pose());
                client.player.setOldPosAndRot(); // Snap only before warmup, never on moving frames.
                writeManifest("warming");
            }
            requireOwner(client);
            if (worldCheck != null && worldCheck.isDone()) {
                World observed = worldCheck.join();
                worldCheck = null;
                if (!initialWorld.equals(observed) || !acceptableWorld(observed)) throw new IllegalStateException("Server frozen scene changed: " + observed);
                report.add("server_world_last_observed", GSON.toJsonTree(observed));
                report.addProperty("server_world_last_observed_at_utc", Instant.now().toString());
            }
            if (worldCheck == null && seconds(now, lastWorldCheck) >= 1) requestWorldCheck(client, now);
            if (seconds(now, lastWorldCheck) > 5) throw new IllegalStateException("Server scene observation stalled");
            if (seconds(now, lastFingerprint) >= 1) {
                validateFingerprint(client);
                lastFingerprint = now;
            }
            if (!moving) {
                apply(client, config.waypoints().getFirst().pose());
                warmedTicks++;
                if (seconds(now, warmedAt) < config.warmupSeconds() || warmedTicks < Math.ceil(config.warmupSeconds() * 20)) return;
                captureInputs = qualityInputState();
                requirePipeline(captureInputs);
                if (!initialInputs.get("actual_shader_sha256").equals(captureInputs.get("actual_shader_sha256"))) throw new IllegalStateException("Shader archive changed during warmup");
                report.add("input_state_capture_start", captureInputs);
                report.add("shader_temporal_capture_start", inputs.temporalInputs());
                report.addProperty("actual_warmup_wall_seconds", seconds(now, warmedAt));
                report.addProperty("actual_warmup_ticks", warmedTicks);
                report.addProperty("motion_started_at_utc", Instant.now().toString());
                motionStarted = now;
                moving = true;
                writeManifest("capturing");
                return;
            }
            if (seconds(now, motionStarted) > 60) throw new IllegalStateException("Motion exceeded 60 seconds of real time");
            motionTick = motionClock.advanceTick(writes.hasCapacity());
            apply(client, config.poseAt(Math.min(config.duration(), motionTick / 20.0)));
            // The extra tick lets the final target appear in normal camera interpolation.
        } catch (Exception exception) { fail(client, exception); }
    }

    /** Integrate at RenderGuiEvent.Post before any hideGui early return. */
    public void render(Minecraft client) {
        if (finalized || !moving || draining) return;
        try {
            requireOwner(client);
            if (!IrisApi.getInstance().isShaderPackInUse() || Iris.isFallback()) throw new IllegalStateException("Iris pipeline disappeared during capture");
            renderedFrames++;
            float partial = CapturedRenderingState.INSTANCE.getTickDelta();
            if (!Float.isFinite(partial) || partial < 0 || partial > 1) throw new IllegalStateException("Invalid rendered tick delta");
            double pathSeconds = motionClock.pathSeconds(config.duration(), partial);
            motionClock.rendered();
            int due = Math.min(config.slotCount() - 1, (int)Math.floor(pathSeconds * config.fps() + 1e-6));
            while (nextSlot < due) skipped(nextSlot++, "missed_render_slot");
            if (nextSlot <= due && nextSlot < config.slotCount()) {
                if (!writes.hasCapacity()) {
                    if (benchmarkOwned) writerWaitFrames++;
                    else skipped(nextSlot++, "writer_backpressure");
                } else capture(client, nextSlot++, pathSeconds, partial);
            }
            if (motionTick > config.duration() * 20 && nextSlot >= config.slotCount()) {
                report.addProperty("actual_motion_wall_seconds", seconds(System.nanoTime(), motionStarted));
                report.addProperty("actual_motion_ticks", motionTick);
                report.addProperty("held_path_ticks", motionClock.heldTicks());
                report.addProperty("held_writer_ticks", motionClock.writerHeldTicks());
                report.addProperty("writer_capacity_wait_render_frames", writerWaitFrames);
                JsonObject end = qualityInputState();
                report.add("input_state_capture_end", end);
                if (!captureInputs.get("actual_shader_sha256").equals(end.get("actual_shader_sha256"))) throw new IllegalStateException("Shader archive changed during capture");
                report.add("shader_temporal_capture_end", inputs.temporalInputs());
                validateFingerprint(client);
                beginDrain();
            }
        } catch (Exception exception) { fail(client, exception); }
    }

    private void capture(Minecraft client, int slot, double pathSeconds, float partial) throws Exception {
        var target = client.getMainRenderTarget();
        if (target.width < 1 || target.height < 1 || (long)target.width * target.height > MAX_PIXELS) throw new IllegalStateException("Framebuffer exceeds 4096*2160 pixel memory bound");
        long reservedPngBytes = (writes.pendingCount() + 1L) * MAX_PNG_BYTES;
        if (completedBytes > MAX_SEQUENCE_BYTES - reservedPngBytes) throw new IllegalStateException("Sequence has insufficient disk budget for all pending bounded PNGs");
        var camera = client.gameRenderer.getMainCamera();
        var position = camera.getPosition();
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !Float.isFinite(camera.getYRot()) || !Float.isFinite(camera.getXRot())) throw new IllegalStateException("Rendered camera is non-finite");
        if (!report.has("gl_renderer")) {
            report.addProperty("gl_vendor", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));
            report.addProperty("gl_renderer", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            report.addProperty("gl_version", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
        }
        String filename = String.format(Locale.ROOT, "frame-%04d.png", slot);
        JsonObject frame = new JsonObject();
        frame.addProperty("slot", slot);
        frame.addProperty("scheduled_path_seconds", slot / (double)config.fps());
        frame.addProperty("observed_path_seconds", pathSeconds);
        frame.addProperty("session_render_frame", renderedFrames);
        frame.addProperty("source_gpu_frame_id", GpuTimers.frameId());
        frame.addProperty("source_iris_frame_counter", net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt());
        frame.addProperty("source_iris_frame_time_counter", net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getFrameTimeCounter());
        frame.addProperty("source_iris_frame_time", net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getLastFrameTime());
        frame.addProperty("tick_delta", partial);
        frame.addProperty("motion_tick", motionTick);
        frame.addProperty("captured_at_utc", Instant.now().toString());
        frame.addProperty("motion_wall_seconds", seconds(System.nanoTime(), motionStarted));
        frame.addProperty("camera_x", position.x);
        frame.addProperty("camera_y", position.y);
        frame.addProperty("camera_z", position.z);
        frame.addProperty("camera_yaw", camera.getYRot());
        frame.addProperty("camera_pitch", camera.getXRot());
        frame.add("player_pose", GSON.toJsonTree(Pose.of(client)));
        frame.addProperty("framebuffer_width", target.width);
        frame.addProperty("framebuffer_height", target.height);
        frame.addProperty("shader_pack", Iris.getCurrentPackName());
        frame.addProperty("shader_archive_sha256", captureInputs.get("actual_shader_sha256").getAsString());
        frame.addProperty("effective_shader_options_sha256", captureInputs.get("effective_shader_options_sha256").getAsString());
        frame.addProperty("input_fingerprint_policy", "Archive/options hash from capture boundary; loaded options/config/control checked each second and at end");
        frame.addProperty("day_time", client.level.getDayTime());
        frame.addProperty("rain", client.level.getRainLevel(partial));
        frame.addProperty("thunder", client.level.getThunderLevel(partial));
        frame.addProperty("file", filename);
        frame.addProperty("maximum_in_flight_images", config.maximumInFlightImages());
        frame.add("write_state_before_readback", GSON.toJsonTree(writes.snapshot()));
        addPngEncoding(frame);
        frame.addProperty("status", "write_pending");
        frames.add(frame);
        var observedWindow = qualityWindow(client);
        frame.add("window_state", GSON.toJsonTree(observedWindow));
        frame.addProperty("hide_gui", client.options.hideGui);
        if (!observedWindow.availability().equals("available") || !observedWindow.focused().equals("true")
                || !observedWindow.visible().equals("true") || !observedWindow.iconified().equals("false")
                || !client.options.hideGui || observedWindow.framebuffer_width() != target.width
                || observedWindow.framebuffer_height() != target.height) {
            frame.addProperty("status", "window_contract_rejected");
            throw new IllegalStateException("Capture requires a focused visible native framebuffer and hidden GUI");
        }
        JsonObject cameraContext = WaterSceneSurvey.observeCameraSnapshot(client);
        frame.add("camera_context", cameraContext);
        try { requireCameraContract(cameraContext); }
        catch (IllegalStateException exception) {
            frame.addProperty("status", "camera_contract_rejected");
            frame.addProperty("failure", exception.toString());
            throw exception;
        }
        // OpenGL readback occurs exactly here, after the frame's GUI pass.
        writes.reserve(slot);
        frame.addProperty("image_write_slot_id", slot);
        long readbackStarted = System.nanoTime();
        final com.mojang.blaze3d.platform.NativeImage image;
        try { image = Screenshot.takeScreenshot(target); }
        catch (Exception exception) {
            writes.rejectedBeforeWrite(slot);
            frame.addProperty("status", "readback_failed");
            frame.addProperty("failure", exception.toString());
            throw exception;
        }
        frame.addProperty("readback_wall_seconds", seconds(System.nanoTime(), readbackStarted));
        frame.addProperty("native_image_format", image.format().name());
        if (image.format() != com.mojang.blaze3d.platform.NativeImage.Format.RGBA) {
            image.close();
            writes.rejectedBeforeWrite(slot);
            frame.addProperty("status", "unsupported_native_image_format");
            throw new IllegalStateException("Pinned screenshot RGBA format required by lossless PNG writer");
        }
        long bytesBeforeWrite = completedBytes;
        long queuedAt = System.nanoTime();
        writes.queued(slot);
        try {
            writer.execute(() -> {
                String digest = null, error = null;
                long bytes = 0;
                long startedWrite = System.nanoTime();
                try (image) {
                    writes.beginWrite(slot);
                    writes.observeExecutorQueue(writer.getQueue().size());
                    Path path = output.resolve(filename);
                    long encodedBytes = FastPngWriter.write(path, image.getWidth(), image.getHeight(), (x, y) -> {
                        int nativePixel = image.getPixelRGBA(x, y);
                        return LITTLE_ENDIAN_PIXELS ? nativePixel : Integer.reverseBytes(nativePixel);
                    }, config.pngCompressionLevel(), Math.min(MAX_PNG_BYTES, MAX_SEQUENCE_BYTES - bytesBeforeWrite));
                    bytes = Files.size(path);
                    if (bytes != encodedBytes || bytes > MAX_PNG_BYTES || bytesBeforeWrite + bytes > MAX_SEQUENCE_BYTES) throw new IllegalStateException("PNG size mismatch or 64 MiB/12 GiB disk bound exceeded");
                    digest = WorkloadInputs.hash(path);
                } catch (Throwable exception) { error = exception.toString(); }
                // Image is closed by the resource block on both encoding success and error.
                writes.imageReleased(slot);
                long writeFinished = System.nanoTime();
                String completedHash = digest, completedError = error;
                long writtenBytes = bytes;
                double writeSeconds = seconds(System.nanoTime(), startedWrite);
                client.execute(() -> {
                    long callbackAt = System.nanoTime();
                    try { writes.callback(slot); }
                    catch (IllegalStateException exception) {
                        frame.addProperty("callback_protocol_failure", exception.toString());
                        fail(client, exception);
                        return;
                    }
                    if (finalized) return; // Defensive: normal finalization requires every callback first.
                    frame.addProperty("callback_slot_id", slot);
                    frame.addProperty("callback_completed_at_utc", Instant.now().toString());
                    frame.addProperty("writer_queue_wait_seconds", seconds(startedWrite, queuedAt));
                    frame.addProperty("writer_callback_delay_seconds", seconds(callbackAt, writeFinished));
                    frame.addProperty("image_lease_wall_seconds", seconds(callbackAt, readbackStarted));
                    frame.add("write_state_after_callback", GSON.toJsonTree(writes.snapshot()));
                    frame.addProperty("write_wall_seconds", writeSeconds);
                    frame.addProperty("bytes", writtenBytes);
                    frame.addProperty("status", completedError == null ? "written" : "write_failed");
                    if (completedHash != null) frame.addProperty("png_sha256", completedHash);
                    if (completedError != null) {
                        frame.addProperty("failure", completedError);
                        fail(client, new IllegalStateException("PNG writer failed: " + completedError));
                    } else {
                        completedWrites++;
                        completedBytes += writtenBytes;
                        report.addProperty("png_callback_successes", completedWrites);
                        report.addProperty("skipped_capture_slots", skippedFrames);
                        writeManifest(draining ? (drainTimeoutReported ? "failed_waiting_for_png_callbacks" : "waiting_for_png_callback") : "capturing");
                    }
                });
            });
        } catch (RuntimeException exception) {
            image.close();
            writes.rejectedBeforeWrite(slot);
            frame.addProperty("status", "writer_rejected");
            throw exception;
        }
        // Submission succeeded: only the worker may now close this image.
        // Keep later metadata failures outside the submission-rejection cleanup.
        writes.observeExecutorQueue(writer.getQueue().size());
        frame.add("write_state_after_submission", GSON.toJsonTree(writes.snapshot()));
        frame.addProperty("writer_executor_queue_size_after_submission", writer.getQueue().size());
    }

    private void skipped(int slot, String reason) {
        JsonObject frame = new JsonObject();
        frame.addProperty("slot", slot);
        frame.addProperty("scheduled_path_seconds", slot / (double)config.fps());
        frame.addProperty("maximum_in_flight_images", config.maximumInFlightImages());
        frame.add("write_state_at_skip", GSON.toJsonTree(writes.snapshot()));
        addPngEncoding(frame);
        frame.addProperty("status", reason);
        frames.add(frame);
        skippedFrames++;
    }

    private void addPngEncoding(JsonObject target) {
        target.addProperty("png_encoding", FastPngWriter.ENCODING);
        target.addProperty("png_content_flush", "Writer-thread FileChannel.force(false) before successful write; no directory metadata durability claim");
        target.addProperty("png_compression_level", config.pngCompressionLevel());
        target.addProperty("png_bit_depth", 8);
        target.addProperty("png_colour_type", 6);
    }

    private void requireCameraContract(JsonObject snapshot) {
        var cameraElement = snapshot.get("camera");
        JsonObject camera = cameraElement != null && cameraElement.isJsonObject()
                ? cameraElement.getAsJsonObject() : new JsonObject();
        if (config.expectedCameraFluid() != null) {
            var fog = camera.get("fog_type");
            if (fog == null || !fog.isJsonPrimitive() || !fog.getAsJsonPrimitive().isString()) {
                throw new IllegalStateException("Expected camera fluid " + config.expectedCameraFluid() + " but guarded FogType is missing/unknown");
            }
            if (!config.expectedCameraFluid().equals(fog.getAsString())) {
                throw new IllegalStateException("Expected camera fluid " + config.expectedCameraFluid() + " but observed " + fog.getAsString());
            }
        }
        if (config.rejectFullBlockCamera()) {
            var collision = camera.get("full_collision_block");
            if (collision != null && collision.isJsonPrimitive() && collision.getAsJsonPrimitive().isBoolean()
                    && collision.getAsBoolean()) {
                throw new IllegalStateException("Actual camera is in an observed full-collision block cell");
            }
        }
    }

    private void requireOwner(Minecraft client) throws Exception {
        if (!client.gameDirectory.toPath().toRealPath().equals(gameDirectory) || client.player == null || client.level == null
                || !owner.equals(client.player.getUUID()) || client.getSingleplayerServer() == null
                || !benchmarkOwned && !client.player.isSpectator() || client.getCameraEntity() != client.player) throw new IllegalStateException("Owned local camera changed");
        if (client.screen != null || client.isPaused()) throw new IllegalStateException("Capture interrupted by screen/pause");
        if (!client.options.getCameraType().isFirstPerson()) throw new IllegalStateException("Capture requires first-person main camera");
        if (client.level != worldReference || Iris.getPipelineManager().getPipelineNullable() != pipelineReference) throw new IllegalStateException("World/pipeline reloaded during quality session");
    }

    private void validateFingerprint(Minecraft client) throws Exception {
        if (!sourceHash.equals(safeHash(source))) throw new IllegalStateException("Request source changed after activation");
        JsonObject current = inputs.inputState();
        current.addProperty("hide_gui", client.options.hideGui);
        requirePipeline(current);
        JsonObject expected = captureInputs == null ? initialInputs : captureInputs;
        // Camera moves intentionally; rain/thunder may settle during warmup.
        for (String key : List.of("shader_pack", "effective_shader_options_sha256", "framebuffer_width", "framebuffer_height",
                "requested_chunks", "effective_chunks", "dimension", "day_time", "control_sha256", "hide_gui",
                "config/shaderlod-client.toml_sha256", "config/iris.properties_sha256", "config/DistantHorizons.toml_sha256", "terrain_active")) {
            if (!java.util.Objects.equals(expected.get(key), current.get(key))) throw new IllegalStateException("Capture input changed: " + key);
        }
        if (captureInputs != null) for (String key : List.of("rain", "thunder")) {
            if (Math.abs(expected.get(key).getAsDouble() - current.get(key).getAsDouble()) > 1e-6) throw new IllegalStateException("Settled weather changed: " + key);
        }
        if (expected.has("lod_settings") && !expected.get("lod_settings").equals(current.get("lod_settings"))) throw new IllegalStateException("ShaderLOD settings changed during quality session");
    }

    private static void requirePipeline(JsonObject state) {
        if (!state.get("shaders_in_use").getAsBoolean() || state.get("fallback").getAsBoolean()) throw new IllegalStateException("Rendered Iris shader pipeline required");
    }

    private void requestWorldCheck(Minecraft client, long now) {
        var server = client.getSingleplayerServer();
        var dimension = client.level.dimension();
        var uuid = client.player.getUUID();
        worldCheck = server.submit(() -> {
            var level = server.getLevel(dimension);
            var player = server.getPlayerList().getPlayer(uuid);
            if (level == null || player == null) throw new IllegalStateException("Owned server scene disappeared");
            return new World(dimension.location().toString(), level.getDayTime(),
                    !level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT),
                    !level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE), player.isSpectator(),
                    server.tickRateManager().isFrozen(),player.gameMode.getGameModeForPlayer().getName());
        });
        lastWorldCheck = now;
    }

    private static void apply(Minecraft client, Pose pose) {
        client.player.setDeltaMovement(Vec3.ZERO);
        client.player.setPos(pose.x(), pose.y(), pose.z());
        client.player.setYRot((float)pose.yaw());
        client.player.setXRot((float)pose.pitch());
        client.player.setYHeadRot((float)pose.yaw());
        // Preserve Minecraft's previous-tick position/rotation fields for interpolation.
    }

    private void fail(Minecraft client, Exception exception) {
        if (failure == null) failure = exception.toString();
        LogUtils.getLogger().error("Camera quality session failed: {}", output, exception);
        if (!draining) beginDrain();
        if (writes.drained()) finish(client);
    }

    private void beginDrain() {
        draining = true;
        drainingAt = System.nanoTime();
        writeManifest("waiting_for_png_callback");
    }

    private void finish(Minecraft client) {
        if (finalized) return;
        if (!writes.drained()) throw new IllegalStateException("Cannot finalize or close a client with unsettled image leases");
        finalized = true;
        writer.shutdown();
        report.addProperty("rendered_frames_observed", renderedFrames);
        report.addProperty("requested_capture_slots", config.slotCount());
        report.addProperty("png_callback_successes", completedWrites);
        report.addProperty("png_bytes_successfully_written", completedBytes);
        report.addProperty("skipped_capture_slots", skippedFrames);
        report.addProperty("writer_pending_at_finalization", !writes.drained());
        boolean unchanged = sourceHash.equals(safeHash(source));
        report.addProperty("request_unchanged_at_end", unchanged);
        if (!unchanged && failure == null) failure = "Request source changed before finalization";
        try {
            var endInputs = qualityInputState();
            report.add("input_state_end", endInputs);
            report.add("loaded_artifact_evidence_end", LoadedArtifactEvidence.capture());
            var artifacts = artifactIdentities();
            report.add("loaded_mod_artifacts_at_end", artifacts);
            if (!report.get("loaded_mod_artifacts").equals(artifacts) && failure == null) failure = "Loaded artifact files changed during quality session";
        } catch (Exception exception) {
            if (failure == null) failure = "End artifact verification failed: " + exception;
        }
        if (failure != null) report.addProperty("failure", failure);
        String status = failure != null ? "failed" : skippedFrames != 0 || completedWrites != config.slotCount() ? "incomplete_capture" : "complete";
        writeManifest(status);
        if (owner != null && client.player != null && owner.equals(client.player.getUUID())) {
            if (closeOwnedClient && client.gameDirectory.toPath().toAbsolutePath().normalize().equals(gameDirectory)) client.execute(client::stop);
            else {
                apply(client, originalPose);
                client.player.setOldPosAndRot();
            }
        }
        LogUtils.getLogger().info("Camera quality finished: status={}, images={}, skipped={}, output={}", status, completedWrites, skippedFrames, output);
    }

    private JsonObject qualityInputState() throws Exception {
        var state = inputs.benchmarkInputState();
        state.add("configuration_files", ConfigurationFilesEvidence.capture(gameDirectory));
        state.add("window_state", GSON.toJsonTree(qualityWindow(Minecraft.getInstance())));
        if (ModList.get().isLoaded("shaderlod")) {
            state.getAsJsonObject("loaded_semantic_config").add("effects",
                    GSON.toJsonTree(com.pp.shaderlod.ShaderLodEffectConfig.snapshot()));
        }
        return state;
    }

    private static com.pp.shaderlod.dev.timing.WindowStateTracker.State qualityWindow(Minecraft client) {
        var window = client.getWindow();
        return com.pp.shaderlod.dev.timing.WindowStateTracker.readWindow(window.getWindow(),
                window.getScreenWidth(), window.getScreenHeight(), window.getWidth(), window.getHeight(),
                new com.pp.shaderlod.dev.timing.WindowStateTracker.Pacing(client.options.pauseOnLostFocus,
                        window.isFullscreen(), client.options.framerateLimit().get(), client.options.enableVsync().get()));
    }

    private void writeManifest(String status) {
        writes.observeExecutorQueue(writer.getQueue().size());
        report.add("capture_write_tracker", GSON.toJsonTree(writes.snapshot()));
        report.addProperty("writer_executor_queue_size", writer.getQueue().size());
        report.addProperty("status", status);
        report.addProperty("written_at_utc", Instant.now().toString());
        try {
            Path temporary = output.resolve("manifest.json.tmp");
            Files.writeString(temporary, GSON.toJson(report));
            Files.move(temporary, output.resolve("manifest.json"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) { LogUtils.getLogger().error("Cannot retain camera quality manifest: {}", output, exception); }
    }

    private static String safeHash(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > 65536) return "unavailable";
            byte[] bytes;
            try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(65537); }
            return bytes.length > 65536 ? "unavailable" : hash(bytes);
        }
        catch (Exception exception) { return "unavailable"; }
    }

    private static JsonObject artifactIdentities() throws Exception {
        var result = new JsonObject();
        for (var modFile : ModList.get().getModFiles()) {
            Path path = modFile.getFile().getFilePath();
            var item = new JsonObject();
            item.addProperty("path", path.toAbsolutePath().toString());
            if (Files.isRegularFile(path)) item.addProperty("sha256", WorkloadInputs.hash(path));
            else if (Files.isDirectory(path)) {
                var files = new java.util.TreeMap<String, String>();
                long bytes = 0;
                try (var stream = Files.walk(path)) {
                    var iterator = stream.filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)).sorted().iterator();
                    while (iterator.hasNext()) {
                        Path file = iterator.next();
                        bytes += Files.size(file);
                        if (files.size() >= 5000 || bytes > 32L * 1024 * 1024) throw new IllegalStateException("Development artifact exceeds fingerprint bound");
                        files.put(path.relativize(file).toString(), WorkloadInputs.hash(file));
                    }
                }
                item.addProperty("kind", "development-directory; regular files only, transformed runtime classes unverified");
                item.add("files_sha256", GSON.toJsonTree(files));
            } else throw new IllegalStateException("Loaded artifact unavailable: " + path);
            result.add(modFile.getMods().stream().map(mod -> mod.getModId()).sorted().collect(java.util.stream.Collectors.joining(",")), item);
        }
        return result;
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static double seconds(long now, long then) { return (now - then) * 1e-9; }

    private boolean acceptableWorld(World value) {
        return benchmarkOwned ? value.tickFrozen() : value.daylightFrozen() && value.weatherFrozen() && value.spectator();
    }
    private record World(String dimension, long dayTime, boolean daylightFrozen, boolean weatherFrozen, boolean spectator,
                         boolean tickFrozen, String gameMode) {}
    private record Pose(double x, double y, double z, double yaw, double pitch) {
        static Pose of(Minecraft client) { var p = client.player; return new Pose(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()); }
        boolean near(Pose other, double positionTolerance, double angleTolerance) {
            return distance(other) <= positionTolerance && Math.abs(angle(other.yaw - yaw)) <= angleTolerance && Math.abs(pitch - other.pitch) <= angleTolerance;
        }
        double distance(Pose other) { return Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2)); }
        Pose lerp(Pose other, double t) { return new Pose(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t, yaw + (other.yaw - yaw) * t, pitch + (other.pitch - pitch) * t); }
    }
    private record Waypoint(double seconds, Pose pose) {}
    private record Config(String label, double warmupSeconds, int fps, int pngCompressionLevel, int maximumInFlightImages,
                          String expectedCameraFluid, boolean rejectFullBlockCamera, List<Waypoint> waypoints) {
        static Config parse(String text) {
            JsonObject value = JsonParser.parseString(text).getAsJsonObject();
            if (number(value, "schema", 1, 1) != 1) throw new IllegalArgumentException("schema must be 1");
            if (!value.has("label") || !value.get("label").isJsonPrimitive() || !value.get("label").getAsJsonPrimitive().isString()) throw new IllegalArgumentException("String label required");
            String label = value.get("label").getAsString();
            if (!label.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("label must be 1-64 ASCII filename characters");
            double warmup = value.has("warmupSeconds") ? number(value, "warmupSeconds", 30, 120) : 30;
            double rate = value.has("fps") ? number(value, "fps", 1, 10) : 10;
            if (rate != Math.floor(rate)) throw new IllegalArgumentException("fps must be an integer");
            double compression = value.has("pngCompressionLevel") ? number(value, "pngCompressionLevel", 0, 1) : 0;
            if (compression != Math.floor(compression)) throw new IllegalArgumentException("pngCompressionLevel must be an integer 0 or 1");
            double inFlight = value.has("maximumInFlightImages") ? number(value, "maximumInFlightImages", 1, 2) : 1;
            if (inFlight != Math.floor(inFlight)) throw new IllegalArgumentException("maximumInFlightImages must be an integer 1 or 2");
            String expectedCameraFluid = null;
            if (value.has("expectedCameraFluid")) {
                var expected = value.get("expectedCameraFluid");
                if (!expected.isJsonPrimitive() || !expected.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("expectedCameraFluid must be a string NONE, WATER or LAVA");
                expectedCameraFluid = expected.getAsString();
                if (!List.of("NONE", "WATER", "LAVA").contains(expectedCameraFluid)) throw new IllegalArgumentException("expectedCameraFluid must be NONE, WATER or LAVA");
            }
            boolean rejectFullBlockCamera = false;
            if (value.has("rejectFullBlockCamera")) {
                var reject = value.get("rejectFullBlockCamera");
                if (!reject.isJsonPrimitive() || !reject.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("rejectFullBlockCamera must be a JSON boolean");
                rejectFullBlockCamera = reject.getAsBoolean();
            }
            JsonArray points = value.getAsJsonArray("waypoints");
            if (points.size() < 2 || points.size() > 32) throw new IllegalArgumentException("Need 2-32 waypoints");
            List<Waypoint> result = new ArrayList<>();
            double totalDistance = 0;
            for (var element : points) {
                JsonObject point = element.getAsJsonObject();
                double time = number(point, "seconds", 0, 30);
                Pose raw = new Pose(number(point, "x", -29999000, 29999000), number(point, "y", -2048, 2048),
                        number(point, "z", -29999000, 29999000), number(point, "yaw", -3600, 3600), number(point, "pitch", -90, 90));
                if (result.isEmpty()) {
                    if (time != 0) throw new IllegalArgumentException("First waypoint must be at seconds 0");
                } else {
                    Waypoint previous = result.getLast();
                    double duration = time - previous.seconds();
                    if (duration < 0.25) throw new IllegalArgumentException("Waypoint segments must last >= 0.25 seconds");
                    // Unwrap yaw once in immutable config; normal partial-tick interpolation cannot spin at +/-180.
                    raw = new Pose(raw.x(), raw.y(), raw.z(), previous.pose().yaw() + angle(raw.yaw() - previous.pose().yaw()), raw.pitch());
                    double distance = previous.pose().distance(raw);
                    totalDistance += distance;
                    // Cubic ease has peak speed 1.5 times its segment's average speed.
                    if (1.5 * distance / duration > 32 || 1.5 * Math.abs(raw.yaw() - previous.pose().yaw()) / duration > 90 || 1.5 * Math.abs(raw.pitch() - previous.pose().pitch()) / duration > 90) throw new IllegalArgumentException("Waypoint exceeds peak 32 blocks/s or 90 degrees/s");
                }
                result.add(new Waypoint(time, raw));
            }
            if (result.getLast().seconds() < 20 || totalDistance > 512) throw new IllegalArgumentException("Sequence must last 20-30 seconds and travel <= 512 blocks");
            double slots = result.getLast().seconds() * rate;
            if (Math.abs(slots - Math.rint(slots)) > 1e-6) throw new IllegalArgumentException("Duration must end on a capture slot");
            return new Config(label, warmup, (int)rate, (int)compression, (int)inFlight, expectedCameraFluid, rejectFullBlockCamera, List.copyOf(result));
        }
        double duration() { return waypoints.getLast().seconds(); }
        int slotCount() { return (int)Math.round(duration() * fps) + 1; }
        Pose poseAt(double time) {
            for (int i = 1; i < waypoints.size(); i++) {
                Waypoint next = waypoints.get(i), previous = waypoints.get(i - 1);
                if (time <= next.seconds()) {
                    double t = Math.clamp((time - previous.seconds()) / (next.seconds() - previous.seconds()), 0, 1);
                    return previous.pose().lerp(next.pose(), t * t * (3 - 2 * t));
                }
            }
            return waypoints.getLast().pose();
        }
    }
    private static double number(JsonObject value, String key, double low, double high) {
        var element = value.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Numeric field required: " + key);
        double number = element.getAsDouble();
        if (!Double.isFinite(number) || number < low || number > high) throw new IllegalArgumentException("Out of bounds: " + key);
        return number;
    }
    private static double angle(double degrees) { return degrees - 360 * Math.floor((degrees + 180) / 360); }
}
