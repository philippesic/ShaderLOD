package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CloudSamplerStateProbe;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned Iris 1.8.12 full-screen draw, after Program.use() has updated samplers. */
@Mixin(value = FullScreenQuadRenderer.class, remap = false)
public abstract class CloudSamplerStateMixin {
    @Inject(method = "renderQuad()V", at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$observeBoundSamplers(CallbackInfo callback) {
        CloudSamplerStateProbe.beforeQuadDraw();
    }
}
