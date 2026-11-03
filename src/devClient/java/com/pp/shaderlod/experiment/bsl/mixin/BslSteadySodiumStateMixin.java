package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import net.irisshaders.iris.pipeline.programs.SodiumShader;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.function.Supplier;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SodiumShader.class,remap=false)
public abstract class BslSteadySodiumStateMixin {
 @Unique private WorldRenderingPipeline shaderlod$bslPipeline;
 @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/programs/SodiumShader;buildUniforms(Lnet/irisshaders/iris/pipeline/programs/SodiumPrograms$Pass;ILnet/irisshaders/iris/uniforms/custom/CustomUniforms;)Lnet/irisshaders/iris/gl/program/ProgramUniforms;"),require=1,allow=1)
 private ProgramUniforms constructor(SodiumShader shader,SodiumPrograms.Pass pass,int program,CustomUniforms custom,
   Operation<ProgramUniforms> original,IrisRenderingPipeline pipeline,SodiumPrograms.Pass constructorPass,
   ShaderBindingContext binding,int constructorProgram,BlendModeOverride blend,List<BufferBlendOverride> overrides,
   CustomUniforms constructorCustom,Supplier<ImmutableSet<Integer>> flips,float alpha,boolean tessellation) {
  shaderlod$bslPipeline=pipeline;
  return original.call(shader,pass,program,custom);
 }
 @WrapOperation(method="buildUniforms",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/uniforms/CommonUniforms;addDynamicUniforms(Lnet/irisshaders/iris/gl/uniform/DynamicUniformHolder;Lnet/irisshaders/iris/gl/state/FogMode;)V"),require=1,allow=1)
 private void register(DynamicUniformHolder holder,FogMode fog,Operation<Void> original,
   SodiumPrograms.Pass pass,int program,CustomUniforms custom) {
  original.call(holder,fog);
  if(pass==SodiumPrograms.Pass.TERRAIN||pass==SodiumPrograms.Pass.TERRAIN_CUTOUT)
   BslShadowFieldBridge.registerSodium(holder,shaderlod$bslPipeline,program);
 }

 @Inject(method="setupState()V",at=@At("HEAD"),require=1)
 private void begin(CallbackInfo ci){BslShadowFieldBridge.sodiumSetupBegin();}
 @Inject(method="setupState()V",at=@At("RETURN"),require=1)
 private void ready(CallbackInfo ci){BslShadowFieldBridge.sodiumSetupReturned(this);}
 @Inject(method="resetState()V",at=@At("HEAD"),require=1)
 private void reset(CallbackInfo ci){BslShadowFieldBridge.sodiumReset(this);}
}
