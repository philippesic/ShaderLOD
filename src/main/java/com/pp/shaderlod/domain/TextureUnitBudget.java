package com.pp.shaderlod.domain;

public record TextureUnitBudget(int fragmentResources, int combinedIndices, int trackedIndices) {
  public TextureUnitBudget {
    if (fragmentResources < 1
        || combinedIndices < 1
        || trackedIndices < 1
        || Math.min(combinedIndices, trackedIndices) < fragmentResources) {
      throw new IllegalArgumentException(
          "Texture index namespace cannot preserve native resource capacity");
    }
  }

  public int bindingIndices() {
    return Math.min(combinedIndices, trackedIndices);
  }
}
