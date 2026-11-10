package com.pp.shaderlod.dev;

import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.ResourcePatch;
import com.pp.shaderlod.engine.OptimizationProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class ReloadFailureProvider implements OptimizationProvider {
  @Override
  public String id() {
    return "reload-failure-probe";
  }

  @Override
  public boolean supports(PackIdentity identity) {
    return new IdentityProvider().supports(identity);
  }

  @Override
  public List<ResourcePatch> patches(PackIdentity identity, Map<String, String> parameters) {
    if (!supports(identity) || !parameters.isEmpty()) {
      throw new IllegalArgumentException("Unsupported fault probe input");
    }
    String member = "shaders/world0/composite1.fsh";
    return List.of(
        new ResourcePatch(
            member,
            identity.memberSha256().get(member),
            "#version 330 compatibility\ninvalid_shader_token\n".getBytes(StandardCharsets.UTF_8)));
  }
}
