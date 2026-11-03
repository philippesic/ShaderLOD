package com.pp.shaderlod.fabric.mixin;

import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.fabric.FabricLodConfig;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.mixinterface.GpuTextureInterface;
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
    @org.spongepowered.asm.mixin.Unique private static Object shaderlod$depth;
    @org.spongepowered.asm.mixin.Unique private static Object shaderlod$color;
    @org.spongepowered.asm.mixin.Unique private static int shaderlod$depthVersion;
    @org.spongepowered.asm.mixin.Unique private static int shaderlod$colorVersion;
    @org.spongepowered.asm.mixin.Unique private static int shaderlod$depthEpoch(Object texture) {
        if (shaderlod$depth != texture) { shaderlod$depth = texture; shaderlod$depthVersion++; }
        return shaderlod$depthVersion;
    }
    @org.spongepowered.asm.mixin.Unique private static int shaderlod$colorEpoch(Object texture) {
        if (shaderlod$color != texture) { shaderlod$color = texture; shaderlod$colorVersion++; }
        return shaderlod$colorVersion;
    }
    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), remap = false, require = 1)
    private static void shaderlod$register(UniformHolder uniforms, FrameUpdateNotifier notifier,
                                           PackDirectives directives, CallbackInfo ci) {
        com.pp.shaderlod.integration.RendererBackend.requireOpenGl();
        var state = CoreUniformFrame.forNotifier(notifier, notifier::addListener,
                () -> FabricLodConfig.snapshot().core(), () -> Minecraft.getInstance().level,
                SystemTimeUniforms.COUNTER, () -> {
                    var client = Minecraft.getInstance();
                    var target = client.getMainRenderTarget();
                    if (target == null || target.getColorTexture() == null || target.getDepthTexture() == null) {
                        return new CoreUniformFrame.RenderContext(client.getCameraEntity(), client.options.getCameraType(),
                                target, 0, 0, 0, 0, 0, 0);
                    }
                    return new CoreUniformFrame.RenderContext(client.getCameraEntity(), client.options.getCameraType(),
                            target, target.width, target.height,
                            ((GpuTextureInterface) target.getColorTexture()).iris$getGlId(),
                            ((GpuTextureInterface) target.getDepthTexture()).iris$getGlId(),
                            shaderlod$depthEpoch(target.getDepthTexture()), shaderlod$colorEpoch(target.getColorTexture()));
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
