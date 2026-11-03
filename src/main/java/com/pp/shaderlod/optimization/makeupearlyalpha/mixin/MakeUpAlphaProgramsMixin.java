package com.pp.shaderlod.optimization.makeupearlyalpha.mixin;
import java.util.*;
import com.pp.shaderlod.optimization.makeupshadow.*;
import com.pp.shaderlod.optimization.makeupearlyalpha.*;
import com.pp.shaderlod.optimization.makeuppresence.MakeUpPresencePrograms;
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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class MakeUpAlphaProgramsMixin {
 @ModifyArgs(method="transformShaders",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void prepare(Args a,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ProgramSet set) {
  MakeUpCompiledPrograms.discardPreparation(source);
  var shadowReceipt=MakeUpShadowCompiledPrograms.prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5),a.get(7),MakeUpShadowRuntime.epoch());
  var presence=MakeUpPresencePrograms.prepare(shadowReceipt,source,set,a.get(6),a.get(5));
  a.set(5,MakeUpShadowCompiledPrograms.specializeOpaque(shadowReceipt,source,set,a.get(6),a.get(5)));
  if(presence!=null)a.set(5,presence.fragment());
  MakeUpEarlyAlphaRuntime.prepared(source,false);
  AlphaTest actualAlpha=a.get(6);
  if(actualAlpha!=null&&actualAlpha.function()==net.irisshaders.iris.gl.blending.AlphaTestFunction.ALWAYS)return;
  if(!MakeUpEarlyAlphaCompilation.enabled(source,set))return;
  if(source==null||source.getParent()==null||!"gbuffers_terrain".equals(source.getName())||!MakeUpEarlyAlphaRuntime.source(source.getParent().getPack()))return;
  String before=a.get(5);String next=MakeUpAlphaInterface.prepare(before,a.get(6));
  if(next!=null&&MakeUpAlphaInterface.candidate(before)) {
   MakeUpCompiledPrograms.prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),before,a.get(7),MakeUpEarlyAlphaRuntime.epoch());
   a.set(5,next);
  }
 }
 @WrapOperation(method="transformShaders",order=1200,at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private Map<PatchShaderType,String> patch(String name,String vertex,String geometry,String control,
         String evaluation,String fragment,AlphaTest alpha,Object2ObjectMap<?,?> textures,
         Operation<Map<PatchShaderType,String>> original,
         @Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ProgramSet set) {
  var receipt=MakeUpPresencePrograms.consume(source,set,alpha,vertex,geometry,control,evaluation,fragment,textures);
  try {
   var output=original.call(name,vertex,geometry,control,evaluation,fragment,alpha,textures);
   MakeUpPresencePrograms.patched(source,receipt,output);
   return output;
  } catch(RuntimeException|Error failure) {
   MakeUpPresencePrograms.failed(source);
   MakeUpShadowCompiledPrograms.failed(source);
   MakeUpCompiledPrograms.discardPreparation(source);
   throw failure;
  }
 }
 @Inject(method="transformShaders",at=@At("RETURN"),cancellable=true,require=1)
 private void actual(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
  boolean presence=MakeUpPresencePrograms.transformed(source,set,alpha,ci.getReturnValue());
  MakeUpShadowCompiledPrograms.transformed(source,set,alpha,ci.getReturnValue(),presence);
  var postSample=MakeUpPostSampleAlpha.adapt(source,alpha,set,ci.getReturnValue());
  if(postSample!=ci.getReturnValue()) {
   MakeUpEarlyAlphaRuntime.prepared(source,false);
   MakeUpCompiledPrograms.discardPreparation(source);
   ci.setReturnValue(postSample);
   return;
  }
  if(!MakeUpEarlyAlphaCompilation.enabled(source,set)){MakeUpEarlyAlphaRuntime.prepared(source,false);MakeUpCompiledPrograms.discardPreparation(source);return;}
  if(source==null||source.getParent()==null||!"gbuffers_terrain".equals(source.getName())||!MakeUpEarlyAlphaRuntime.source(source.getParent().getPack()))return;
  boolean accepted=MakeUpAlphaInterface.transformed(ci.getReturnValue().get(PatchShaderType.FRAGMENT),alpha);
  MakeUpEarlyAlphaRuntime.prepared(source,accepted);
  MakeUpCompiledPrograms.transformed(source,set,alpha,ci.getReturnValue(),accepted);
 }
 @Inject(method="createGlShaders",at=@At("RETURN"),require=1)
 private void compiled(String name,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,GlShader>> ci) {
  MakeUpPostSampleAlphaWitness.compiled(input,ci.getReturnValue());
  MakeUpCompiledPrograms.compiled(input,ci.getReturnValue());
  MakeUpShadowCompiledPrograms.compiled(input,ci.getReturnValue());
  MakeUpPresencePrograms.compiled(input,ci.getReturnValue());
 }
 @ModifyReturnValue(method="createShader",at=@At("RETURN"),require=1)
 private GlProgram<?> own(GlProgram<?> value,@Local(argsOnly=true)IrisRenderingPipeline pipeline,
         @Local(argsOnly=true)SodiumPrograms.Pass pass,@Local(argsOnly=true)ProgramSource source,
         @Local(argsOnly=true)AlphaTest alpha,@Local(argsOnly=true)Map<PatchShaderType,GlShader> shaders) {
  MakeUpPostSampleAlphaWitness.linked(pipeline,pass,source,shaders,value);
  boolean presence=MakeUpPresencePrograms.linked(pipeline,pass,source,alpha,shaders,value);
  if(value!=null)com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.register(pipeline,value);
  if(value!=null) {
   var shadow=MakeUpShadowCompiledPrograms.linked(source,alpha,shaders);
   MakeUpShadowRuntime.ownCompiled(pipeline,pass,source,value,alpha,presence?shadow:null);
  }
  if(value!=null)MakeUpEarlyAlphaRuntime.ownLinked(pipeline,pass,source,value,MakeUpCompiledPrograms.linked(source,alpha,shaders));
  return value;
 }
}
