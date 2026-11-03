package com.pp.shaderlod.optimization.unboundearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;import net.irisshaders.iris.uniforms.custom.CustomUniforms;import net.irisshaders.iris.gl.uniform.*;
/** Register only Unbound private defaults in the actual native dynamic-holder consumer. */
@Mixin(value=ShaderCreator.class,remap=false)
public abstract class UnboundAlphaUniformMixin {
 @Inject(method="lambda$create$2(Lnet/irisshaders/iris/uniforms/custom/CustomUniforms;Lnet/irisshaders/iris/gl/uniform/DynamicLocationalUniformHolder;)V",at=@At("HEAD"),require=1,allow=1)
 private static void shaderlod$unboundDefaults(CustomUniforms custom,DynamicLocationalUniformHolder holder,CallbackInfo ci){holder.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodUnboundEarlyAlphaPermission",()->0);holder.uniform1f(UniformUpdateFrequency.PER_FRAME,"shaderlodUnboundEarlyAlphaReference",()->0f);}
}
