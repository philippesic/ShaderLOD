package com.pp.shaderlod.dev.config;

public final class DevelopmentSettings {
  private DevelopmentSettings() {}

  public static boolean functionalProbe() {
    return Boolean.getBoolean("shaderlod.functionalProbe");
  }
}
