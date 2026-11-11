package com.pp.shaderlod.dev;

import java.util.ArrayList;
import java.util.List;

public final class FrameCapture {
  public record Frame(long sequence, long timestamp_ns, long interval_ns, long generation) {}

  public record Result(List<Frame> frames, boolean overflow, boolean generationChanged) {
    public Result {
      frames = List.copyOf(frames);
    }
  }

  private final int capacity;
  private final ArrayList<Frame> frames = new ArrayList<>();
  private long previous;
  private long generation;
  private boolean overflow;
  private boolean generationChanged;

  public FrameCapture(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("Positive frame capacity required");
    }
    this.capacity = capacity;
  }

  public void observe(long timestamp, long observedGeneration) {
    if (timestamp <= 0 || observedGeneration < 0 || (previous != 0 && timestamp <= previous)) {
      throw new IllegalArgumentException("Invalid frame clock or generation");
    }
    if (previous == 0) {
      generation = observedGeneration;
    } else {
      generationChanged |= generation != observedGeneration;
      if (frames.size() == capacity) {
        overflow = true;
      } else {
        frames.add(
            new Frame(frames.size() + 1L, timestamp, timestamp - previous, observedGeneration));
      }
    }
    previous = timestamp;
  }

  public Result finish() {
    return new Result(frames, overflow, generationChanged);
  }
}
