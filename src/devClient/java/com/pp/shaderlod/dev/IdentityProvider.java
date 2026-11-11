package com.pp.shaderlod.dev;

import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.ResourcePatch;
import com.pp.shaderlod.engine.OptimizationProvider;
import java.util.List;
import java.util.Map;

public final class IdentityProvider implements OptimizationProvider {
  private static int invocations;

  public static int invocations() {
    return invocations;
  }

  @Override
  public String id() {
    return "identity-probe";
  }

  @Override
  public boolean supports(PackIdentity identity) {
    return identity
        .archiveSha256()
        .equals("fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38");
  }

  @Override
  public List<ResourcePatch> patches(PackIdentity identity, Map<String, String> parameters) {
    if (!supports(identity) || !parameters.isEmpty()) {
      throw new IllegalArgumentException("Unsupported identity probe input");
    }
    invocations++;
    return List.of();
  }
}
