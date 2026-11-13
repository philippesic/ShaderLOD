package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderGroupsTest {
  @Test
  void nestedGroupsReportTheInnermostOwnerAndSurviveImbalance() {
    var owners = new ArrayList<String>();
    RenderGroups.reset();
    RenderGroups.observe(owners::add);
    RenderGroups.began("composite");
    RenderGroups.began("composite1");
    RenderGroups.ended();
    RenderGroups.began("a/b");
    RenderGroups.ended();
    RenderGroups.ended();
    RenderGroups.ended();
    RenderGroups.began(" ");
    assertEquals(
        List.of(
            "composite",
            "composite/composite1",
            "composite",
            "composite/a_b",
            "composite",
            RenderGroups.UNGROUPED,
            "unnamed"),
        owners);
    RenderGroups.reset();
    assertEquals(RenderGroups.UNGROUPED, RenderGroups.owner());
    owners.clear();
    for (int depth = 0; depth < 40; depth++) {
      RenderGroups.began("g");
    }
    assertEquals(32, owners.size());
    for (int depth = 0; depth < 40; depth++) {
      RenderGroups.ended();
    }
    assertEquals(RenderGroups.UNGROUPED, RenderGroups.owner());
    assertEquals(64, owners.size());
    RenderGroups.observe(null);
    RenderGroups.reset();
  }
}
