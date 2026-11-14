package com.pp.shaderlod.dev;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;

final class DhControl {
  private static long bufferRenders;
  private static boolean bound;
  private static boolean generationOverridden;

  private DhControl() {}

  static void prepare(boolean witness, boolean generation) {
    if (witness && !bound) {
      DhApi.events.bind(DhApiBeforeBufferRenderEvent.class, new BufferCounter());
      bound = true;
    }
    var config = DhApi.Delayed.configs;
    if (config != null && !generation && !generationOverridden) {
      generationOverridden =
          config
              .worldGenerator()
              .GeneratorPlan()
              .setValue(
                  com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiGeneratorPlan.DISABLED,
                  "ShaderLOD benchmark session");
    }
  }

  static void restore() {
    var config = DhApi.Delayed.configs;
    if (config != null && generationOverridden) {
      config.worldGenerator().GeneratorPlan().clearValue();
      generationOverridden = false;
    }
  }

  static boolean witnessed() {
    return bound;
  }

  static long bufferRenders() {
    return bufferRenders;
  }

  private static final class BufferCounter extends DhApiBeforeBufferRenderEvent {
    @Override
    public void beforeRender(DhApiEventParam<DhApiBeforeBufferRenderEvent.EventParam> parameter) {
      bufferRenders++;
    }
  }
}
