package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.WorkTraceEvidence;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned Iris draw completion, before the caller changes framebuffer/program/viewport. */
@Mixin(value = FullScreenQuadRenderer.class, remap = false)
public abstract class WorkTraceReadbackMixin {
    @Inject(method = "renderQuad()V", at = @At("TAIL"), require = 1, remap = false)
    private void shaderlod$readRawWorkTrace(CallbackInfo callback) {
        WorkTraceEvidence.afterQuadDraw();
    }
}
