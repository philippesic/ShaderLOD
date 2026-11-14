package com.pp.shaderlod.dev;

import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.ResourcePatch;
import com.pp.shaderlod.engine.OptimizationProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class GpuLoadProvider implements OptimizationProvider {
  static final String MEMBER = "shaders/world0/final.fsh";
  static final int ITERATIONS = 22;

  @Override
  public String id() {
    return "gpu-load-probe";
  }

  @Override
  public boolean supports(PackIdentity identity) {
    return new IdentityProvider().supports(identity)
        && "a31fb000ae6dd1f963f44ff2cd238af3583d46a696c998f8e33762c569d9738f"
            .equals(identity.memberSha256().get(MEMBER));
  }

  @Override
  public List<ResourcePatch> patches(PackIdentity identity, Map<String, String> parameters) {
    if (!supports(identity) || !parameters.isEmpty()) {
      throw new IllegalArgumentException("Unsupported GPU load control");
    }
    return List.of(
        new ResourcePatch(
            MEMBER,
            identity.memberSha256().get(MEMBER),
            source().getBytes(StandardCharsets.UTF_8)));
  }

  static String source() {
    return String.join(
        "\n",
        "#version 130",
        "",
        "#define FRAGMENT_SHADER",
        "#define OVERWORLD",
        "#define FINAL",
        "",
        "#define main shaderlodOriginalMain",
        "#include \"/program/final.glsl\"",
        "#undef main",
        "",
        "void main() {",
        "    shaderlodOriginalMain();",
        "    float load = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);",
        "    for (int i = 0; i < " + ITERATIONS + "; i++) {",
        "        load = fract(load * 1.6180339 + sin(load * 6.2831853 + float(i) * 0.37"
            + " + viewWidth * 0.0001));",
        "    }",
        "    gl_FragData[0].rgb += (load - 0.5) / 255.0;",
        "}",
        "");
  }
}
