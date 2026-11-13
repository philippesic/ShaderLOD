package com.pp.shaderlod.dev.measurement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StageProfiler {
  private final Map<String, GpuCollector> collectors = new LinkedHashMap<>();
  private final List<Map<String, Object>> cpu = new ArrayList<>();
  private final List<Map<String, Object>> gpu = new ArrayList<>();
  private final boolean supported;
  private final int counterBits;
  private final String origin;
  private String previous;
  private long started;
  private long frame;
  private long generation;
  private boolean sampling;
  private boolean overflow;
  private long spanId;
  private long activeSpan;
  private final Map<Long, Map<String, Object>> spans = new LinkedHashMap<>();

  public StageProfiler(String origin) {
    this.origin = java.util.Objects.requireNonNull(origin);
    var caps = org.lwjgl.opengl.GL.getCapabilities();
    boolean timer = caps.OpenGL33 || caps.GL_ARB_timer_query;
    counterBits =
        timer
            ? org.lwjgl.opengl.GL15.glGetQueryi(
                org.lwjgl.opengl.GL33.GL_TIMESTAMP, org.lwjgl.opengl.GL15.GL_QUERY_COUNTER_BITS)
            : 0;
    supported = counterBits > 0;
  }

  public void begin(long observedGeneration, long submission) {
    frame = submission;
    generation = observedGeneration;
    sampling = frame % 10 == 0;
    if (sampling) {
      previous = origin;
      started = System.nanoTime();
      activeSpan = ++spanId;
      if (supported) {
        collectors
            .computeIfAbsent(previous, key -> new GpuCollector(new OpenGlQueries(), 64))
            .begin(generation, activeSpan);
      }
    }
  }

  public void boundary(String boundary) {
    if (!sampling) {
      return;
    }
    finishSpan(boundary);
    previous = boundary;
    started = System.nanoTime();
    activeSpan = ++spanId;
    if (supported) {
      collectors
          .computeIfAbsent(previous, key -> new GpuCollector(new OpenGlQueries(), 64))
          .begin(generation, activeSpan);
    }
  }

  public void end() {
    if (sampling) {
      finishSpan("frame-callback-end");
      sampling = false;
    }
  }

  private void finishSpan(String boundary) {
    var row =
        Map.<String, Object>of(
            "span_id",
            activeSpan,
            "submission",
            frame,
            "generation",
            generation,
            "from",
            previous,
            "to",
            boundary,
            "started_ns",
            started,
            "wall_ns",
            System.nanoTime() - started);
    if (cpu.size() < 50000) {
      cpu.add(row);
      spans.put(activeSpan, row);
    } else {
      overflow = true;
    }
    if (supported) {
      var collector = collectors.get(previous);
      collector.end(generation, activeSpan);
      collect(collector);
    }
  }

  private void collect(GpuCollector collector) {
    for (var sample : collector.drain().samples()) {
      if (gpu.size() < 50000) {
        var span = spans.get(sample.frame());
        if (span != null) {
          var timed = new LinkedHashMap<String, Object>(span);
          timed.put("gpu_elapsed_ns", sample.nanos());
          gpu.add(timed);
        } else {
          overflow = true;
        }
      } else {
        overflow = true;
      }
    }
  }

  public Map<String, Object> finish() {
    var health = new LinkedHashMap<String, Object>();
    collectors.forEach((name, collector) -> {
      collect(collector);
      var snapshot = collector.snapshot();
      health.put(
          name,
          Map.of(
              "pending",
              snapshot.pending(),
              "lost",
              snapshot.lost(),
              "invalid",
              snapshot.invalid()));
      collector.close();
    });
    return Map.of(
        "schema",
        "shaderlod-stage-profile-v1",
        "gpu_supported",
        supported,
        "counter_bits",
        counterBits,
        "sample_every_callbacks",
        10,
        "cpu",
        List.copyOf(cpu),
        "gpu",
        List.copyOf(gpu),
        "health",
        health,
        "overflow",
        overflow,
        "scope",
        "Public renderer-stage intervals; shader effects may share intervals; GPU elapsed is not exclusive occupancy");
  }
}
