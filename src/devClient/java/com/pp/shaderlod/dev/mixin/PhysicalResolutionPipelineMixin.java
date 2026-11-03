package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.PhysicalResolutionTracker;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Normal RETURN is later than full-clear pass execution in pinned Iris. No GPU completion claim. */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class PhysicalResolutionPipelineMixin {
    @Shadow @Final private RenderTargets renderTargets;
    @Inject(method = "<init>", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$constructorCompleted(CallbackInfo callback) {
        PhysicalResolutionTracker.constructorCompleted(this, renderTargets);
    }
    @Inject(method = "beginLevelRendering()V", at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$begin(CallbackInfo callback) {
        PhysicalResolutionTracker.begin(this, renderTargets);
    }
    @Inject(method = "beginLevelRendering()V", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$completed(CallbackInfo callback) {
        PhysicalResolutionTracker.beginCompleted(this, renderTargets);
    }
}
