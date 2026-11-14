package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pp.shaderlod.storage.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

final class BenchmarkControl {
  private static final int UNLIMITED_FRAMERATE = 260;
  private final Path directory;
  private final BenchmarkWorld world;
  private java.util.concurrent.CompletableFuture<java.util.Map<String, Object>> worldAction;
  private java.util.List<Double> pendingPose;
  private boolean closing;
  private boolean idleClosing;
  private long lastActivity = System.nanoTime();
  private Boolean originalFullscreen;
  private java.util.Optional<com.mojang.blaze3d.platform.VideoMode> originalVideoMode;
  private final Gson gson = new Gson();
  private long sequence;
  private long started;
  private long duration;
  private int captureWidth;
  private int captureHeight;
  private boolean contextLost;
  private boolean durableChunks = true;
  private String captureDimension;
  private long activeWorldFrames;
  private long renderedFrames;
  private long captureBufferRenders;
  private boolean observingFrame;
  private boolean worldRendered;
  private java.util.List<String> selectedModules = java.util.List.of();
  private FrameCapture capture;
  private CaptureSpool spool;
  private com.pp.shaderlod.dev.measurement.StageProfiler stages;
  private com.pp.shaderlod.dev.measurement.StageProfiler passes;
  private Integer originalFramerateLimit;
  private Boolean originalVsync;
  private long callbacks;
  private int spooled;
  private long spooledAt;
  private String fault;
  private long reconciledFailure = -1;
  private final com.pp.shaderlod.integration.IrisSelection backend =
      new com.pp.shaderlod.integration.IrisSelection();
  private final com.pp.shaderlod.integration.ShaderController modules =
      new com.pp.shaderlod.integration.ShaderController();
  private com.pp.shaderlod.engine.SelectionTransaction selection;
  private boolean moduleSelection;
  private boolean pendingSelect;
  private FunctionalProbe functional;
  private long selectionStarted;
  private CameraRoute route;
  private java.util.List<Double> originalPose;
  private java.util.List<Double> scenarioOriginalPose;
  private int delayMillis;
  private long delayExecuted;
  private long checkpointTime;
  private long routeTicks;
  private long viewCheckpointTime;
  private long qualityCadence;
  private CaptureCadence qualityClock;
  private java.util.List<Double> latestCamera = java.util.List.of();
  private long cameraObserved;
  private QualityCapture quality;
  private final java.util.List<java.util.Map<String, Object>> viewCheckpoints =
      new java.util.ArrayList<>();
  private final java.util.List<java.util.Map<String, Object>> checkpoints =
      new java.util.ArrayList<>();

  BenchmarkControl(Path directory) {
    this.directory = directory;
    world = new BenchmarkWorld(directory);
    RenderGroups.observe(owner -> {
      if (passes != null) {
        passes.boundary(owner);
      }
    });
  }

  void frame(long timestamp, long generation) {
    RenderGroups.reset();
    DhSections.reset();
    renderedFrames++;
    if (capture != null) {
      contextLost |= observingFrame && !worldRendered;
      observingFrame = true;
      worldRendered = false;
      var client = Minecraft.getInstance();
      contextLost |=
          client.level == null
              || client.player == null
              || client.screen != null
              || client.getOverlay() != null
              || !client.isWindowActive()
              || client.getMainRenderTarget().width != captureWidth
              || client.getMainRenderTarget().height != captureHeight
              || !String.valueOf(net.irisshaders.iris.Iris.getCurrentDimension())
                  .equals(captureDimension);
      if (route != null && timestamp - checkpointTime >= 250000000) {
        checkpoints.add(
            java.util.Map.of(
                "elapsed_ns",
                timestamp - started,
                "pose",
                currentPose(),
                "client_ticks",
                routeTicks));
        checkpointTime = timestamp;
      }
      capture.observe(timestamp, generation);
      if (durableChunks && timestamp - spooledAt >= 1000000000) {
        spool.offer(capture.since(spooled));
        spooled = capture.count();
        spooledAt = timestamp;
      }
      if (stages != null) {
        stages.begin(generation, ++callbacks);
        passes.begin(generation, callbacks);
      }
      if (delayMillis != 0) {
        long delayStart = System.nanoTime();
        long delayEnd = delayStart + delayMillis * 1000000L;
        long remaining;
        while ((remaining = delayEnd - System.nanoTime()) > 0) {
          java.util.concurrent.locks.LockSupport.parkNanos(remaining);
        }
        delayExecuted += System.nanoTime() - delayStart;
      }
    }
  }

