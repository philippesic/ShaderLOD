package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaptureCadenceTest {
  @Test
  void readbackTimeDoesNotAccumulateOrSlowTheRoute() {
    var cadence = new CaptureCadence(0, 500);
    assertTrue(cadence.due(5));
    assertFalse(cadence.due(200));
    assertTrue(cadence.due(505));
    assertFalse(cadence.due(800));
    assertTrue(cadence.due(1005));
    assertEquals(0, cadence.skipped());
    assertTrue(cadence.due(2505));
    assertEquals(2, cadence.skipped());
    assertFalse(cadence.due(2506));
  }
}
