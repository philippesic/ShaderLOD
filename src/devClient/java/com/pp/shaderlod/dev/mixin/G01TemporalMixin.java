package com.pp.shaderlod.dev.mixin;

import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.dev.G01TemporalProbe;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Map;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Loaded only by the explicit untimed G01 exposure launch flag. */
@Mixin(value = {CompositeRenderer.class, FinalPassRenderer.class}, remap = false)
public abstract class G01TemporalMixin {
    @Unique private Map<?,String> shaderlod$g01Stages = Map.of();
    @ModifyExpressionValue(method = "createProgram", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchComposite(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"), require = 1)
    private Map<?,String> transformed(Map<?,String> stages) { shaderlod$g01Stages = new java.util.HashMap<>(stages); return stages; }
    @Inject(method = "createProgram", at = @At("RETURN"), require = 1)
    private void compiled(ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> once,
            Supplier<ShadowRenderTargets> shadows, CallbackInfoReturnable<Program> ci) { G01TemporalProbe.compiled(source, ci.getReturnValue(), shaderlod$g01Stages); com.pp.shaderlod.dev.G01GraphProbe.compiled(source, ci.getReturnValue(), shaderlod$g01Stages, flipped, once); shaderlod$g01Stages = Map.of(); }
    @Inject(method = {"renderAll()V", "renderFinalPass()V"}, at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"), require = 1)
    private void before(CallbackInfo ci) { G01TemporalProbe.quad("before"); }
    @Inject(method = {"renderAll()V", "renderFinalPass()V"}, at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V", shift = At.Shift.AFTER), require = 1)
    private void after(CallbackInfo ci) { G01TemporalProbe.quad("after"); }
    @Inject(method = "setupMipmapping", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/IrisRenderSystem;generateMipmaps(II)V", shift = At.Shift.AFTER), require = 1)
    private static void mip(RenderTarget target, boolean alt, CallbackInfo ci) { G01TemporalProbe.mip(target, alt); }
    @Inject(method = "renderFinalPass", at = @At("RETURN"), require = 0)
    private void finalReturned(CallbackInfo ci) { G01TemporalProbe.finalReturned(); }
}
