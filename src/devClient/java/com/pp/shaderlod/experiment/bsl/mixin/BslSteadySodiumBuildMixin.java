package com.pp.shaderlod.experiment.bsl.mixin;
import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
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
/** Pin actual pass + pipeline + GlProgram + constructed SodiumShader, after
 * linked native creation and before caller's stage deletion. */
@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class BslSteadySodiumBuildMixin {
 @Inject(method="buildProgram",at=@At("RETURN"),require=1)
 private void built(GlProgram.Builder builder,IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,
   ProgramSource source,AlphaTest alpha,CustomUniforms uniforms,Supplier<ImmutableSet<Integer>> flips,
   boolean tessellation,CallbackInfoReturnable<GlProgram<?>> ci) {
  BslShadowFieldBridge.sodiumBuilt(pipeline,pass,ci.getReturnValue());
 }
}