  void stage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
    boolean shadow = net.irisshaders.iris.api.v0.IrisApi.getInstance().isRenderingShadowPass();
    if (capture != null
        && !shadow
        && event
            .getStage()
            .equals(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL)
        && net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()
        && !net.irisshaders.iris.Iris.isFallback()) {
      activeWorldFrames++;
      worldRendered = true;
    }
    if (capture != null
        && route != null
        && !shadow
        && event
            .getStage()
            .equals(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY)) {
      var camera = event.getCamera();
      var point = camera.getPosition();
      cameraObserved = System.nanoTime();
      latestCamera =
          java.util.List.of(
              point.x, point.y, point.z, (double) camera.getYRot(), (double) camera.getXRot());
      if (cameraObserved - viewCheckpointTime >= 250000000) {
        viewCheckpointTime = System.nanoTime();
        viewCheckpoints.add(
            java.util.Map.of(
                "elapsed_ns",
                viewCheckpointTime - started,
                "client_ticks",
                routeTicks,
                "player_pose",
                currentPose(),
                "camera_pose",
                java.util.List.of(
                    point.x,
                    point.y,
                    point.z,
                    (double) camera.getYRot(),
                    (double) camera.getXRot()),
                "eye_height",
                Minecraft.getInstance().player.getEyeHeight(),
                "first_person",
                Minecraft.getInstance().options.getCameraType().isFirstPerson()));
      }
    }
    if (stages != null) {
      stages.boundary((shadow ? "shadow/" : "main/") + event.getStage());
    }
  }

  void endFrame() {
    if (stages != null) {
      stages.end();
      passes.end();
    }
    if (capture != null && qualityClock != null && qualityClock.due(System.nanoTime())) {
      long readbackStart = System.nanoTime();
      var image =
          net.minecraft.client.Screenshot.takeScreenshot(
              Minecraft.getInstance().getMainRenderTarget());
      long readbackEnd = System.nanoTime();
      quality.offer(
          "motion-" + sequence + "-" + readbackStart + ".png",
          image,
          java.util.Map.of(
              "elapsed_ns",
              readbackStart - started,
              "readback_ns",
              readbackEnd - readbackStart,
              "client_ticks",
              routeTicks,
              "pose",
              currentPose(),
              "camera_pose",
              latestCamera,
              "camera_observed_ns",
              cameraObserved,
              "frame_start_ns",
              capture.latestTimestamp(),
              "first_person",
              Minecraft.getInstance().options.getCameraType().isFirstPerson()));
    }
  }

  void tick(ClientTickEvent.Post event) {
    try {
      if (Minecraft.getInstance().level == null) {
        lastActivity = System.nanoTime();
      }
      if (fault != null) {
        Path recovery = directory.resolve("command-" + (sequence + 1) + ".json");
        if (!Files.isRegularFile(recovery) || Files.size(recovery) > 4096) {
          return;
        }
        var close =
            ControlRequest.decode(
                gson.fromJson(Files.readString(recovery), JsonObject.class), sequence + 1);
        if (!close.action().equals("close") && !close.action().equals("recover")) {
          return;
        }
        reconciledFailure = sequence;
        if (originalPose != null) {
          position(originalPose);
        }
        capture = null;
        functional = null;
        if (quality != null) {
          quality.finish();
          quality = null;
        }
        if (spool != null) {
          spool.finish();
        }
        if (stages != null) {
          stages.finish();
          stages = null;
          passes.finish();
          passes = null;
        }
        pendingSelect = false;
        worldAction = null;
        fault = null;
      }
      modules.tick();
      if (functional != null) {
        functional.tick(event);
        if (functional.done()) {
          functional = null;
          publish(state());
        }
        return;
      }
      if (worldAction != null) {
        if (!worldAction.isDone()) {
          return;
        }
        var change = worldAction.join();
        world.applyClient(change);
        if (pendingPose != null) {
          position(pendingPose);
          pendingPose = null;
        }
        worldAction = null;
        var record = state();
        record.put("world", change);
        if (idleClosing) {
          AtomicFiles.publish(
              directory.resolve("idle-close.json"),
              gson.toJson(record).getBytes(StandardCharsets.UTF_8));
        } else {
          publish(record);
        }
        lastActivity = System.nanoTime();
        if (closing) {
          if (originalVideoMode != null) {
            Minecraft.getInstance().getWindow().setPreferredFullscreenVideoMode(originalVideoMode);
            Minecraft.getInstance().getWindow().changeFullscreenVideoMode();
          }
          if (originalFullscreen != null
              && Minecraft.getInstance().getWindow().isFullscreen() != originalFullscreen) {
            Minecraft.getInstance().getWindow().toggleFullScreen();
          }
          if (net.neoforged.fml.ModList.get().isLoaded("distanthorizons")) {
            DhControl.restore();
          }
          if (originalFramerateLimit != null) {
            Minecraft.getInstance().options.framerateLimit().set(originalFramerateLimit);
            Minecraft.getInstance().options.enableVsync().set(originalVsync);
          }
          Minecraft.getInstance().stop();
        }
        return;
      }
      if (pendingSelect) {
        if (System.nanoTime() - selectionStarted > 60000000000L) {
          throw new IOException("Selection did not render before deadline");
        }
        if (!backend.hasSubmittedFrame() || !Boolean.TRUE.equals(state().get("ready"))) {
          return;
        }
        if (moduleSelection) {
          if (!modules.status().equals("ACTIVE")) {
            return;
          }
        } else {
          selection.confirmRendered();
        }
        pendingSelect = false;
        publish(state());
        return;
      }
      if (capture != null) {
        if (route != null) {
          routeTicks++;
          var pose = route.at(routeTicks / (duration / 50000000.0));
          var client = Minecraft.getInstance();
          if (client.level == null
              || !client.level.hasChunk(
                  ((int) Math.floor(pose.get(0))) >> 4, ((int) Math.floor(pose.get(2))) >> 4)) {
            throw new IOException("Route left admitted loaded region");
          }
          position(pose, false);
        }
        if (System.nanoTime() - started >= duration) {
          if (durableChunks) {
            spool.offer(capture.since(spooled));
          }
          var result = capture.finish();
          capture = null;
          var spoolStatus = spool.finish();
          var record = state();
          record.put("capture", result);
          record.put("spool", spoolStatus);
          record.put("durable_chunks", durableChunks);
          if (stages != null) {
            record.put("stage_profile", stages.finish());
            stages = null;
            record.put("pass_profile", passes.finish());
            passes = null;
          }
          record.put("checkpoints", java.util.List.copyOf(checkpoints));
          record.put("view_checkpoints", java.util.List.copyOf(viewCheckpoints));
          record.put("progression_clock", "client-simulation-ticks-20hz");
          record.put("elapsed_client_ticks", routeTicks);
          record.put("expected_client_ticks", duration / 50000000.0);
          record.put("delay_ms", delayMillis);
          record.put("context_lost", contextLost);
          record.put("active_world_frames", activeWorldFrames);
          record.put("dh_buffer_renders", dhBufferRenders() - captureBufferRenders);
          record.put("executed_delay_ns", delayExecuted);
          record.put("quality_cadence_ns", qualityCadence);
          record.put("quality_skipped_slots", qualityClock == null ? 0 : qualityClock.skipped());
          if (quality != null) {
            var qualityResult = quality.finish();
            record.put("quality_frames", qualityResult.get("frames"));
            record.put("quality_status", qualityResult);
            quality = null;
          }
          if (originalPose != null) {
            position(originalPose);
          }
          route = null;
          originalPose = null;
          delayMillis = 0;
          record.put("elapsed_ns", System.nanoTime() - started);
          record.put(
              "valid",
              !result.overflow()
                  && !contextLost
                  && worldRendered
                  && activeWorldFrames >= result.frames().size()
                  && !result.generationChanged()
                  && !result.frames().isEmpty()
                  && Boolean.TRUE.equals(spoolStatus.get("complete")));
          publish(record);
        }
        return;
      }
      if (!closing
          && Minecraft.getInstance().level != null
          && System.nanoTime() - lastActivity > 120000000000L) {
        restore();
        closing = true;
        idleClosing = true;
        restorePose();
        worldAction = world.set(true);
        return;
      }
      Path command = directory.resolve("command-" + (sequence + 1) + ".json");
      if (!Files.isRegularFile(command)) {
        return;
      }
      if (Files.size(command) > 4096) {
        throw new IOException("Command exceeds size limit");
      }
      var request =
          ControlRequest.decode(
              gson.fromJson(Files.readString(command), JsonObject.class), sequence + 1);
      sequence = request.sequence();
      lastActivity = System.nanoTime();
      String action = request.action();
      if (action.equals("status")) {
        publish(state());
      } else if (action.equals("validate")) {
        restore();
        if (!Boolean.TRUE.equals(state().get("ready"))
            || !net.irisshaders.iris.Iris.getCurrentPackName()
                .equals("ComplementaryReimagined_r5.9.3.zip")) {
          throw new IOException("Validation requires a rendered original primary pack selection");
        }
        for (String name :
            java.util.List.of(
                "shaderlod-functional.json",
                "shaderlod-functional.png",
                "shaderlod-functional-identity.png")) {
          if (Files.exists(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve(name))) {
            throw new IOException("Prior functional evidence must be preserved before validation");
          }
        }
        functional = new FunctionalProbe(false);
      } else if (action.equals("prepare")) {
        var client = Minecraft.getInstance();
        if (originalFullscreen == null) {
          originalFullscreen = client.getWindow().isFullscreen();
        }
        if (!client.getWindow().isFullscreen()) {
          client.getWindow().toggleFullScreen();
        }
        if (originalFramerateLimit == null) {
          originalFramerateLimit = client.options.framerateLimit().get();
          originalVsync = client.options.enableVsync().get();
        }
        if (request.payload().has("surface")) {
          var surface = request.payload().getAsJsonArray("surface");
          selectSurface(surface.get(0).getAsInt(), surface.get(1).getAsInt());
        }
        client.options.framerateLimit().set(UNLIMITED_FRAMERATE);
        client.options.enableVsync().set(false);
        if (net.neoforged.fml.ModList.get().isLoaded("distanthorizons")) {
          DhControl.prepare(
              com.pp.shaderlod.dev.config.DevelopmentSettings.dhWitness(),
              com.pp.shaderlod.dev.config.DevelopmentSettings.dhGeneration());
        }
        org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
        client.setScreen(null);
        if (!request.payload().has("day_time")) {
          worldAction = world.set(false);
        } else {
          String weather = request.payload().get("weather").getAsString();
          worldAction =
              world.set(
                  false,
                  request.payload().get("day_time").getAsInt(),
                  !weather.equals("clear"),
                  weather.equals("thunder"));
        }
        if (request.payload().has("pose")) {
          var pose = vector(request.payload().getAsJsonArray("pose"));
          new CameraRoute(pose, pose);
          if (scenarioOriginalPose == null) {
            scenarioOriginalPose = currentPose();
          }
          if (distance(scenarioOriginalPose, pose) > 4096) {
            throw new IOException("Scenario exceeds 64 blocks from owned pose preimage");
          }
          pendingPose = pose;
        }
      } else if (action.equals("screenshot")) {
        var record = state();
        record.putAll(image("quality-" + sequence + ".png"));
        publish(record);
      } else if (action.equals("recover")) {
        restore();
        restorePose();
        closing = false;
        worldAction = world.set(true);
      } else if (action.equals("restore")) {
        restore();
        restorePose();
        publish(state());
      } else if (action.equals("select")) {
        select(request.payload());
      } else if (action.equals("close")) {
        restore();
        restorePose();
        closing = true;
        worldAction = world.set(true);
      } else if (action.equals("capture")) {
        long millis = request.durationMillis();
        if (millis < 100 || millis > 300000 || !Boolean.TRUE.equals(state().get("ready"))) {
          throw new IOException("Ready world and bounded 100–300000 ms capture required");
        }
        checkpoints.clear();
        viewCheckpoints.clear();
        viewCheckpointTime = 0;
        routeTicks = 0;
        route = null;
        originalPose = null;
        delayMillis = 0;
        delayExecuted = 0;
        durableChunks =
            !request.payload().has("durable_chunks")
                || request.payload().get("durable_chunks").getAsBoolean();
        quality = null;
        qualityClock = null;
        latestCamera = java.util.List.of();
        qualityCadence =
            request.payload().has("quality_cadence_ms")
                ? request.payload().get("quality_cadence_ms").getAsLong() * 1000000
                : 0;
        if (qualityCadence != 0
            && (qualityCadence < 100000000 || qualityCadence > 1000000000 || millis > 10000)) {
          throw new IOException(
              "Quality sequence requires 100–1000 ms cadence and at most 10 seconds");
        }
        if (!request.payload().isEmpty()) {
          route =
              new CameraRoute(
                  vector(request.payload().getAsJsonArray("from")),
                  vector(request.payload().getAsJsonArray("to")));
          originalPose = currentPose();
          var anchor = scenarioOriginalPose == null ? originalPose : scenarioOriginalPose;
          if (distance(anchor, route.from()) > 4096 || distance(anchor, route.to()) > 4096) {
            throw new IOException("Route start exceeds 64 blocks from current position");
          }
          delayMillis = request.payload().get("delay_ms").getAsInt();
          if (delayMillis < 0
              || delayMillis > 100
              || !request.payload().get("delay_ms").toString().matches("[0-9]+")) {
            throw new IOException("Invalid test control delay");
          }
          position(route.from());
        }
        duration = millis * 1000000;
        contextLost = false;
        activeWorldFrames = 0;
        captureBufferRenders = dhBufferRenders();
        observingFrame = false;
        worldRendered = false;
        captureWidth = Minecraft.getInstance().getMainRenderTarget().width;
        captureHeight = Minecraft.getInstance().getMainRenderTarget().height;
        captureDimension = String.valueOf(net.irisshaders.iris.Iris.getCurrentDimension());
        checkpointTime = 0;
        AtomicFiles.publish(
            directory.resolve("capture-" + sequence + "-started.json"),
            gson.toJson(state()).getBytes(StandardCharsets.UTF_8));
        boolean diagnostic =
            request.payload().has("diagnostic")
                && request.payload().get("diagnostic").getAsBoolean();
        stages =
            diagnostic ? new com.pp.shaderlod.dev.measurement.StageProfiler("frame-start") : null;
        passes =
            diagnostic
                ? new com.pp.shaderlod.dev.measurement.StageProfiler(RenderGroups.UNGROUPED)
                : null;
        callbacks = 0;
        capture = new FrameCapture(200000);
        if (qualityCadence > 0) {
          quality = new QualityCapture(directory);
        }
        spool = new CaptureSpool(directory, sequence);
        spooled = 0;
        started = System.nanoTime();
        qualityClock = qualityCadence > 0 ? new CaptureCadence(started, qualityCadence) : null;
        spooledAt = started;
      } else {
        throw new IOException("Unsupported command");
      }
    } catch (IOException
        | IllegalArgumentException
        | IllegalStateException
        | com.google.gson.JsonParseException
        | java.util.concurrent.CompletionException failure) {
      fault = failure.getMessage();
      var record = state();
      record.put("failure", fault);
      try {
        AtomicFiles.publish(
            directory.resolve("failure-" + sequence + ".json"),
            gson.toJson(record).getBytes(StandardCharsets.UTF_8));
        if (!Files.exists(directory.resolve("failure.json"))) {
          AtomicFiles.publish(
              directory.resolve("failure.json"),
              gson.toJson(record).getBytes(StandardCharsets.UTF_8));
        }
      } catch (IOException publicationFailure) {
        net.irisshaders.iris.Iris.logger.error(
            "Benchmark failure publication failed", publicationFailure);
      }
    }
  }

  private java.util.Map<String, Object> state() {
    var client = Minecraft.getInstance();
    var record = new LinkedHashMap<String, Object>();
    record.put("schema", "shaderlod-control-result-v1");
    record.put("sequence", sequence);
    record.put("recovered_failure_sequence", reconciledFailure);
    record.put("pack", net.irisshaders.iris.Iris.getCurrentPackName());
    record.put("fallback", net.irisshaders.iris.Iris.isFallback());
    record.put("world_loaded", client.level != null && client.player != null);
    record.put(
        "ready",
        client.level != null
            && client.player != null
            && client.screen == null
            && client.getOverlay() == null
            && client.isWindowActive()
            && client.levelRenderer.countRenderedSections() > 0
            && !net.irisshaders.iris.Iris.isFallback());
    record.put(
        "surface",
        java.util.List.of(client.getMainRenderTarget().width, client.getMainRenderTarget().height));
    record.put(
        "generation",
        net.irisshaders.iris.Iris.getPipelineManager().getVersionCounterForSodiumShaderReload());
    record.put("world_day_time", client.level == null ? -1 : client.level.getDayTime());
    record.put("rain", client.level == null ? -1 : client.level.getRainLevel(1));
    record.put("thunder", client.level == null ? -1 : client.level.getThunderLevel(1));
    record.put("focused", client.isWindowActive());
    var monitor = org.lwjgl.glfw.GLFW.glfwGetVideoMode(org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor());
    record.put(
        "monitor",
        monitor == null
            ? java.util.List.of()
            : java.util.List.of(monitor.width(), monitor.height(), monitor.refreshRate()));
    record.put("renderer", com.pp.shaderlod.dev.measurement.RenderEnvironment.current());
    record.put("runtime", com.pp.shaderlod.dev.config.DevelopmentSettings.runtimeIdentity());
    var runtimeMods = new java.util.TreeMap<String, String>();
    for (String id :
        java.util.List.of("neoforge", "iris", "sodium", "distanthorizons", "shaderlod")) {
      runtimeMods.put(
          id,
          net.neoforged.fml.ModList.get()
              .getModContainerById(id)
              .map(mod -> mod.getModInfo().getVersion().toString())
              .orElse("absent"));
    }
    record.put("runtime_mods", runtimeMods);
    record.put("minecraft", net.minecraft.SharedConstants.getCurrentVersion().getName());
    var server = client.getSingleplayerServer();
    if (server != null) {
      record.put("world_seed", server.overworld().getSeed());
      record.put(
          "world_path",
          server
              .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
              .toAbsolutePath()
              .normalize()
              .toString());
    }
    record.put("resource_packs", client.getResourcePackRepository().getSelectedIds());
    var runtime = Runtime.getRuntime();
    record.put(
        "jvm_heap",
        java.util.Map.of(
            "used_bytes",
            runtime.totalMemory() - runtime.freeMemory(),
            "committed_bytes",
            runtime.totalMemory(),
            "limit_bytes",
            runtime.maxMemory()));
    record.put("dimension", String.valueOf(net.irisshaders.iris.Iris.getCurrentDimension()));
    record.put("dh", com.pp.shaderlod.integration.DhObservation.current());
    record.put("dh_settings", com.pp.shaderlod.integration.DhObservation.settings());
    record.put("dh_witness", dhWitnessed());
    record.put("dh_buffer_renders", dhBufferRenders());
    record.put("rendered_frames", renderedFrames);
    record.put("monotonic_ns", System.nanoTime());
    record.put(
        "probe_invocations",
        java.util.Map.of(
            "identity-probe",
            IdentityProvider.invocations(),
            "identity-companion-probe",
            IdentityCompanionProvider.invocations()));
    record.put("selection", selection == null ? modules.status() : selection.state().toString());
    record.put("selected_modules", selectedModules);
    if (moduleSelection && modules.status().equals("ACTIVE")) {
      record.put("activation", modules.activation());
    } else if (selection != null
        && selection.state() == com.pp.shaderlod.engine.SelectionTransaction.State.ACTIVE) {
      record.put("activation", selection.activation());
    }
    record.put("loaded_main_class_sha256", classDigest(com.pp.shaderlod.storage.AtomicFiles.class));
    record.put("loaded_dev_class_sha256", classDigest(BenchmarkControl.class));
    record.put("options", com.pp.shaderlod.integration.IrisSelection.effectiveOptions());
    if (!record.containsKey("activation")
        && net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()) {
      try {
        record.put("activation", backend.observe());
      } catch (IOException unavailable) {
        record.put("activation_unavailable", unavailable.toString());
      }
    }
    record.put(
        "client_options",
        java.util.Map.of(
            "render_distance",
            client.options.renderDistance().get(),
            "simulation_distance",
            client.options.simulationDistance().get(),
            "max_fps",
            client.options.framerateLimit().get(),
            "vsync",
            client.options.enableVsync().get(),
            "fov",
            client.options.fov().get()));
    if (client.player != null) {
      record.put(
          "pose",
          java.util.List.of(
              client.player.getX(),
              client.player.getY(),
              client.player.getZ(),
              client.player.getYRot(),
              client.player.getXRot()));
    }
    return record;
  }

  private java.util.Map<String, Object> image(String name) throws IOException {
    try (var image =
        net.minecraft.client.Screenshot.takeScreenshot(
            Minecraft.getInstance().getMainRenderTarget())) {
      byte[] png = image.asByteArray();
      AtomicFiles.publish(directory.resolve(name), png);
      var record = new LinkedHashMap<String, Object>();
      record.put("image", name);
      record.put("image_sha256", com.pp.shaderlod.storage.PackArchive.sha256(png));
      record.put(
          "image_has_variation",
          java.util.Arrays.stream(image.getPixelsRGBA()).distinct().limit(2).count() > 1);
      return record;
    }
  }

  private static String classDigest(Class<?> type) {
    try (var input = type.getResourceAsStream(type.getSimpleName() + ".class")) {
      if (input == null) {
        return "unavailable";
      }
      return com.pp.shaderlod.storage.PackArchive.sha256(input.readAllBytes());
    } catch (IOException failure) {
      return "unavailable";
    }
  }

  private static boolean dhWitnessed() {
    return net.neoforged.fml.ModList.get().isLoaded("distanthorizons") && DhControl.witnessed();
  }

  private static long dhBufferRenders() {
    return dhWitnessed() ? DhControl.bufferRenders() : -1;
  }

  private void selectSurface(int width, int height) throws IOException {
    var window = Minecraft.getInstance().getWindow();
    var monitor = window.findBestMonitor();
    if (monitor == null) {
      throw new IOException("Fullscreen monitor unavailable");
    }
    var current = org.lwjgl.glfw.GLFW.glfwGetVideoMode(monitor.getMonitor());
    if (current != null && current.width() == width && current.height() == height) {
      return;
    }
    monitor.refreshVideoModes();
    com.mojang.blaze3d.platform.VideoMode chosen = null;
    for (int index = 0; index < monitor.getModeCount(); index++) {
      var mode = monitor.getMode(index);
      if (mode.getWidth() == width
          && mode.getHeight() == height
          && (chosen == null || mode.getRefreshRate() > chosen.getRefreshRate())) {
        chosen = mode;
      }
    }
    if (chosen == null) {
      throw new IOException("Monitor does not offer the requested surface");
    }
    if (originalVideoMode == null) {
      originalVideoMode = window.getPreferredFullscreenVideoMode();
    }
    window.setPreferredFullscreenVideoMode(java.util.Optional.of(chosen));
    window.changeFullscreenVideoMode();
  }

  private static java.util.List<Double> vector(com.google.gson.JsonArray array) {
    if (array == null || array.size() != 5) {
      throw new IllegalArgumentException("Five numeric pose values required");
    }
    var values = new java.util.ArrayList<Double>();
    for (var element : array) {
      if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
        throw new IllegalArgumentException("Numeric pose required");
      }
      values.add(element.getAsDouble());
    }
    return java.util.List.copyOf(values);
  }

  private static java.util.List<Double> currentPose() {
    var player = Minecraft.getInstance().player;
    if (player == null) {
      throw new IllegalStateException("Player unavailable");
    }
    return java.util.List.of(
        player.getX(),
        player.getY(),
        player.getZ(),
        (double) player.getYRot(),
        (double) player.getXRot());
  }

  private static double distance(java.util.List<Double> a, java.util.List<Double> b) {
    double sum = 0;
    for (int i = 0; i < 3; i++) {
      sum += Math.pow(a.get(i) - b.get(i), 2);
    }
    return sum;
  }

  private static void position(java.util.List<Double> pose) {
    position(pose, true);
  }

  private static void position(java.util.List<Double> pose, boolean snap) {
    var player = Minecraft.getInstance().player;
    if (player == null) {
      throw new IllegalStateException("Player unavailable");
    }
    player.setPos(pose.get(0), pose.get(1), pose.get(2));
    if (snap) {
      player.xo = pose.get(0);
      player.yo = pose.get(1);
      player.zo = pose.get(2);
    }
    player.setYRot(pose.get(3).floatValue());
    player.setXRot(pose.get(4).floatValue());
    if (snap) {
      player.yRotO = player.getYRot();
      player.xRotO = player.getXRot();
    }
  }

  private void restore() throws IOException {
    if (selection != null) {
      selection.off();
      selection = null;
    }
    modules.off();
    moduleSelection = false;
    selectedModules = java.util.List.of();
  }

  private void restorePose() {
    if (scenarioOriginalPose != null) {
      position(scenarioOriginalPose);
      scenarioOriginalPose = null;
    }
  }

  private void select(JsonObject payload) throws IOException {
    restore();
    if (!payload.get("pack").isJsonPrimitive()
        || !payload.get("pack").getAsJsonPrimitive().isString()
        || !payload.get("sha256").isJsonPrimitive()
        || !payload.get("modules").isJsonArray()) {
      throw new IOException("Invalid selection payload");
    }
    String pack = payload.get("pack").getAsString();
    String hash = payload.get("sha256").getAsString();
    var source = com.pp.shaderlod.integration.IrisSelection.archive(pack);
    var archive =
        com.pp.shaderlod.storage.PackArchive.read(
            source, com.pp.shaderlod.storage.PackArchive.Limits.defaults());
    if (!archive.identity().archiveSha256().equals(hash)) {
      throw new IOException("Requested archive digest differs");
    }
    var ids = new java.util.ArrayList<String>();
    for (var id : payload.getAsJsonArray("modules")) {
      if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
        throw new IOException("String module IDs required");
      }
      ids.add(id.getAsString());
    }
    if (ids.isEmpty()) {
      selection =
          com.pp.shaderlod.engine.SelectionTransaction.prepare(
              backend,
              new com.pp.shaderlod.domain.Selection(pack, true, java.util.Map.of(), ids),
              hash,
              java.util.Set.of("linked-archive:" + hash),
              net.irisshaders.iris.Iris.getPipelineManager()
                  .getVersionCounterForSodiumShaderReload());
      selection.apply();
    } else {
      moduleSelection = true;
      modules.apply(pack, ids);
      selectedModules = java.util.List.copyOf(ids);
    }
    selectionStarted = System.nanoTime();
    pendingSelect = true;
  }

  private void publish(java.util.Map<String, Object> record) throws IOException {
    AtomicFiles.publish(
        directory.resolve("result-" + sequence + ".json"),
        gson.toJson(record).getBytes(StandardCharsets.UTF_8));
    lastActivity = System.nanoTime();
  }
}
