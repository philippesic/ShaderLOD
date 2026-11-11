package com.pp.shaderlod.integration;

import com.seibel.distanthorizons.api.DhApi;

final class DhApiObservation {
  private DhApiObservation() {}

  static Boolean enabled() {
    var config = DhApi.Delayed.configs;
    return config == null ? null : config.graphics().renderingEnabled().getValue();
  }

  static String read() {
    var config = DhApi.Delayed.configs;
    if (config == null) {
      return "unknown: DH API not initialized";
    }
    var graphics = config.graphics();
    return "enabled="
        + graphics.renderingEnabled().getValue()
        + "; distance="
        + graphics.chunkRenderDistance().getValue()
        + "; mode="
        + graphics.renderingMode().getValue()
        + "; rendered coverage unknown";
  }
}
