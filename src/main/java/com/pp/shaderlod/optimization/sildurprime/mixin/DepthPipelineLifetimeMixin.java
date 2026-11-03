package com.pp.shaderlod.optimization.sildurprime.mixin;
import com.pp.shaderlod.optimization.sildurprime.DepthProgramAccess;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class DepthPipelineLifetimeMixin {
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)
 private void shaderlod$sildurdelete(CallbackInfo ci) {
  var programs=((IrisRenderingPipeline)(Object)this).getSodiumPrograms();
  if(programs instanceof DepthProgramAccess depth)depth.shaderlod$sildurdeleteDepthPrograms();
  if(!com.pp.shaderlod.optimization.sildurprime.SildurDepthPriming.COMPILED.hasSessions())com.pp.shaderlod.optimization.sildurprime.SildurDepthPriming.rearm();
 }
}
