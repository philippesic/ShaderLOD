package com.pp.shaderlod.dev.config;

public final class DevelopmentSettings {
  private DevelopmentSettings() {}

  public static java.util.Optional<java.nio.file.Path> benchmarkDirectory() {
    return java.util.Optional.ofNullable(System.getProperty("shaderlod.benchmarkDirectory"))
        .map(java.nio.file.Path::of);
  }

  public static boolean functionalProbe() {
    return Boolean.getBoolean("shaderlod.functionalProbe");
  }
}
