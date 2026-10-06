package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShaderLodEffectConfig;
import com.pp.shaderlod.EffectUniformFrame;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.IrisAdapterRuntime;
import java.util.function.DoubleSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Additive extension; the existing seven-field core protocol remains version 2. */
@Mixin(value = CommonUniforms.class, remap = false)
public abstract class EffectUniformsMixin {
    @Inject(method = "generalCommonUniforms", at = @At("HEAD"), remap = false)
    private static void shaderlod$sealAdapters(UniformHolder uniforms, FrameUpdateNotifier notifier,
                                               PackDirectives directives, CallbackInfo ci) {
        IrisAdapterRuntime.registerCurrentPack();
    }

    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), remap = false)
    private static void shaderlod$registerCapabilities(UniformHolder uniforms, FrameUpdateNotifier notifier,
                                                       PackDirectives directives, CallbackInfo ci) {
        IrisAdapterRuntime.registerCurrentPack();
        var state = EffectUniformFrame.forNotifier(notifier, notifier::addListener);
        var frequency = UniformUpdateFrequency.PER_FRAME;
        uniforms.uniform1i(frequency, "shaderlodCapabilitiesVersion", () -> AdapterRuntimeBinding.CAPABILITIES_VERSION);
        for (var export : IrisAdapterRuntime.capabilityExports()) {
            uniforms.uniform1i(frequency, export.uniformName(), export.supplier(state));
        }
    }

    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), remap = false)
    private static void shaderlod$registerEffects(UniformHolder uniforms, FrameUpdateNotifier notifier,
                                                 PackDirectives directives, CallbackInfo ci) {
        IrisAdapterRuntime.registerCurrentPack();
        var state = EffectUniformFrame.forNotifier(notifier, notifier::addListener);
        ShaderLodEffectConfig.observeIrisRegistration();
        var frequency = UniformUpdateFrequency.PER_FRAME;
        uniforms.uniform1i(frequency, "shaderlodEffectsVersion", () -> ShaderLodEffectConfig.PROTOCOL_VERSION);
        for (Effect effect : Effect.values()) {
            String prefix = "shaderlod" + effect.uniformKeySuffix();
            uniforms.uniform1i(frequency, prefix + "Mode", () -> state.control(effect).mode().protocolValue());
            uniforms.uniform1f(frequency, prefix + "Near",
                    (DoubleSupplier) () -> state.control(effect).policy().bands().nearBlocks());
            uniforms.uniform1f(frequency, prefix + "Far",
                    (DoubleSupplier) () -> state.control(effect).policy().bands().farBlocks());
            uniforms.uniform1f(frequency, prefix + "Transition",
                    (DoubleSupplier) () -> state.control(effect).policy().bands().transitionBlocks());
            uniforms.uniform1f(frequency, prefix + "MediumWorkRatio",
                    (DoubleSupplier) () -> state.control(effect).policy().mediumWorkRatio());
            uniforms.uniform1f(frequency, prefix + "FarWorkRatio",
                    (DoubleSupplier) () -> state.control(effect).policy().farWorkRatio());
        }
    }

}
