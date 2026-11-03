package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.PhysicalResolutionTracker;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit Iris 1.8.12 seam; plugin must exclude this mixin unless diagnostic enabled. */
@Mixin(value = RenderTargets.class, remap = false)
public abstract class PhysicalResolutionTargetsMixin {
    @Shadow private int cachedDepthBufferVersion;
    @Shadow private DepthBufferFormat currentDepthFormat;
    @Unique private boolean shaderlod$depthRebound;
    @Unique private boolean shaderlod$depthFormatChanged;
    @Unique private boolean shaderlod$targetMissing;
    @Inject(method = "getOrCreate(I)Lnet/irisshaders/iris/targets/RenderTarget;",
            at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$beforeCreate(int index,
            CallbackInfoReturnable<net.irisshaders.iris.targets.RenderTarget> callback) {
        if (PhysicalResolutionTracker.enabled()) shaderlod$targetMissing = ((RenderTargets)(Object)this).get(index) == null;
    }
    @Inject(method = "getOrCreate(I)Lnet/irisshaders/iris/targets/RenderTarget;",
            at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$afterCreate(int index,
            CallbackInfoReturnable<net.irisshaders.iris.targets.RenderTarget> callback) {
        if (PhysicalResolutionTracker.enabled() && shaderlod$targetMissing && callback.getReturnValue() != null)
            PhysicalResolutionTracker.targetCreated((RenderTargets)(Object)this, index);
    }
    @Inject(method = "resizeIfNeeded(IIIILnet/irisshaders/iris/gl/texture/DepthBufferFormat;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)Z",
            at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$beforeResize(int depthVersion, int depthTexture, int width, int height,
            DepthBufferFormat format, PackDirectives directives, CallbackInfoReturnable<Boolean> callback) {
        if (!PhysicalResolutionTracker.enabled()) return;
        shaderlod$depthRebound = cachedDepthBufferVersion != depthVersion;
        shaderlod$depthFormatChanged = currentDepthFormat != format;
    }
    @Inject(method = "resizeIfNeeded(IIIILnet/irisshaders/iris/gl/texture/DepthBufferFormat;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)Z",
            at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$afterResize(int depthVersion, int depthTexture, int width, int height,
            DepthBufferFormat format, PackDirectives directives, CallbackInfoReturnable<Boolean> callback) {
        if (!PhysicalResolutionTracker.enabled()) return;
        boolean resized = callback.getReturnValueZ();
        PhysicalResolutionTracker.resized((RenderTargets)(Object)this, width, height, resized,
                resized || shaderlod$depthFormatChanged, shaderlod$depthRebound);
    }
    @Inject(method = "onFullClear()V", at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$clearSelected(CallbackInfo callback) {
        PhysicalResolutionTracker.clearSelected((RenderTargets)(Object)this);
    }
}
