package com.pp.shaderlod.dev;

import java.util.ArrayDeque;
import java.util.List;

public final class BoundedProfiler {
  public record Sample(long generation, long frame, long cpuNanos) {
    public Sample {
      if (generation < 0 || frame < 0 || cpuNanos < 0) {
        throw new IllegalArgumentException("Invalid profiler sample");
      }
    }
  }

  public record Snapshot(List<Sample> samples, long lost, String gpuTiming) {
    public Snapshot {
      samples = List.copyOf(samples);
    }
  }

  private final int capacity;
  private final ArrayDeque<Sample> samples = new ArrayDeque<>();
  private long lost;

  public BoundedProfiler(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("Positive capacity required");
    }
    this.capacity = capacity;
  }

  public synchronized void record(Sample sample) {
    if (samples.size() == capacity) {
      samples.removeFirst();
      lost++;
    }
    samples.addLast(sample);
  }

  public synchronized Snapshot snapshot() {
    return new Snapshot(
        List.copyOf(samples), lost, "not collected by CPU stream; see separate GPU observations");
  }
}
