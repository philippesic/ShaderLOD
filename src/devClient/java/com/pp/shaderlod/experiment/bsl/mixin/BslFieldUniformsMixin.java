package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CommonUniforms.class,remap=false)
public abstract class BslFieldUniformsMixin {
 @Inject(method="generalCommonUniforms",at=@At("TAIL"),require=1)
 private static void register(UniformHolder holder,FrameUpdateNotifier notifier,PackDirectives directives,CallbackInfo ci) {
  BslShadowFieldBridge.register(holder,notifier);
 }
}
