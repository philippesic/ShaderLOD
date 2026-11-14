package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pp.shaderlod.domain.PackIdentity;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GpuLoadProviderTest {
  private static final String PACK =
      "fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38";
  private static final String FINAL =
      "a31fb000ae6dd1f963f44ff2cd238af3583d46a696c998f8e33762c569d9738f";

  @Test
  void addsRuntimeDependentWorkAfterTheOriginalFinalPass() {
    var provider = new GpuLoadProvider();
    var identity = new PackIdentity(PACK, Map.of(GpuLoadProvider.MEMBER, FINAL));
    var patches = provider.patches(identity, Map.of());
    assertEquals(1, patches.size());
    assertEquals(GpuLoadProvider.MEMBER, patches.get(0).member());
    assertEquals(FINAL, patches.get(0).expectedSha256());
    String source = new String(patches.get(0).replacement(), StandardCharsets.UTF_8);
    assertTrue(source.contains("#include \"/program/final.glsl\""));
    assertTrue(source.contains("shaderlodOriginalMain();"));
    assertTrue(source.contains("i < " + GpuLoadProvider.ITERATIONS));
    assertTrue(source.contains("gl_FragCoord.xy"));
    assertTrue(source.contains("viewWidth"));
    assertFalse(provider.supports(new PackIdentity(PACK, Map.of(GpuLoadProvider.MEMBER, PACK))));
    assertThrows(
        IllegalArgumentException.class, () -> provider.patches(identity, Map.of("x", "1")));
  }
}
