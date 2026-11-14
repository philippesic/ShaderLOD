package com.pp.shaderlod.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DhSectionsTest {
  @Test
  void onlyTopLevelStagesBecomeGroupsAndDeeperSectionsStayBalanced() {
    var owners = new ArrayList<String>();
    RenderGroups.reset();
    DhSections.reset();
    RenderGroups.observe(owners::add);
    RenderGroups.began("Terrain solid");
    DhSections.began("DH-RenderLevel");
    DhSections.began("LOD Opaque");
    DhSections.changed("Custom Objects");
    for (int object = 0; object < 300; object++) {
      DhSections.began("render object");
      DhSections.changed("render next");
      DhSections.ended();
    }
    DhSections.ended();
    DhSections.ended();
    RenderGroups.ended();
    assertEquals(
        List.of(
            "Terrain solid",
            "Terrain solid/DH-RenderLevel",
            "Terrain solid/DH-RenderLevel/DH LOD Opaque",
            "Terrain solid/DH-RenderLevel",
            "Terrain solid/DH-RenderLevel/DH Custom Objects",
            "Terrain solid/DH-RenderLevel",
            "Terrain solid",
            RenderGroups.UNGROUPED),
        owners);
    owners.clear();
    DhSections.ended();
    DhSections.changed("stray");
    assertEquals(List.of(), owners);
    RenderGroups.observe(null);
  }
}
