package com.pp.shaderlod.dev;

import com.google.gson.Gson;
import com.pp.shaderlod.storage.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class CaptureSpool {
  private final Path directory;
  private final long sequence;
  private final ThreadPoolExecutor writer;
  private final AtomicInteger written = new AtomicInteger();
  private final AtomicReference<String> failure = new AtomicReference<>();
  private int queued;

  CaptureSpool(Path directory, long sequence) {
    this.directory = directory;
    this.sequence = sequence;
    writer =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4),
            action -> {
              var thread = new Thread(action, "shaderlod-frame-spool");
              thread.setDaemon(true);
              return thread;
            });
  }

  void offer(List<FrameCapture.Frame> frames) {
    if (frames.isEmpty()) {
      return;
    }
    int chunk = ++queued;
    try {
      writer.execute(() -> {
        try {
          AtomicFiles.publish(
              directory.resolve("frames-" + sequence + "-" + chunk + ".json"),
              new Gson().toJson(frames).getBytes(StandardCharsets.UTF_8));
          written.incrementAndGet();
        } catch (IOException error) {
          failure.compareAndSet(null, error.toString());
        }
      });
    } catch (java.util.concurrent.RejectedExecutionException error) {
      failure.compareAndSet(null, "Frame spool queue overflow");
    }
  }

  Map<String, Object> finish() throws IOException {
    writer.shutdown();
    boolean complete;
    try {
      complete = writer.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted frame drain", error);
    }
    return Map.of(
        "queued",
        queued,
        "written",
        written.get(),
        "complete",
        complete && written.get() == queued && failure.get() == null,
        "failure",
        failure.get() == null ? "none" : failure.get());
  }
}
