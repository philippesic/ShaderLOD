package com.pp.shaderlod.optimization.solasearlyalpha.mixin;
import com.pp.shaderlod.optimization.solasearlyalpha.*;
import net.irisshaders.iris.pipeline.programs.*;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
/** Actual 26.2 source-to-lazy-linked-program boundary. Never consumes/deletes PartialShader stages. */
@Mixin(value=ShaderCreator.class,remap=false)
public abstract class SolasAlphaProgramsMixin {
 @ModifyArgs(method="create(Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;Ljava/lang/String;Lnet/irisshaders/iris/pipeline/programs/ShaderKey;Lnet/irisshaders/iris/shaderpack/programs/ProgramSource;Lnet/irisshaders/iris/shaderpack/loading/ProgramId;Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lcom/mojang/blaze3d/vertex/VertexFormat;Lnet/irisshaders/iris/gl/state/ShaderAttributeInputs;Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;Ljava/util/function/Supplier;Lnet/irisshaders/iris/gl/state/FogMode;ZZZZLnet/irisshaders/iris/uniforms/custom/CustomUniforms;Lnet/irisshaders/iris/pipeline/transform/Patch;)Lnet/irisshaders/iris/pipeline/programs/ShaderSupplier;",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;Z)Ljava/util/Map;"),require=1,allow=1)
 private static void shaderlod$solasPrepare(Args a,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ShaderKey key,@Local(argsOnly=true)Patch patch,@Local(index=16)boolean shadow){
  if(!SolasEarlyAlphaRuntime.consumer(source,key,patch,shadow)||!Boolean.FALSE.equals(a.get(8)))return;
  String before=a.get(5),next=SolasAlphaInterface.prepare(before,a.get(6));
  if(next!=null&&SolasAlphaInterface.candidate(before))a.set(5,next);
 }
 @ModifyReturnValue(method="create(Lnet/irisshaders/iris/pipeline/WorldRenderingPipeline;Ljava/lang/String;Lnet/irisshaders/iris/pipeline/programs/ShaderKey;Lnet/irisshaders/iris/shaderpack/programs/ProgramSource;Lnet/irisshaders/iris/shaderpack/loading/ProgramId;Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lcom/mojang/blaze3d/vertex/VertexFormat;Lnet/irisshaders/iris/gl/state/ShaderAttributeInputs;Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;Ljava/util/function/Supplier;Lnet/irisshaders/iris/gl/state/FogMode;ZZZZLnet/irisshaders/iris/uniforms/custom/CustomUniforms;Lnet/irisshaders/iris/pipeline/transform/Patch;)Lnet/irisshaders/iris/pipeline/programs/ShaderSupplier;",at=@At("RETURN"),require=1,allow=1)
 private static ShaderSupplier shaderlod$solasOwn(ShaderSupplier returned,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ShaderKey key,@Local(argsOnly=true)Patch patch,@Local(index=16)boolean shadow,@Local(argsOnly=true)IrisRenderingPipeline pipeline,@Local(name="fragment")String fragment,@Local(name="alpha")AlphaTest alpha){
  if(returned==null||returned.key()!=key||!SolasEarlyAlphaRuntime.consumer(source,key,patch,shadow)||!SolasAlphaInterface.transformed(fragment,alpha))return returned;
  final var certificate=SolasEarlyAlphaRuntime.creation(pipeline,source,returned.id().program());final var nativeFactory=returned.shader();
  return new ShaderSupplier(returned.key(),returned.id(),()->{var program=nativeFactory.get();SolasEarlyAlphaRuntime.own(certificate,program);return program;});
 }
}
