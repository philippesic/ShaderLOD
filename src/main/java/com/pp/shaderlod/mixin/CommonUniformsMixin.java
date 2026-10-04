package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.ShaderLod;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Deliberately pinned internal Iris seam; it does not modify shader source. */
@Mixin(value = CommonUniforms.class, remap = false)
public abstract class CommonUniformsMixin {
    @Unique private static boolean shaderlod$logged;
    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), remap = false)
    private static void shaderlod$register(UniformHolder uniforms, FrameUpdateNotifier notifier,
                                           PackDirectives directives, CallbackInfo ci) {
        var frequency = UniformUpdateFrequency.PER_FRAME;
        uniforms.uniform1i(frequency, "shaderlodBridgeVersion", () -> 2);
        uniforms.uniform1i(frequency, "shaderlodPreset", () -> ShaderLodConfig.PRESET.get().ordinal());
        uniforms.uniform1i(frequency, "shaderlodEnabled", () -> ShaderLodConfig.snapshot().enabled() ? 1 : 0);
        uniforms.uniform1f(frequency, "shaderlodNear", (java.util.function.DoubleSupplier) () -> ShaderLodConfig.snapshot().near());
        uniforms.uniform1f(frequency, "shaderlodFar", (java.util.function.DoubleSupplier) () -> ShaderLodConfig.snapshot().far());
        uniforms.uniform1f(frequency, "shaderlodTransition", (java.util.function.DoubleSupplier) () -> ShaderLodConfig.snapshot().transition());
        uniforms.uniform1i(frequency, "shaderlodDebug", () -> ShaderLodConfig.snapshot().debug() ? 1 : 0);
        if (!shaderlod$logged) {
            ShaderLod.LOGGER.info("ShaderLOD Iris uniform bridge registered (protocol 2)");
            shaderlod$logged = true;
        }
    }
}
