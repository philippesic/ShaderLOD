package com.pp.shaderlod.engine;

import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.ResourcePatch;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface OptimizationProvider {
  String id();

  boolean supports(PackIdentity identity);

  List<ResourcePatch> patches(PackIdentity identity, Map<String, String> parameters);

  default Set<String> conflicts() {
    return Set.of();
  }
}
