package com.pp.shaderlod.integration.mixin;

import com.pp.shaderlod.integration.FrameWitness;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
@SuppressWarnings("UnusedMethod")
public abstract class IrisFrameCompletionMixin {
  @Inject(method = "finalizeLevelRendering()V", at = @At("RETURN"), require = 1, remap = false)
  private void shaderlod$submittedFrame(@SuppressWarnings("UnusedVariable") CallbackInfo callback) {
    FrameWitness.completed(this);
  }
}
