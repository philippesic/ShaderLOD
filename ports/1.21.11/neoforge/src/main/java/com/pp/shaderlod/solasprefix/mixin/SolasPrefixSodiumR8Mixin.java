package com.pp.shaderlod.solasprefix.mixin;
import com.pp.shaderlod.solasprefix.SolasPrefixRuntime;
import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.pipeline.programs.*;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.blending.*;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import java.util.*;
import java.util.function.Supplier;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SodiumShader.class,remap=false)
public abstract class SolasPrefixSodiumR8Mixin {
 @Unique private IrisRenderingPipeline shaderlod$prefixPipeline;
 @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/programs/SodiumShader;buildUniforms(Lnet/irisshaders/iris/pipeline/programs/SodiumPrograms$Pass;ILnet/irisshaders/iris/uniforms/custom/CustomUniforms;)Lnet/irisshaders/iris/gl/program/ProgramUniforms;"),require=1,allow=1)
 private ProgramUniforms constructor(SodiumShader shader,SodiumPrograms.Pass pass,int program,CustomUniforms custom,Operation<ProgramUniforms> original,
  IrisRenderingPipeline pipeline,SodiumPrograms.Pass constructorPass,ShaderBindingContext binding,int constructorProgram,BlendModeOverride blend,List<BufferBlendOverride> overrides,CustomUniforms constructorCustom,Supplier<ImmutableSet<Integer>> flips,float alpha,boolean tessellation){shaderlod$prefixPipeline=pipeline;return original.call(shader,pass,program,custom);}
 @WrapOperation(method="buildUniforms",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/uniforms/CommonUniforms;addDynamicUniforms(Lnet/irisshaders/iris/gl/uniform/DynamicUniformHolder;Lnet/irisshaders/iris/gl/state/FogMode;)V"),require=1,allow=1)
 private void uniforms(DynamicUniformHolder holder,FogMode fog,Operation<Void> original,SodiumPrograms.Pass pass,int program,CustomUniforms custom){original.call(holder,fog);SolasPrefixRuntime.register(holder,this,shaderlod$prefixPipeline,pass,program);}
 @Inject(method="setupState",at=@At("HEAD"),require=1)private void begin(CallbackInfo ci){SolasPrefixRuntime.begin(this);SolasPrefixRuntime.restoreForSetup(this);}
 @Inject(method="setupState",at=@At("RETURN"),require=1)private void ready(CallbackInfo ci){SolasPrefixRuntime.ready(this);}
 @Inject(method="resetState()V",at=@At("HEAD"),require=1)private void reset(CallbackInfo ci){SolasPrefixRuntime.reset(this);}
}
