package com.pp.shaderlod.domain;

import java.util.Map;
import java.util.Optional;

public record NativeState(
    Optional<String> packName,
    boolean shadersEnabled,
    Map<String, String> queuedOptions,
    Map<String, String> effectiveOptions) {
  public NativeState {
    java.util.Objects.requireNonNull(packName);
    queuedOptions = Map.copyOf(queuedOptions);
    effectiveOptions = Map.copyOf(effectiveOptions);
  }
}
