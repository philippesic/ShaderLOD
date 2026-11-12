package com.pp.shaderlod.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.pp.shaderlod.storage.AtomicFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class QualityCapture {
  private final Path directory;
  private final ThreadPoolExecutor writer =
      new ThreadPoolExecutor(
          2,
          2,
          0,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(4),
          action -> {
            var thread = new Thread(action, "shaderlod-quality-writer");
            thread.setDaemon(true);
            return thread;
          });
  private final List<CompletableFuture<Map<String, Object>>> frames = new ArrayList<>();
  private int dropped;

  QualityCapture(Path directory) {
    this.directory = directory;
  }

  void offer(String name, NativeImage image, Map<String, Object> metadata) {
    var result = new CompletableFuture<Map<String, Object>>();
    try {
      writer.execute(() -> {
        try (image) {
          byte[] payload = image.asByteArray();
          AtomicFiles.publish(directory.resolve(name), payload);
          var record = new LinkedHashMap<>(metadata);
          record.put("image", name);
          record.put("image_sha256", com.pp.shaderlod.storage.PackArchive.sha256(payload));
          result.complete(Map.copyOf(record));
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
          result.completeExceptionally(failure);
        }
      });
      frames.add(result);
    } catch (java.util.concurrent.RejectedExecutionException failure) {
      image.close();
      dropped++;
    }
  }

  Map<String, Object> finish() throws IOException {
    writer.shutdown();
    boolean drained;
    try {
      drained = writer.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IOException("Quality drain interrupted", failure);
    }
    var completed = new ArrayList<Map<String, Object>>();
    var failures = new ArrayList<String>();
    for (var frame : frames) {
      if (frame.isDone() && !frame.isCompletedExceptionally()) {
        completed.add(frame.join());
      } else {
        failures.add(frame.isDone() ? "Encoding failed" : "Encoding incomplete");
      }
    }
    return Map.of(
        "frames",
        completed,
        "dropped",
        dropped,
        "failures",
        failures,
        "complete",
        drained && dropped == 0 && failures.isEmpty());
  }
}
