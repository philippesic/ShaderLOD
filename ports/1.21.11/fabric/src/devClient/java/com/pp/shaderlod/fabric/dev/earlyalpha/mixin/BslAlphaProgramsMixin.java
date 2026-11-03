package com.pp.shaderlod.fabric.dev.earlyalpha.mixin;
import java.util.EnumMap;
import java.util.Map;
import com.pp.shaderlod.fabric.dev.earlyalpha.BslEarlyAlpha;
import com.pp.shaderlod.fabric.dev.earlyalpha.BslAlphaInterface;
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
public abstract class BslAlphaProgramsMixin {
 @org.spongepowered.asm.mixin.injection.ModifyArgs(method="transformShaders",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1,remap=false)
 private void shaderlod$beforePruning(org.spongepowered.asm.mixin.injection.invoke.arg.Args args){
  if(!BslEarlyAlpha.packPinned()||!"gbuffers_terrain".equals(args.<String>get(0)))return;
  String raw=args.get(5);AlphaTest alpha=args.get(6);String prepared=BslAlphaInterface.prepare(raw,alpha);
  if(prepared!=null)args.set(5,prepared);
 }

 @Inject(method="transformShaders",at=@At("RETURN"),cancellable=true,require=1)
 private void shaderlod$nativeInterface(ProgramSource source,AlphaTest alpha,ProgramSet set,
     CallbackInfoReturnable<Map<PatchShaderType,String>> ci){
  
  if(!BslEarlyAlpha.packPinned() || !"gbuffers_terrain".equals(source.getName()))return;
  if(alpha.function()!=net.irisshaders.iris.gl.blending.AlphaTestFunction.ALWAYS && (alpha.function()!=net.irisshaders.iris.gl.blending.AlphaTestFunction.GREATER || alpha.reference()!=0.5f))return;
  var stages=ci.getReturnValue(); String fragment=stages.get(PatchShaderType.FRAGMENT);
  String adapted=BslAlphaInterface.adapt(fragment,alpha,BslAlphaInterface.candidate(source.getFragmentSource().orElse(null))); if(adapted==null)return;
  var copy=new EnumMap<PatchShaderType,String>(PatchShaderType.class);copy.putAll(stages);copy.put(PatchShaderType.FRAGMENT,adapted);
  BslEarlyAlpha.built(alpha.function()!=net.irisshaders.iris.gl.blending.AlphaTestFunction.ALWAYS);ci.setReturnValue(copy);
 }
}
