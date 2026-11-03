package com.pp.shaderlod.experiment.mixin;
import com.pp.shaderlod.experiment.ComplementaryRasterR1;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CommonUniforms.class, remap=false)
public abstract class RasterUniformsMixin {
 @Inject(method="generalCommonUniforms", at=@At("TAIL"), require=1)
 private static void register(UniformHolder uniforms, FrameUpdateNotifier notifier, PackDirectives directives, CallbackInfo ci) {
  uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "shaderlodRasterR1Active", ComplementaryRasterR1::activeUniform);
 }
}
