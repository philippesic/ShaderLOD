package com.pp.shaderlod.domain;

import java.util.List;
import java.util.Map;

public record Selection(
    String packName, boolean enabled, Map<String, String> options, List<String> optimizationIds) {
  public Selection {
    if (packName == null
        || packName.isBlank()
        || packName.contains("/")
        || packName.contains("\\")
        || packName.equals(".")
        || packName.equals("..")) {
      throw new IllegalArgumentException("One shader archive filename required");
    }
    options = Map.copyOf(options);
    optimizationIds = List.copyOf(optimizationIds);
    if (optimizationIds.stream().distinct().count() != optimizationIds.size()) {
      throw new IllegalArgumentException("Duplicate optimization ID");
    }
  }
}
