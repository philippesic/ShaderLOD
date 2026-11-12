package com.pp.shaderlod.dev;

final class CaptureCadence {
  private long next;
  private final long interval;
  private long skipped;

  CaptureCadence(long start, long interval) {
    if (start < 0 || interval <= 0) {
      throw new IllegalArgumentException("Positive cadence and nonnegative origin required");
    }
    next = start;
    this.interval = interval;
  }

  boolean due(long now) {
    if (now < next) {
      return false;
    }
    long missed = (now - next) / interval;
    skipped += missed;
    next += (missed + 1) * interval;
    return true;
  }

  long skipped() {
    return skipped;
  }
}
