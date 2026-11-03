package com.pp.shaderlod.sildurprefix.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.sildurprefix.SildurPrefixRuntime;
import java.util.function.Supplier;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class SildurPrefixBuildR8Mixin {
 @Inject(method="buildProgram",at=@At("RETURN"),require=1)
 private void built(GlProgram.Builder builder,IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,
   ProgramSource source,AlphaTest alpha,CustomUniforms uniforms,Supplier<ImmutableSet<Integer>> flips,
   boolean tessellation,CallbackInfoReturnable<GlProgram<?>> ci){
  SildurPrefixRuntime.built(pipeline,pass,source,ci.getReturnValue());
 }
}
