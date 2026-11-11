package com.pp.shaderlod.integration;

import net.neoforged.fml.ModList;

public record DhObservation(boolean present, String version, Boolean enabled, String readiness) {
  public static DhObservation current() {
    var mod = ModList.get().getModContainerById("distanthorizons");
    return mod.map(value ->
            new DhObservation(
                true,
                value.getModInfo().getVersion().toString(),
                DhApiObservation.enabled(),
                DhApiObservation.read()))
        .orElseGet(() -> new DhObservation(false, "absent", null, "unavailable: mod absent"));
  }
}
