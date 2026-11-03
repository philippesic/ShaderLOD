package com.pp.shaderlod.fabric.mixin;

import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.fabric.FabricLodConfig;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Required, release-pinned Iris seam; metadata rejects other Iris internals. */
@Mixin(value = CommonUniforms.class, remap = false)
public abstract class FabricCoreUniformsMixin {
    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), remap = false, require = 1)
    private static void shaderlod$register(UniformHolder uniforms, FrameUpdateNotifier notifier,
                                           PackDirectives directives, CallbackInfo ci) {
        var state = CoreUniformFrame.forNotifier(notifier, notifier::addListener,
                () -> FabricLodConfig.snapshot().core(), () -> Minecraft.getInstance().level,
                SystemTimeUniforms.COUNTER, () -> {
                    var client = Minecraft.getInstance();
                    var target = client.getMainRenderTarget();
                    return new CoreUniformFrame.RenderContext(client.getCameraEntity(), client.options.getCameraType(),
                            target, target.width, target.height, target.getColorTextureId(), target.getDepthTextureId(),
                            ((Blaze3dRenderTargetExt) target).iris$getDepthBufferVersion(),
                            ((Blaze3dRenderTargetExt) target).iris$getColorBufferVersion());
                });
        var frequency = UniformUpdateFrequency.PER_FRAME;
        uniforms.uniform1i(frequency, "shaderlodOptimizationFlagsVersion", () -> com.pp.shaderlod.optimization.OptimizationConfig.PROTOCOL_VERSION);
        for (var id : com.pp.shaderlod.optimization.OptimizationId.values()) {
            uniforms.uniform1i(frequency, id.uniform(), () -> state.current().core().optimizations().uniform(id));
        }
        for(var alias : com.pp.shaderlod.optimization.OptimizationId.uniformAliases().entrySet()) {
            uniforms.uniform1i(frequency, alias.getKey(), () -> state.current().core().optimizations().uniform(alias.getValue()));
        }
        uniforms.uniform1i(frequency, "shaderlodBridgeVersion", () -> CoreUniformFrame.BRIDGE_VERSION);
        uniforms.uniform1i(frequency, "shaderlodPreset", () -> state.current().core().preset().ordinal());
        uniforms.uniform1i(frequency, "shaderlodEnabled", () -> state.current().core().settings().enabled() ? 1 : 0);
        uniforms.uniform1f(frequency, "shaderlodNear", (DoubleSupplier) () -> state.current().core().settings().near());
        uniforms.uniform1f(frequency, "shaderlodFar", (DoubleSupplier) () -> state.current().core().settings().far());
        uniforms.uniform1f(frequency, "shaderlodTransition", (DoubleSupplier) () -> state.current().core().settings().transition());
        uniforms.uniform1i(frequency, "shaderlodDebug", () -> state.current().core().settings().debug() ? 1 : 0);
        uniforms.uniform1i(frequency, "shaderlodTemporalVersion", () -> state.current().temporalVersion());
        uniforms.uniform1i(frequency, "shaderlodHistoryReset", () -> state.current().historyReset() ? 1 : 0);
        uniforms.uniform1i(frequency, "shaderlodScaleHistoryVersion", () -> state.current().scaleHistoryVersion());
        uniforms.uniform1i(frequency, "shaderlodPreviousEnabled", () -> state.current().previousEnabled());
        uniforms.uniform1i(frequency, "shaderlodScaleTransition", () -> state.current().scaleTransition() ? 1 : 0);
    }
}
