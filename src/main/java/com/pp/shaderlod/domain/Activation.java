package com.pp.shaderlod.domain;

import java.util.Map;
import java.util.Set;

public record Activation(
    String packName,
    String archiveSha256,
    long generation,
    boolean linked,
    Map<String, String> effectiveOptions,
    Set<String> witnesses) {
  public Activation {
    PackIdentity.requireDigest(archiveSha256);
    if (generation < 0 || packName == null || packName.isBlank()) {
      throw new IllegalArgumentException("Invalid activation identity");
    }
    effectiveOptions = Map.copyOf(effectiveOptions);
    witnesses = Set.copyOf(witnesses);
  }
}
