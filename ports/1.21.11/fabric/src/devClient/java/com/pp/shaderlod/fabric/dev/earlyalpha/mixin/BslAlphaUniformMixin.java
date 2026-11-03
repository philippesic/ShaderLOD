package com.pp.shaderlod.fabric.dev.earlyalpha.mixin;
import net.irisshaders.iris.pipeline.programs.SodiumShader;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Safe defaults: exact pre-draw bridge installs qualified values only after native state setup. */
@Mixin(value=SodiumShader.class,remap=false)
public abstract class BslAlphaUniformMixin {
 @Redirect(method="buildUniforms",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/ProgramUniforms$Builder;buildUniforms()Lnet/irisshaders/iris/gl/program/ProgramUniforms;"),require=1,allow=1,remap=false)
 private ProgramUniforms shaderlod$register(ProgramUniforms.Builder b,SodiumPrograms.Pass pass,int programId,net.irisshaders.iris.uniforms.custom.CustomUniforms custom) {
  if(com.pp.shaderlod.fabric.dev.earlyalpha.BslEarlyAlpha.REQUESTED) {
   b.uniform1b(UniformUpdateFrequency.PER_FRAME,"shaderlodBslAlphaEnabled",()->false);
   b.uniform1b(UniformUpdateFrequency.PER_FRAME,"shaderlodBslAlphaQualified",()->false);
   b.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodBslAlphaVersion",()->0);
   b.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodBslAlphaPreset",()->0);
   b.uniform1f(UniformUpdateFrequency.PER_FRAME,"shaderlodBslAlphaReference",()->0.0);
  }
  com.pp.shaderlod.fabric.dev.zeroemission.BslZeroEmission.register(b,pass);
  return b.buildUniforms();
 }
}
