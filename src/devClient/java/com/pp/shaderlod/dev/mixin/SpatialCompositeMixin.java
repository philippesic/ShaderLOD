package com.pp.shaderlod.dev.mixin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.pp.shaderlod.dev.timing.SpatialLayoutProbe;
import com.pp.shaderlod.dev.PixelHistorySession;
import com.pp.shaderlod.dev.SettledConsumerUniforms;
import com.pp.shaderlod.dev.timing.PixelCoverageProbe;
import java.util.Map;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompositeRenderer.class, remap = false)
public abstract class SpatialCompositeMixin {
    @Shadow @Final private WorldRenderingPipeline pipeline;
    @Shadow @Final private CompositePass compositePass;
    @Shadow @Final private ImmutableList<?> passes;
    @Shadow @Final private RenderTargets renderTargets;
    @Unique private Map<PatchShaderType, String> shaderlod$transformed = Map.of();

    @ModifyExpressionValue(method = "createProgram", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchComposite(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"), require = 1, remap = false)
    private Map<PatchShaderType, String> shaderlod$actualTransformedStages(Map<PatchShaderType, String> original) {
        com.pp.shaderlod.dev.MakeupMrtProbe.transformed(original);
        shaderlod$transformed = original;
        return original;
    }

    @Inject(method = "createProgram", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$compiledSource(ProgramSource source, ImmutableSet<Integer> flipped,
                                          ImmutableSet<Integer> flippedOnce, Supplier<ShadowRenderTargets> shadows,
                                          CallbackInfoReturnable<Program> callback) {
        SpatialLayoutProbe.compiledProgram(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        PixelCoverageProbe.compiledProgram(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed, renderTargets);
        PixelHistorySession.compiledProgram(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        SettledConsumerUniforms.compiledProgram(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.BlissChainWitness.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.SildurReceiverWitness.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.MakeupCloudWitness.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.BslMipWitness.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.BslMipParity.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.CompositeCopyWitness.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.ComplementaryCopyGraph.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.ComplementaryRasterActivationProbe.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        com.pp.shaderlod.dev.MakeupMrtProbe.compiled(this, pipeline, compositePass, source, callback.getReturnValue(), shaderlod$transformed);
        shaderlod$transformed = Map.of();
    }

    @Inject(method = "renderAll()V", at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$enter(CallbackInfo callback) {
        com.pp.shaderlod.dev.MakeupMrtProbe.enter(this, pipeline, compositePass, passes, renderTargets);
        SpatialLayoutProbe.enterRenderer(this, pipeline, compositePass, passes);
        PixelCoverageProbe.enterRenderer(this, pipeline, compositePass, passes);
        PixelHistorySession.enterRenderer(this, pipeline, compositePass, passes);
        SettledConsumerUniforms.enterRenderer(this, pipeline, compositePass, passes);
        com.pp.shaderlod.dev.BlissChainWitness.enter(this, pipeline, compositePass, passes);
        com.pp.shaderlod.dev.SildurReceiverWitness.enter(this, pipeline, compositePass, passes);
        com.pp.shaderlod.dev.MakeupCloudWitness.enter(this, pipeline, compositePass, passes);
        com.pp.shaderlod.dev.BslMipWitness.enter(this, pipeline, compositePass, passes, renderTargets);
        com.pp.shaderlod.dev.BslMipParity.enter(this, pipeline, compositePass, passes, renderTargets);
        com.pp.shaderlod.dev.CompositeCopyWitness.enter(this, pipeline, compositePass, passes, renderTargets);
        com.pp.shaderlod.dev.ComplementaryCopyGraph.enter(this, pipeline, compositePass, passes, renderTargets);
        com.pp.shaderlod.dev.ComplementaryRasterActivationProbe.enter(this, pipeline, compositePass, passes, renderTargets);
    }
    @Inject(method = "renderAll()V", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$leave(CallbackInfo callback) {
        com.pp.shaderlod.dev.MakeupMrtProbe.leave();
        SpatialLayoutProbe.leaveRenderer();
        PixelCoverageProbe.leaveRenderer();
        PixelHistorySession.leaveRenderer();
        SettledConsumerUniforms.leaveRenderer();
        com.pp.shaderlod.dev.BlissChainWitness.leave();
        com.pp.shaderlod.dev.SildurReceiverWitness.leave();
        com.pp.shaderlod.dev.MakeupCloudWitness.leave();
        com.pp.shaderlod.dev.BslMipWitness.leave();
        com.pp.shaderlod.dev.BslMipParity.leave();
        com.pp.shaderlod.dev.CompositeCopyWitness.leave();
        com.pp.shaderlod.dev.ComplementaryCopyGraph.leave();
        com.pp.shaderlod.dev.ComplementaryRasterActivationProbe.leave();
    }
    @Inject(method = "setupMipmapping", at = @At("RETURN"), require = 1, remap = false)
    private static void shaderlod$nativeMipReturned(net.irisshaders.iris.targets.RenderTarget target, boolean readAlt,
                                                   CallbackInfo callback) {
        com.pp.shaderlod.dev.MakeupMrtProbe.generationReturned(target, readAlt);
        com.pp.shaderlod.dev.BslMipWitness.generationReturned(target, readAlt);
        com.pp.shaderlod.dev.ComplementaryCopyGraph.generationReturned("composite", target, readAlt);
    }
}
