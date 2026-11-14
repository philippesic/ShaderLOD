package com.pp.shaderlod.dev.config;

public final class DevelopmentSettings {
  private DevelopmentSettings() {}

  public static java.util.Optional<java.nio.file.Path> benchmarkDirectory() {
    return java.util.Optional.ofNullable(System.getProperty("shaderlod.benchmarkDirectory"))
        .map(java.nio.file.Path::of);
  }

  public static boolean dhGeneration() {
    return Boolean.getBoolean("shaderlod.dhGeneration");
  }

  public static boolean dhWitness() {
    return !"false".equals(System.getProperty("shaderlod.dhWitness"));
  }

  public static boolean functionalProbe() {
    return Boolean.getBoolean("shaderlod.functionalProbe");
  }

  public static java.util.Map<String, String> runtimeIdentity() {
    var result = new java.util.TreeMap<String, String>();
    for (String key :
        java.util.List.of(
            "java.runtime.version", "java.vendor", "os.name", "os.version", "os.arch")) {
      result.put(key, System.getProperty(key, "unavailable"));
    }
    return java.util.Map.copyOf(result);
  }
}
