package com.pp.shaderlod.dev.measurement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class GpuCollector {
  public interface Queries {
    int create();

    void timestamp(int query);

    boolean available(int query);

    long value(int query);

    void delete(int query);
  }

  public record Sample(long generation, long frame, long nanos) {}

  public record Snapshot(List<Sample> samples, int pending, long lost, long invalid, String scope) {
    public Snapshot {
      samples = List.copyOf(samples);
    }
  }

  private record Pair(long generation, long frame, int begin, int end) {}

  private final Queries queries;
  private final int capacity;
  private final ArrayDeque<Pair> pending = new ArrayDeque<>();
  private final ArrayDeque<Sample> samples = new ArrayDeque<>();
  private Pair active;
  private long lost;
  private long invalid;

  public GpuCollector(Queries queries, int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("Positive query capacity required");
    }
    this.queries = java.util.Objects.requireNonNull(queries);
    this.capacity = capacity;
  }

  public void begin(long generation, long frame) {
    if (generation < 0 || frame < 0) {
      throw new IllegalArgumentException("Invalid query owner");
    }
    poll();
    if (active != null) {
      discard(active);
      active = null;
      invalid++;
    }
    if (pending.size() >= capacity) {
      lost++;
      return;
    }
    active = new Pair(generation, frame, queries.create(), queries.create());
    queries.timestamp(active.begin());
  }

  public void end(long generation, long frame) {
    if (active == null) {
      return;
    }
    var pair = active;
    active = null;
    if (pair.generation() != generation || pair.frame() != frame) {
      discard(pair);
      invalid++;
      return;
    }
    queries.timestamp(pair.end());
    pending.addLast(pair);
  }

  public void poll() {
    var completed = new ArrayList<Pair>();
    for (var pair : pending) {
      if (queries.available(pair.begin()) && queries.available(pair.end())) {
        long start = queries.value(pair.begin());
        long end = queries.value(pair.end());
        if (end < start || end - start < 0) {
          invalid++;
        } else {
          if (samples.size() == capacity) {
            samples.removeFirst();
            lost++;
          }
          samples.addLast(new Sample(pair.generation(), pair.frame(), end - start));
        }
        completed.add(pair);
      }
    }
    for (var pair : completed) {
      pending.remove(pair);
      discard(pair);
    }
  }

  private void discard(Pair pair) {
    queries.delete(pair.begin());
    queries.delete(pair.end());
  }

  public Snapshot snapshot() {
    return new Snapshot(
        List.copyOf(samples),
        pending.size() + (active == null ? 0 : 1),
        lost,
        invalid,
        "diagnostic client-frame GPU timestamps; per-pass work and performance qualification unavailable");
  }

  public void close() {
    if (active != null) {
      discard(active);
      active = null;
    }
    pending.forEach(this::discard);
    pending.clear();
  }
}
