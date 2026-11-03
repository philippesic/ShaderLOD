package com.pp.shaderlod.optimization.photonprime.mixin;
import com.pp.shaderlod.optimization.photonprime.DepthProgramAccess;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class DepthPipelineLifetimeMixin {
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)
 private void shaderlod$photondelete(CallbackInfo ci) {
  var programs=((IrisRenderingPipeline)(Object)this).getSodiumPrograms();
  if(programs instanceof DepthProgramAccess depth)depth.shaderlod$photondeleteDepthPrograms();
  com.pp.shaderlod.optimization.photonprime.PhotonDepthPriming.rearm();
 }
}
