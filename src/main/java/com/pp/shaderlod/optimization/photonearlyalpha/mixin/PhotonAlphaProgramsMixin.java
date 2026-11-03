package com.pp.shaderlod.optimization.photonearlyalpha.mixin;
import java.util.*;
import com.pp.shaderlod.optimization.photonearlyalpha.*;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.programs.*;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class PhotonAlphaProgramsMixin {
 @ModifyArgs(method="transformShaders",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void prepare(Args a,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ProgramSet set) {
  PhotonEarlyAlphaRuntime.prepared(source,false);
  if(source==null||source.getParent()==null||!"gbuffers_terrain".equals(source.getName())||!PhotonEarlyAlphaRuntime.source(source.getParent().getPack()))return;
  String before=a.get(5);String next=PhotonAlphaInterface.prepare(before,a.get(6));
  if(next!=null&&PhotonAlphaInterface.candidate(before)) {
   PhotonEarlyAlphaRuntime.compilation().prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),before,a.get(7),PhotonEarlyAlphaRuntime.epoch());
   a.set(5,next);
  }
 }
 @Inject(method="transformShaders",at=@At("RETURN"),require=1)
 private void actual(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
  if(source==null||source.getParent()==null||!"gbuffers_terrain".equals(source.getName())||!PhotonEarlyAlphaRuntime.source(source.getParent().getPack()))return;
  boolean accepted=PhotonAlphaInterface.transformed(ci.getReturnValue().get(PatchShaderType.FRAGMENT),alpha);
  PhotonEarlyAlphaRuntime.prepared(source,accepted);
  PhotonEarlyAlphaRuntime.compilation().transformed(source,set,alpha,ci.getReturnValue(),accepted);
 }
 @Inject(method="createGlShaders",at=@At("RETURN"),require=1)
 private void compiled(String name,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,GlShader>> ci) {
  PhotonEarlyAlphaRuntime.compilation().compiled(input,ci.getReturnValue());
 }
 @ModifyReturnValue(method="createShader",at=@At("RETURN"),require=1)
 private GlProgram<?> own(GlProgram<?> value,@Local(argsOnly=true)IrisRenderingPipeline pipeline,
         @Local(argsOnly=true)SodiumPrograms.Pass pass,@Local(argsOnly=true)ProgramSource source,
         @Local(argsOnly=true)AlphaTest alpha,@Local(argsOnly=true)Map<PatchShaderType,GlShader> shaders) {
  if(value!=null){PhotonEarlyAlphaRuntime.own(pipeline,pass,source,value.handle(),PhotonEarlyAlphaRuntime.compilation().linked(source,alpha,shaders));}
  return value;
 }
}
