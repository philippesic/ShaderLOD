package com.pp.shaderlod.experiment.makeup.mixin;
import com.pp.shaderlod.experiment.makeup.MakeupEarlyAlphaR1;
import net.irisshaders.iris.pipeline.programs.SodiumShader;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Direct actual Sodium consumer registration; native currentAlphaTest remains Iris-owned. */
@Mixin(value=SodiumShader.class,remap=false)
public abstract class MakeupAlphaUniformMixin {
 @Redirect(method="buildUniforms",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/ProgramUniforms$Builder;buildUniforms()Lnet/irisshaders/iris/gl/program/ProgramUniforms;"),require=1,allow=1)
 private ProgramUniforms shaderlod$register(ProgramUniforms.Builder b,
      net.irisshaders.iris.pipeline.programs.SodiumPrograms.Pass pass,int programId,
      net.irisshaders.iris.uniforms.custom.CustomUniforms custom){
  Object pack=pass==net.irisshaders.iris.pipeline.programs.SodiumPrograms.Pass.TERRAIN_CUTOUT?MakeupEarlyAlphaR1.eligibleBuildPack():null;
  b.uniform1b(UniformUpdateFrequency.PER_FRAME,"shaderlodMakeupEarlyAlphaEnabled",() -> MakeupEarlyAlphaR1.enabled(pack));
  return b.buildUniforms();
 }
}
