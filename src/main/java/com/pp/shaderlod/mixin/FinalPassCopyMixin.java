package com.pp.shaderlod.mixin;

import com.google.common.collect.ImmutableList;
import com.pp.shaderlod.optimization.framecopy.FrameCopyPins;
import com.pp.shaderlod.optimization.framecopy.FrameCopyPlan;
import com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive exact-call-site observer. No operation wrapper/boxing or native argument changes. */
@Mixin(value = FinalPassRenderer.class, remap = false)
public abstract class FinalPassCopyMixin {
    @Shadow @Final private RenderTargets renderTargets;
    @Shadow @Final private ImmutableList<?> swapPasses;
    @Unique private FrameCopyPlan shaderlod$copyPlan;
    @Unique private int shaderlod$swapWidth, shaderlod$swapHeight, shaderlod$fallbackWidth, shaderlod$fallbackHeight;

    @Inject(method = {"<init>", "recalculateSwapPassSize"}, at = @At("RETURN"), require = 0, remap = false)
    private void shaderlod$copyPlan(CallbackInfo ci) {
        if (FrameCopyPins.exactApi()) shaderlod$copyPlan = FrameCopyPlan.read(renderTargets, swapPasses);
    }
    @Inject(method = "renderFinalPass", at = @At("HEAD"), require = 0, allow = 1, remap = false)
    private void shaderlod$copyBegin(CallbackInfo ci) {
        if (!FrameCopyRuntime.active()) return;
        if (shaderlod$copyPlan == null) shaderlod$copyPlan = FrameCopyPlan.read(renderTargets, swapPasses);
        FrameCopyRuntime.beginFinal(this, shaderlod$copyPlan.targets(), shaderlod$copyPlan.status());
    }
    @Inject(method = "renderFinalPass", at = @At("RETURN"), require = 0, allow = 1, remap = false)
    private void shaderlod$copyEnd(CallbackInfo ci) { FrameCopyRuntime.endFinal(); }

    @ModifyArg(method = "renderFinalPass", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"), index = 6, require = 0, allow = 1, remap = false)
    private int shaderlod$copySwapWidth(int width) {
        if (FrameCopyRuntime.active()) shaderlod$swapWidth = width; return width;
    }
    @ModifyArg(method = "renderFinalPass", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"), index = 7, require = 0, allow = 1, remap = false)
    private int shaderlod$copySwapHeight(int height) {
        if (FrameCopyRuntime.active()) shaderlod$swapHeight = height; return height;
    }
    @Inject(method = "renderFinalPass", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V", shift = At.Shift.AFTER), require = 0, allow = 1, remap = false)
    private void shaderlod$copySwapReturned(CallbackInfo ci) {
        FrameCopyRuntime.swapExecuted(shaderlod$swapWidth, shaderlod$swapHeight);
    }
    @ModifyArg(method = "renderFinalPass", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/gl/IrisRenderSystem;copyTexSubImage2D(IIIIIIIII)V"), index = 7, require = 0, allow = 1, remap = false)
    private int shaderlod$copyFallbackWidth(int width) {
        if (FrameCopyRuntime.active()) shaderlod$fallbackWidth = width; return width;
    }
    @ModifyArg(method = "renderFinalPass", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/gl/IrisRenderSystem;copyTexSubImage2D(IIIIIIIII)V"), index = 8, require = 0, allow = 1, remap = false)
    private int shaderlod$copyFallbackHeight(int height) {
        if (FrameCopyRuntime.active()) shaderlod$fallbackHeight = height; return height;
    }
    @Inject(method = "renderFinalPass", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/gl/IrisRenderSystem;copyTexSubImage2D(IIIIIIIII)V", shift = At.Shift.AFTER), require = 0, allow = 1, remap = false)
    private void shaderlod$copyFallbackReturned(CallbackInfo ci) {
        FrameCopyRuntime.fallbackExecuted(shaderlod$fallbackWidth, shaderlod$fallbackHeight);
    }
}
