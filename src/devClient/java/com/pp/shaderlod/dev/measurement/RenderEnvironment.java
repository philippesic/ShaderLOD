package com.pp.shaderlod.dev.measurement;

import java.util.Map;
import org.lwjgl.opengl.GL11;

public final class RenderEnvironment {
  private RenderEnvironment() {}

  public static Map<String, String> current() {
    return Map.of(
        "renderer",
        String.valueOf(GL11.glGetString(GL11.GL_RENDERER)),
        "vendor",
        String.valueOf(GL11.glGetString(GL11.GL_VENDOR)),
        "version",
        String.valueOf(GL11.glGetString(GL11.GL_VERSION)));
  }
}
