package com.pp.shaderlod.dev.mixin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.pp.shaderlod.dev.ComplementaryCopyGraph;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import java.util.Map;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.targets.RenderTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FinalPassRenderer.class, remap = false)
public abstract class FinalPassTimingMixin {
    @Shadow @Final private WorldRenderingPipeline pipeline;
    @Shadow @Final private RenderTargets renderTargets;
    @Shadow @Final private ImmutableList<?> swapPasses;
    @Shadow @Final private GlFramebuffer colorHolder;
    @Unique private Map<PatchShaderType,String> shaderlod$graphTransformed = Map.of();

    @ModifyExpressionValue(method = "createProgram", at = @At(value = "INVOKE", target =
        "Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchComposite(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"), require = 1, remap = false)
    private Map<PatchShaderType,String> shaderlod$graphTransform(Map<PatchShaderType,String> original) {
        com.pp.shaderlod.dev.MakeupMrtProbe.transformed(original);
        if (!System.getProperty("shaderlod.dev.complementaryCopyGraph", "").isBlank() || com.pp.shaderlod.dev.MakeupMrtProbe.enabled()) shaderlod$graphTransformed = original;
        return original;
    }
    @Inject(method = "createProgram", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$graphCompiled(ProgramSource source, ImmutableSet<Integer> flipped,
            ImmutableSet<Integer> once, Supplier<ShadowRenderTargets> shadows, CallbackInfoReturnable<Program> callback) {
        com.pp.shaderlod.dev.MakeupMrtProbe.finalCompiled(this, pipeline, source, callback.getReturnValue(), shaderlod$graphTransformed);
        ComplementaryCopyGraph.finalCompiled(this, pipeline, source, callback.getReturnValue(), shaderlod$graphTransformed);
        shaderlod$graphTransformed = Map.of();
    }
    @Inject(method = "renderFinalPass", at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$begin(CallbackInfo ci) {
        com.pp.shaderlod.dev.MakeupMrtProbe.finalEnterReflected(this, pipeline, renderTargets, swapPasses, colorHolder);
        // Private Iris Pass cannot be a typed @Shadow. Read its exact pinned field only when witness is enabled.
        if (!System.getProperty("shaderlod.dev.complementaryCopyGraph", "").isBlank()) {
            ComplementaryCopyGraph.finalEnterReflected(this, pipeline, renderTargets, swapPasses, colorHolder);
        }
        CpuTimers.beginLayer("iris/final"); GpuTimers.beginStandalonePass("final");
    }
    @Inject(method = "renderFinalPass", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$end(CallbackInfo ci) {
        com.pp.shaderlod.dev.MakeupMrtProbe.finalLeave();
        ComplementaryCopyGraph.finalLeave();
        GpuTimers.endStandalonePass(); CpuTimers.endLayer("iris/final");
    }
    @Inject(method = "setupMipmapping", at = @At("RETURN"), require = 1, remap = false)
    private static void shaderlod$graphMipReturned(RenderTarget target, boolean readAlt, CallbackInfo ci) {
        ComplementaryCopyGraph.generationReturned("final", target, readAlt);
    }
    @Inject(method = "renderFinalPass", at = @At(value = "INVOKE", target =
        "Lnet/irisshaders/iris/gl/IrisRenderSystem;copyTexSubImage2D(IIIIIIIII)V"), require = 1, remap = false)
    private void shaderlod$graphFallbackCopy(CallbackInfo ci) { com.pp.shaderlod.dev.MakeupMrtProbe.fallbackCopy(); ComplementaryCopyGraph.fallbackCopy(); }
    @Inject(method = "renderFinalPass", at = @At(value = "INVOKE", target =
        "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"), require = 1, remap = false)
    private void shaderlod$graphSwapCopy(CallbackInfo ci) { com.pp.shaderlod.dev.MakeupMrtProbe.swapCopy(); ComplementaryCopyGraph.swapCopy(); }
}
