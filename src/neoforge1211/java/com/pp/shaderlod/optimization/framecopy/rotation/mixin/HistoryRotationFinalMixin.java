package com.pp.shaderlod.optimization.framecopy.rotation.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationRuntime;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPreparation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.pipeline.FinalPassRenderer", remap = false)
public abstract class HistoryRotationFinalMixin {
    @Inject(method = "destroy()V", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$destroy(CallbackInfo ci) { HistoryRotationPreparation.destroyed(this); }
    @Inject(method = "recalculateSwapPassSize", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$recalculate(CallbackInfo ci) { HistoryRotationPreparation.beforeRecalculate(this); }
    @Inject(method = "renderFinalPass()V", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$begin(CallbackInfo ci) { HistoryRotationRuntime.beginFinal(this); }
    // Select only the value consumed by renderFinalPass's iterator. The field stays canonical.
    @ModifyExpressionValue(method = "renderFinalPass()V", at = @At(value = "FIELD", target =
        "Lnet/irisshaders/iris/pipeline/FinalPassRenderer;swapPasses:Lcom/google/common/collect/ImmutableList;"), require = 1, allow = 1)
    private ImmutableList<?> rotation$list(ImmutableList<?> original) {
        return HistoryRotationRuntime.selectSwapList(this, original);
    }
    @WrapOperation(method = "renderFinalPass()V", at = @At(value = "INVOKE", target =
        "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"), require = 1, allow = 1)
    private void rotation$copy(int target, int level, int xo, int yo, int x, int y, int width, int height, Operation<Void> original) {
        HistoryRotationRuntime.beforeNativeCopy(this, target, level, xo, yo, x, y, width, height);
        try { original.call(target, level, xo, yo, x, y, width, height); }
        catch (RuntimeException | Error e) { HistoryRotationRuntime.nativeCopyFailed(this, e); throw e; }
        HistoryRotationRuntime.nativeCopyReturned(this);
    }
    @Inject(method = "renderFinalPass()V", at = @At("RETURN"), require = 1, allow = 1)
    private void rotation$end(CallbackInfo ci) { HistoryRotationRuntime.finalReturned(this); }
}
