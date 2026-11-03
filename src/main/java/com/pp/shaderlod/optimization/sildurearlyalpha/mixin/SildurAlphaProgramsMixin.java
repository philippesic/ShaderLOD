package com.pp.shaderlod.optimization.sildurearlyalpha.mixin;
import java.util.*;
import com.pp.shaderlod.optimization.sildurearlyalpha.*;
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
public abstract class SildurAlphaProgramsMixin {
 @ModifyArgs(method="transformShaders",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void prepare(Args a,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ProgramSet set) {
  SildurEarlyAlphaRuntime.prepared(source,false);
  SildurAlphaAbsence.discard(source);
  if(source==null||source.getParent()==null||!"gbuffers_textured".equals(source.getName())||!SildurEarlyAlphaRuntime.source(source.getParent().getPack()))return;
  String before=a.get(5);
  String absence=SildurAlphaInterface.absent(before);
  if(absence!=null&&SildurAlphaAbsence.Request.capture()!=null){
   var prepared=SildurEarlyAlphaRuntime.compilation().prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),before,a.get(7),SildurEarlyAlphaRuntime.epoch());
   SildurAlphaAbsence.begin(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),before,absence,a.get(7),prepared);
   if(SildurAlphaAbsence.pending(source)){a.set(5,absence);return;}
  }
  String next=SildurAlphaInterface.prepare(before,a.get(6));
  if(next!=null&&SildurAlphaInterface.candidate(before)) {
   SildurEarlyAlphaRuntime.compilation().prepare(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),before,a.get(7),SildurEarlyAlphaRuntime.epoch());
   a.set(5,next);
  }
 }
 @ModifyArgs(method="transformShaders",order=1200,at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
 private void consumed(Args a,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ProgramSet set) {
  if(!SildurAlphaAbsence.pending(source))return;
  String raw=SildurAlphaAbsence.rawForRevert(source,a.get(5));
  if(!SildurAlphaAbsence.arguments(source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5),a.get(7))) {
   if(raw!=null)a.set(5,raw);
   else throw new IllegalStateException("Sildur alpha absence consumed arguments changed");
  }
 }
 @Inject(method="transformShaders",at=@At("RETURN"),require=1)
 private void actual(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci) {
  SildurAlphaAbsence.transformed(source,set,alpha,ci.getReturnValue());
  if(source==null||source.getParent()==null||!"gbuffers_textured".equals(source.getName())||!SildurEarlyAlphaRuntime.source(source.getParent().getPack()))return;
  boolean accepted=SildurAlphaInterface.transformed(ci.getReturnValue().get(PatchShaderType.FRAGMENT),alpha);
  SildurEarlyAlphaRuntime.prepared(source,accepted);
  SildurEarlyAlphaRuntime.compilation().transformed(source,set,alpha,ci.getReturnValue(),accepted);
 }
 @Inject(method="createGlShaders",at=@At("RETURN"),require=1)
 private void compiled(String name,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,GlShader>> ci) {
  SildurAlphaAbsence.compiled(input,ci.getReturnValue());
  SildurEarlyAlphaRuntime.compilation().compiled(input,ci.getReturnValue());
 }
 @ModifyReturnValue(method="createShader",at=@At("RETURN"),require=1)
 private GlProgram<?> own(GlProgram<?> value,@Local(argsOnly=true)IrisRenderingPipeline pipeline,
         @Local(argsOnly=true)SodiumPrograms.Pass pass,@Local(argsOnly=true)ProgramSource source,
         @Local(argsOnly=true)AlphaTest alpha,@Local(argsOnly=true)Map<PatchShaderType,GlShader> shaders) {
  var absent=SildurAlphaAbsence.compiledAbsence(source,alpha,shaders);
  if(value==null&&absent!=null)throw new IllegalStateException("Sildur alpha absence native link missing");
  if(value!=null){
   var on=SildurEarlyAlphaRuntime.compilation().linked(source,alpha,shaders);
   if(pass==SodiumPrograms.Pass.TERRAIN_CUTOUT&&(absent!=null||on!=null)&&
      org.lwjgl.opengl.GL20C.glGetProgrami(value.handle(),org.lwjgl.opengl.GL20C.GL_LINK_STATUS)==org.lwjgl.opengl.GL11C.GL_TRUE)
    SildurAlphaAbsence.clearProgram(pipeline);
   if(absent!=null&&source!=null&&source.getParent()!=null&&pass==SodiumPrograms.Pass.TERRAIN_CUTOUT&&
      !SildurAlphaAbsence.recordProgram(pipeline,source.getParent().getPack(),value.handle(),absent,shaders))
    throw new IllegalStateException("Sildur alpha absence native program receipt refused");
   SildurEarlyAlphaRuntime.own(pipeline,pass,source,value.handle(),on);
  }
  return value;
 }
}
