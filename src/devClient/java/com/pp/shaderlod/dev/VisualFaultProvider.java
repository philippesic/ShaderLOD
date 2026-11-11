package com.pp.shaderlod.dev;

import com.pp.shaderlod.domain.PackIdentity;
import com.pp.shaderlod.domain.ResourcePatch;
import com.pp.shaderlod.engine.OptimizationProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class VisualFaultProvider implements OptimizationProvider {
  @Override
  public String id() {
    return "visual-fault-probe";
  }

  @Override
  public boolean supports(PackIdentity identity) {
    return new IdentityProvider().supports(identity);
  }

  @Override
  public List<ResourcePatch> patches(PackIdentity identity, Map<String, String> parameters) {
    if (!supports(identity) || !parameters.isEmpty()) {
      throw new IllegalArgumentException("Unsupported visual fault control");
    }
    String member = "shaders/world0/composite1.fsh";
    return List.of(
        new ResourcePatch(
            member,
            identity.memberSha256().get(member),
            "#version 330 compatibility\nvoid main(){gl_FragData[0]=vec4(1.0,0.0,1.0,1.0);}\n"
                .getBytes(StandardCharsets.UTF_8)));
  }
}
