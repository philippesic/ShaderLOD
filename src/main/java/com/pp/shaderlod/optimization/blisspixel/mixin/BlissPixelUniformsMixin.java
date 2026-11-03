package com.pp.shaderlod.optimization.blisspixel.mixin;
import com.pp.shaderlod.optimization.blisspixel.BlissPixelRuntime;import net.irisshaders.iris.gl.uniform.*;import net.irisshaders.iris.uniforms.FrameUpdateNotifier;import net.irisshaders.iris.shaderpack.properties.PackDirectives;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.uniforms.CommonUniforms",remap=false)
public abstract class BlissPixelUniformsMixin {
 @Inject(method="generalCommonUniforms(Lnet/irisshaders/iris/gl/uniform/UniformHolder;Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)V",at=@At("TAIL"),require=1)
 private static void shaderlod$register(UniformHolder u,FrameUpdateNotifier n,PackDirectives d,CallbackInfo ci){for(String name:java.util.List.of("QualificationVersion","Ready","ProfileQualified","SourceQualified","LayoutQualified","HistoryReady","NearQualified","ClearWeather","CurrentEpoch","ProofEpoch","HistoryReset","ClampReady"))u.uniform1i(UniformUpdateFrequency.PER_FRAME,"shaderlodBlissPixel"+name,()->BlissPixelRuntime.uniform(name));u.uniform1f(UniformUpdateFrequency.PER_FRAME,"shaderlodBlissPixelCurrentRain",(java.util.function.DoubleSupplier)BlissPixelRuntime::rain);}
}
