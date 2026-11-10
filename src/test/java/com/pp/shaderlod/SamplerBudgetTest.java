package com.pp.shaderlod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pp.shaderlod.domain.TextureUnitBudget;
import org.junit.jupiter.api.Test;

class SamplerBudgetTest {
  @Test
  void reservedBindingHolesDoNotConsumeFragmentResourceCapacity() {
    var budget = new TextureUnitBudget(16, 96, 128);
    assertEquals(96, budget.bindingIndices());
    int allocated = 0;
    int last = -1;
    for (int binding = 0; binding < budget.bindingIndices() && allocated < 15; binding++) {
      if (binding != 1 && binding != 2) {
        allocated++;
        last = binding;
      }
    }
    assertEquals(15, allocated);
    assertEquals(16, last);
    assertEquals(16, budget.fragmentResources());
  }

  @Test
  void trackedArrayRemainsAnIndependentUpperBound() {
    assertEquals(32, new TextureUnitBudget(16, 96, 32).bindingIndices());
    assertThrows(IllegalArgumentException.class, () -> new TextureUnitBudget(16, 96, 8));
    assertThrows(IllegalArgumentException.class, () -> new TextureUnitBudget(0, 96, 128));
  }
}
