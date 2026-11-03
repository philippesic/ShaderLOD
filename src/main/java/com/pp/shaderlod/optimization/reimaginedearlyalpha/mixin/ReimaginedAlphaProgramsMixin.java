package com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin;
import java.util.*;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.*;
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
public abstract class ReimaginedAlphaProgramsMixin {
 @ModifyArgs(method="transformShaders",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void prepare(Args a,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ProgramSet set) {
  ReimaginedTemporalCompiledPrograms.prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5),a.get(7),ReimaginedEarlyAlphaRuntime.epoch());
  ReimaginedReceiverCompiledPrograms.prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5),a.get(7),ReimaginedEarlyAlphaRuntime.epoch());
  ReimaginedEarlyAlphaRuntime.prepared(source,false);
  if(source==null||source.getParent()==null||!"gbuffers_terrain".equals(source.getName())||!ReimaginedEarlyAlphaRuntime.compileSource(source.getParent().getPack()))return;
  String before=a.get(5);String next=ReimaginedAlphaInterface.prepare(before,a.get(6));
  if(next!=null&&ReimaginedAlphaInterface.candidate(before)) {
   ReimaginedEarlyAlphaRuntime.compilation().prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),before,a.get(7),ReimaginedEarlyAlphaRuntime.epoch());
   a.set(5,next);
  }
 }
 @Inject(method="transformShaders",at=@At("RETURN"),require=1)
 private void actual(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
  ReimaginedTemporalCompiledPrograms.transformed(source,set,alpha,ci.getReturnValue());
  ReimaginedReceiverCompiledPrograms.transformed(source,set,alpha,ci.getReturnValue());
  if(source==null||source.getParent()==null||!"gbuffers_terrain".equals(source.getName())||!ReimaginedEarlyAlphaRuntime.compileSource(source.getParent().getPack()))return;
  boolean accepted=ReimaginedAlphaInterface.transformed(ci.getReturnValue().get(PatchShaderType.FRAGMENT),alpha);
  ReimaginedEarlyAlphaRuntime.prepared(source,accepted);
  ReimaginedEarlyAlphaRuntime.compilation().transformed(source,set,alpha,ci.getReturnValue(),accepted);
 }
 @Inject(method="createGlShaders",at=@At("RETURN"),require=1)
 private void compiled(String name,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,GlShader>> ci) {
  ReimaginedTemporalCompiledPrograms.compiled(input,ci.getReturnValue());
  ReimaginedReceiverCompiledPrograms.compiled(input,ci.getReturnValue());
  ReimaginedEarlyAlphaRuntime.compilation().compiled(input,ci.getReturnValue());
 }
 @ModifyReturnValue(method="createShader",at=@At("RETURN"),require=1)
 private GlProgram<?> own(GlProgram<?> value,@Local(argsOnly=true)IrisRenderingPipeline pipeline,
         @Local(argsOnly=true)SodiumPrograms.Pass pass,@Local(argsOnly=true)ProgramSource source,
         @Local(argsOnly=true)AlphaTest alpha,@Local(argsOnly=true)Map<PatchShaderType,GlShader> shaders) {
  if(value!=null){
   com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.register(pipeline,value);
   ReimaginedEarlyAlphaRuntime.ownTemporalNative(pipeline,pass,source,value,alpha,ReimaginedTemporalCompiledPrograms.linked(source,alpha,shaders));
   ReimaginedEarlyAlphaRuntime.ownReceiverCompiled(pipeline,pass,source,alpha,shaders,value);
   ReimaginedEarlyAlphaRuntime.ownNative(pipeline,pass,source,value,ReimaginedEarlyAlphaRuntime.compilation().linked(source,alpha,shaders));
  }
  return value;
 }
}
