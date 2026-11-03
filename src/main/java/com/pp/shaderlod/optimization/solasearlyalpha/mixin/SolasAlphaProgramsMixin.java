package com.pp.shaderlod.optimization.solasearlyalpha.mixin;
import java.util.*;
import com.pp.shaderlod.optimization.solasearlyalpha.*;
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
public abstract class SolasAlphaProgramsMixin {
 @ModifyArgs(method="transformShaders",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void prepare(Args a,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ProgramSet set) {
  SolasEarlyAlphaRuntime.prepared(source,false);
  if(source==null||source.getParent()==null||!"gbuffers_terrain".equals(source.getName())||!SolasEarlyAlphaRuntime.source(source.getParent().getPack()))return;
  String before=a.get(5);String next=SolasAlphaInterface.prepare(before,a.get(6));
  if(next!=null&&SolasAlphaInterface.candidate(before)) {
   SolasEarlyAlphaRuntime.compilation().prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),before,a.get(7),SolasEarlyAlphaRuntime.epoch());
   a.set(5,next);
  }
 }
 @Inject(method="transformShaders",at=@At("RETURN"),require=1)
 private void actual(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
  if(source==null||source.getParent()==null||!"gbuffers_terrain".equals(source.getName())||!SolasEarlyAlphaRuntime.source(source.getParent().getPack()))return;
  boolean accepted=SolasAlphaInterface.transformed(ci.getReturnValue().get(PatchShaderType.FRAGMENT),alpha);
  SolasEarlyAlphaRuntime.prepared(source,accepted);
  SolasEarlyAlphaRuntime.compilation().transformed(source,set,alpha,ci.getReturnValue(),accepted);
 }
 @Inject(method="createGlShaders",at=@At("RETURN"),require=1)
 private void compiled(String name,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,GlShader>> ci) {
  SolasEarlyAlphaRuntime.compilation().compiled(input,ci.getReturnValue());
 }
 @ModifyReturnValue(method="createShader",at=@At("RETURN"),require=1)
 private GlProgram<?> own(GlProgram<?> value,@Local(argsOnly=true)IrisRenderingPipeline pipeline,
         @Local(argsOnly=true)SodiumPrograms.Pass pass,@Local(argsOnly=true)ProgramSource source,
         @Local(argsOnly=true)AlphaTest alpha,@Local(argsOnly=true)Map<PatchShaderType,GlShader> shaders) {
  if(value!=null){SolasEarlyAlphaRuntime.own(pipeline,pass,source,value.handle(),SolasEarlyAlphaRuntime.compilation().linked(source,alpha,shaders));com.pp.shaderlod.optimization.solasspatial.SolasSpatialRuntime.own(pipeline,pass,source,alpha,value.handle());}
  return value;
 }
}
