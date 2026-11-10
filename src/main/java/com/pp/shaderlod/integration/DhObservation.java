package com.pp.shaderlod.integration;

import net.neoforged.fml.ModList;

public record DhObservation(boolean present, String version, String readiness) {
  public static DhObservation current() {
    var mod = ModList.get().getModContainerById("distanthorizons");
    return mod.map(value ->
            new DhObservation(
                true, value.getModInfo().getVersion().toString(), DhApiObservation.read()))
        .orElseGet(() -> new DhObservation(false, "absent", "unavailable: mod absent"));
  }
}
