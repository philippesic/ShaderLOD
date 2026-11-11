package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameCaptureTest {
  @Test
  void fullFrameIntervalsAndImmutableSnapshot() {
    var capture = new FrameCapture(3);
    capture.observe(100, 1);
    capture.observe(110, 1);
    var snapshot = capture.finish();
    capture.observe(200, 1);
    assertEquals(1, snapshot.frames().size());
    assertEquals(90, capture.finish().frames().get(1).interval_ns());
    assertFalse(capture.finish().overflow());
    assertThrows(IllegalArgumentException.class, () -> capture.observe(200, 1));
  }

  @Test
  void overflowAndReloadNeverDisappear() {
    var capture = new FrameCapture(1);
    capture.observe(10, 1);
    capture.observe(20, 1);
    capture.observe(30, 2);
    assertEquals(1, capture.finish().frames().size());
    assertTrue(capture.finish().overflow());
    assertTrue(capture.finish().generationChanged());
  }
}
