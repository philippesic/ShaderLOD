package com.pp.shaderlod.integration;

import com.pp.shaderlod.domain.TextureUnitBudget;

public final class SamplerIndexPolicy {
  private static volatile TextureUnitBudget observed;

  private SamplerIndexPolicy() {}

  public static int observe(int fragmentResources, int combinedIndices, int trackedIndices) {
    var budget = new TextureUnitBudget(fragmentResources, combinedIndices, trackedIndices);
    observed = budget;
    return budget.bindingIndices();
  }

  public static TextureUnitBudget observed() {
    return observed;
  }
}
