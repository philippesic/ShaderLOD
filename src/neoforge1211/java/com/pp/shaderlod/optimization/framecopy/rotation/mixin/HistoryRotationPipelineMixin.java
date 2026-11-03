package com.pp.shaderlod.optimization.framecopy.rotation.mixin;

import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPreparation;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact pinned Iris entry: native resize/clear/setup precedes the single beginRenderer graph call. */
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class HistoryRotationPipelineMixin {
    @Shadow @Final private RenderTargets renderTargets;
    @Inject(method = "<init>", at = @At("RETURN"), require = 1, allow = 1)
    private void rotation$constructed(CallbackInfo ci) { HistoryRotationPreparation.nativeClearCompleted(renderTargets); }
    @Inject(method = "beginLevelRendering()V", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$beforeFrame(CallbackInfo ci) { HistoryRotationPreparation.beforeFrame(this); }
    @Inject(method = "beginLevelRendering()V", at = @At(value = "INVOKE", target =
        "Lnet/irisshaders/iris/pipeline/CompositeRenderer;renderAll()V"), require = 1, allow = 1)
    private void rotation$afterClear(CallbackInfo ci) { HistoryRotationPreparation.afterClearBeforeGraph(this, renderTargets); }
    @Inject(method = "destroy()V", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$destroy(CallbackInfo ci) { HistoryRotationPreparation.destroyed(this); }
}
