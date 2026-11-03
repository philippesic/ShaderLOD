package com.pp.shaderlod.optimization.makeupprime.mixin;
import com.pp.shaderlod.optimization.makeupprime.DepthProgramAccess;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class DepthPipelineLifetimeMixin {
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)
 private void shaderlod$makeupdepthdelete(CallbackInfo ci) {
  com.pp.shaderlod.optimization.makeupprime.C01Prefix.stop();
  com.pp.shaderlod.optimization.makeupprime.C01Bounds.stop();
  var programs=((IrisRenderingPipeline)(Object)this).getSodiumPrograms();
  if(programs instanceof DepthProgramAccess depth)depth.shaderlod$makeupdepthdeleteDepthPrograms();
  if(!com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.COMPILED.hasSessions())com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.rearm();
 }
}
