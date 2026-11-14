package com.pp.shaderlod.integration;

import com.seibel.distanthorizons.api.DhApi;

final class DhApiObservation {
  private DhApiObservation() {}

  static Boolean enabled() {
    var config = DhApi.Delayed.configs;
    return config == null ? null : config.graphics().renderingEnabled().getValue();
  }

  static java.util.Map<String, Object> settings() {
    var config = DhApi.Delayed.configs;
    var values = new java.util.TreeMap<String, Object>();
    if (config == null) {
      values.put("state", "unknown: DH API not initialized");
      return values;
    }
    var graphics = config.graphics();
    values.put("rendering_enabled", graphics.renderingEnabled().getValue());
    values.put("chunk_render_distance", graphics.chunkRenderDistance().getValue());
    values.put("rendering_mode", String.valueOf(graphics.renderingMode().getValue()));
    values.put(
        "max_horizontal_resolution", String.valueOf(graphics.maxHorizontalResolution().getValue()));
    values.put("vertical_quality", String.valueOf(graphics.verticalQuality().getValue()));
    values.put("horizontal_quality", String.valueOf(graphics.horizontalQuality().getValue()));
    values.put("transparency", String.valueOf(graphics.transparency().getValue()));
    values.put("overdraw_prevention_radius", graphics.overdrawPreventionRadius().getValue());
    values.put("lod_shading", String.valueOf(graphics.lodShading().getValue()));
    values.put("cave_culling", graphics.caveCullingEnabled().getValue());
    values.put("lod_only", graphics.lodOnlyMode().getValue());
    var generator = config.worldGenerator();
    values.put("generator_plan", String.valueOf(generator.GeneratorPlan().getValue()));
    values.put("chunk_generator_mode", String.valueOf(generator.chunkGeneratorMode().getValue()));
    return values;
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
