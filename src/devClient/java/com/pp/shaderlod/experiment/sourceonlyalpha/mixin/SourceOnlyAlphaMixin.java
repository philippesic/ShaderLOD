package com.pp.shaderlod.experiment.sourceonlyalpha.mixin;
import java.util.Map;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.programs.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class SourceOnlyAlphaMixin {
 @Inject(method="transformShaders",at=@At("RETURN"),cancellable=true,require=1,order=2100)
 private void sourceonly(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci){
  var stages=com.pp.shaderlod.experiment.sourceonlyalpha.SourceOnlyAlphaR23.adapt(source,alpha,ci.getReturnValue());
  ci.setReturnValue(com.pp.shaderlod.experiment.sourceonlyalpha.BslPostSampleAlphaR1.adapt(source,alpha,set,stages));
 }
}
