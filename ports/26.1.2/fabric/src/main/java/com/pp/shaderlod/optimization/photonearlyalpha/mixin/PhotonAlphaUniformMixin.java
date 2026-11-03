package com.pp.shaderlod.optimization.photonearlyalpha.mixin;
import net.irisshaders.iris.pipeline.programs.*;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;
/** Exact native builder registration. The native build call remains owned by Iris/other hooks. */
@Mixin(value=SodiumShader.class,remap=false)
public abstract class PhotonAlphaUniformMixin {
 @Inject(method="buildUniforms(Lnet/irisshaders/iris/pipeline/programs/SodiumPrograms$Pass;ILnet/irisshaders/iris/uniforms/custom/CustomUniforms;)Lnet/irisshaders/iris/gl/program/ProgramUniforms;",
  at=@At(value="INVOKE",target="Lnet/irisshaders/iris/uniforms/builtin/BuiltinReplacementUniforms;addBuiltinReplacementUniforms(Lnet/irisshaders/iris/gl/uniform/UniformHolder;)V"),require=1,allow=1)
 private void defaults(SodiumPrograms.Pass pass,int id,CustomUniforms custom,CallbackInfoReturnable<ProgramUniforms> ci,
  @Local(index=4) ProgramUniforms.Builder b) {
  if(pass==SodiumPrograms.Pass.TERRAIN_CUTOUT) {
   b.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodPhotonEarlyAlphaPermission",()->0);
  }
 }
}
