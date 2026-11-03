package com.pp.shaderlod.optimization.sildurearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;import net.irisshaders.iris.uniforms.custom.CustomUniforms;import net.irisshaders.iris.gl.uniform.*;
/** Register only Sildur private defaults in the actual native dynamic-holder consumer. */
@Mixin(value=ShaderCreator.class,remap=false)
public abstract class SildurAlphaUniformMixin {
 @Inject(method="lambda$create$2(Lnet/irisshaders/iris/uniforms/custom/CustomUniforms;Lnet/irisshaders/iris/gl/uniform/DynamicLocationalUniformHolder;)V",at=@At("HEAD"),require=1,allow=1)
 private static void shaderlod$sildurDefaults(CustomUniforms custom,DynamicLocationalUniformHolder holder,CallbackInfo ci){holder.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodSildurEarlyAlphaPermission",()->0);holder.uniform1f(UniformUpdateFrequency.PER_FRAME,"shaderlodSildurEarlyAlphaReference",()->0f);}
}
