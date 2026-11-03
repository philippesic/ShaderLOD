package com.pp.shaderlod.experiment.sourceonlyalpha.mixin;
import java.util.Map;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.programs.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Consumes the admitted public helper return directly, before its caller can cancel Sodium's return. */
@Mixin(value=com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlpha.class,remap=false)
public abstract class MakeUpSingleAlphaMixin {
 @Inject(method="adapt",at=@At("RETURN"),cancellable=true,require=1)
 private static void single(ProgramSource source,AlphaTest alpha,ProgramSet set,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,String>> ci){
  var current=ci.getReturnValue();var next=com.pp.shaderlod.experiment.sourceonlyalpha.MakeUpSingleAlphaR1.adapt(source,alpha,set,current);
  if(next!=current)ci.setReturnValue(next);
 }
}
