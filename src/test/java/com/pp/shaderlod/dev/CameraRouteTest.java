package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CameraRouteTest {
  @Test
  void progressionUsesTimeRatherThanFrameCount() {
    var route = new CameraRoute(List.of(0., 100., 0., 0., 0.), List.of(10., 100., 0., 90., 0.));
    assertEquals(List.of(5., 100., 0., 45., 0.), route.at(.5));
    assertEquals(route.from(), route.at(-1));
    assertEquals(route.to(), route.at(2));
    assertThrows(IllegalArgumentException.class, () -> route.at(Double.NaN));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CameraRoute(route.from(), List.of(100., 100., 0., 0., 0.)));
  }
}
