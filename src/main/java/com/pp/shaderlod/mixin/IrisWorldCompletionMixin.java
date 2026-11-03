package com.pp.shaderlod.mixin;

import com.pp.shaderlod.CoreUniformFrame;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iris 1.8.12: normal return follows compositeRenderer and finalPassRenderer. */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class IrisWorldCompletionMixin {
    @Shadow @Final private FrameUpdateNotifier updateNotifier;

    @Inject(method = "finalizeLevelRendering()V", at = @At("RETURN"), remap = false, require = 1)
    private void shaderlod$completeWorld(CallbackInfo ci) {
        CoreUniformFrame.completeForNotifier(updateNotifier, SystemTimeUniforms.COUNTER.getAsInt());
    }
}
