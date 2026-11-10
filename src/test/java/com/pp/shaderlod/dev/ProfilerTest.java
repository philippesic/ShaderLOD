package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProfilerTest {
  @Test
  void boundedFramesExposeLossAndMissingGpu() {
    var profiler = new BoundedProfiler(2);
    profiler.record(new BoundedProfiler.Sample(1, 0, 0));
    profiler.record(new BoundedProfiler.Sample(1, 1, 10));
    profiler.record(new BoundedProfiler.Sample(2, 2, 20));
    var result = profiler.snapshot();
    assertEquals(1, result.lost());
    assertEquals(1, result.samples().getFirst().frame());
    assertEquals("not collected by CPU stream; see separate GPU observations", result.gpuTiming());
    assertThrows(IllegalArgumentException.class, () -> new BoundedProfiler.Sample(0, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new BoundedProfiler(0));
  }
}
