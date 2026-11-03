package com.pp.shaderlod.experiment.makeup.mixin;
import java.util.EnumMap;
import java.util.Map;
import com.pp.shaderlod.experiment.makeup.MakeupEarlyAlphaR1;
import com.pp.shaderlod.experiment.makeup.MakeupAlphaInterface;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class MakeupAlphaProgramsMixin {
 @Inject(method="transformShaders",at=@At("RETURN"),cancellable=true,require=1)
 private void shaderlod$nativeInterface(ProgramSource source,AlphaTest alpha,ProgramSet set,
     CallbackInfoReturnable<Map<PatchShaderType,String>> ci){
  MakeupEarlyAlphaR1.clearInterface();
  if(!MakeupEarlyAlphaR1.packPinned() || !"gbuffers_terrain".equals(source.getName()))return;
  var stages=ci.getReturnValue(); String fragment=stages.get(PatchShaderType.FRAGMENT);
  String adapted=MakeupAlphaInterface.adapt(fragment,alpha); if(adapted==null)return;
  var copy=new EnumMap<PatchShaderType,String>(PatchShaderType.class);copy.putAll(stages);copy.put(PatchShaderType.FRAGMENT,adapted);
  MakeupEarlyAlphaR1.admitCurrentPack(alpha.function()!=net.irisshaders.iris.gl.blending.AlphaTestFunction.ALWAYS);ci.setReturnValue(copy);
 }
}
