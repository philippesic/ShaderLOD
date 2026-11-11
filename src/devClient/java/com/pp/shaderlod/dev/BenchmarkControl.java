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
  private final Path directory;
  private final Gson gson = new Gson();
  private long sequence;
  private long started;
  private long duration;
  private FrameCapture capture;
  private String fault;

  BenchmarkControl(Path directory) {
    this.directory = directory;
  }

  void frame(long timestamp, long generation) {
    if (capture != null) {
      capture.observe(timestamp, generation);
    }
  }

  void tick(ClientTickEvent.Post event) {
    if (fault != null) {
      return;
    }
    try {
      if (capture != null) {
        if (System.nanoTime() - started >= duration) {
          var result = capture.finish();
          capture = null;
          var record = state();
          record.put("capture", result);
          record.put("elapsed_ns", System.nanoTime() - started);
          record.put(
              "valid",
              !result.overflow() && !result.generationChanged() && !result.frames().isEmpty());
          publish(record);
        }
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
      String action = request.action();
      if (action.equals("status")) {
        publish(state());
      } else if (action.equals("close")) {
        publish(state());
        Minecraft.getInstance().stop();
      } else if (action.equals("capture")) {
        long millis = request.durationMillis();
        if (millis < 100 || millis > 60000 || !Boolean.TRUE.equals(state().get("ready"))) {
          throw new IOException("Ready world and bounded 100–60000 ms capture required");
        }
        duration = millis * 1000000;
        started = System.nanoTime();
        capture = new FrameCapture(200000);
      } else {
        throw new IOException("Unsupported command");
      }
    } catch (IOException
        | IllegalArgumentException
        | IllegalStateException
        | com.google.gson.JsonParseException failure) {
      fault = failure.getMessage();
      var record = state();
      record.put("failure", fault);
      try {
        AtomicFiles.publish(
            directory.resolve("failure.json"),
            gson.toJson(record).getBytes(StandardCharsets.UTF_8));
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
    record.put("pack", net.irisshaders.iris.Iris.getCurrentPackName());
    record.put("fallback", net.irisshaders.iris.Iris.isFallback());
    record.put(
        "ready",
        client.level != null
            && client.player != null
            && client.screen == null
            && client.levelRenderer.countRenderedSections() > 0
            && !net.irisshaders.iris.Iris.isFallback());
    record.put(
        "surface",
        java.util.List.of(client.getMainRenderTarget().width, client.getMainRenderTarget().height));
    record.put("options", com.pp.shaderlod.integration.IrisSelection.effectiveOptions());
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

  private void publish(java.util.Map<String, Object> record) throws IOException {
    AtomicFiles.publish(
        directory.resolve("result-" + sequence + ".json"),
        gson.toJson(record).getBytes(StandardCharsets.UTF_8));
  }
}
