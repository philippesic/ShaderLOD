package com.pp.shaderlod.optimization.framecopy.rotation.mixin;

import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationRuntime;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPreparation;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.targets.RenderTargets", remap = false)
public abstract class HistoryRotationTargetsMixin {
    @Inject(method = "onFullClear()V", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$clear(CallbackInfo ci) { HistoryRotationPreparation.beforeFullClear(this); }
    @Inject(method = "resizeIfNeeded", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$resize(int depthVersion, int depthTexture, int width, int height,
                                 DepthBufferFormat format, PackDirectives directives, CallbackInfoReturnable<Boolean> ci) {
        HistoryRotationPreparation.beforeResize(this, depthVersion, depthTexture, width, height, format);
        HistoryRotationRuntime.beforeResize(this, width, height);
    }
    @Inject(method = "destroy()V", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$destroy(CallbackInfo ci) { HistoryRotationPreparation.targetsDestroyed(this); }
}
