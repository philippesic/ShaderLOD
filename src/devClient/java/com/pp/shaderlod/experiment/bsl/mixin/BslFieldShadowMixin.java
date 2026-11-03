package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ShadowRenderer.class,remap=false)
public abstract class BslFieldShadowMixin {
 @Shadow @Final private IrisRenderingPipeline pipeline;
 @Inject(method="renderShadows",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/shadows/ShadowCompositeRenderer;renderAll()V",shift=At.Shift.BEFORE),require=1)
 private void nativeCompletedBeforeField(CallbackInfo ci){BslShadowFieldBridge.nativeBeforeField(pipeline);}
}
