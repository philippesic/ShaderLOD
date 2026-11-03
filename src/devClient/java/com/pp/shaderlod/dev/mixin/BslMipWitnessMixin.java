package com.pp.shaderlod.dev.mixin;

import com.google.common.collect.ImmutableSet;
import com.pp.shaderlod.optimization.mips.BslMipWitness;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Both pinned renderers share createProgram/setupMipmapping and the same quad invocation.
 * Method arrays select the one native render entry present in each target; no redirects or cancellation.
 * Every disabled callback does only a primitive flag check, without allocating an operation/lambda.
 */
@Mixin(value = {CompositeRenderer.class, FinalPassRenderer.class}, remap = false)
public abstract class BslMipWitnessMixin {
    // The neutral dev mod can be loaded without the production mod (clientLodOff).
    @Unique private static final boolean shaderlod$bslMipPresent = shaderlod$bslMipAvailable();
    @Unique private static boolean shaderlod$bslMipAvailable() {
        try { Class.forName("com.pp.shaderlod.optimization.mips.BslMipWitness", false,
                CompositeRenderer.class.getClassLoader()); return true; }
        catch (ClassNotFoundException | LinkageError absent) { return false; }
    }
    @Inject(method = "createProgram", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$bslMipCompiled(ProgramSource source, ImmutableSet<Integer> flipped,
            ImmutableSet<Integer> once, Supplier<ShadowRenderTargets> shadows, CallbackInfoReturnable<Program> ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.compiled(this, source, ci.getReturnValue());
    }
    @Inject(method = {"renderAll()V", "renderFinalPass()V"}, at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$bslMipEnter(CallbackInfo ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.enter(this);
    }
    @Inject(method = {"renderAll()V", "renderFinalPass()V"}, at = @At(value = "INVOKE", target =
        "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"), require = 1, remap = false)
    private void shaderlod$bslMipBeforeQuad(CallbackInfo ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.beforeQuad(this);
    }
    @Inject(method = {"renderAll()V", "renderFinalPass()V"}, at = @At(value = "INVOKE", target =
        "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V", shift = At.Shift.AFTER), require = 1, remap = false)
    private void shaderlod$bslMipAfterQuad(CallbackInfo ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.afterQuad(this);
    }
    @Inject(method = "setupMipmapping", at = @At(value = "INVOKE", target =
        "Lnet/irisshaders/iris/gl/IrisRenderSystem;generateMipmaps(II)V", shift = At.Shift.AFTER), require = 1, remap = false)
    private static void shaderlod$bslMipGenerated(RenderTarget target, boolean readAlt, CallbackInfo ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.mipReturned(target, readAlt);
    }
    // Only FinalPassRenderer has the swap-copy invocation. Absence in the composite target is intentional.
    @Inject(method = {"renderAll()V", "renderFinalPass()V"}, at = @At(value = "INVOKE", target =
        "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"), require = 0, remap = false)
    private void shaderlod$bslMipBeforeCopy(CallbackInfo ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.beforeSwapCopy(this);
    }
    @ModifyArg(method = {"renderAll()V", "renderFinalPass()V"}, at = @At(value = "INVOKE", target =
        "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"), index = 6, require = 0, remap = false)
    private int shaderlod$bslMipCopyWidth(int value) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.swapWidth(this, value);
        return value;
    }
    @ModifyArg(method = {"renderAll()V", "renderFinalPass()V"}, at = @At(value = "INVOKE", target =
        "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"), index = 7, require = 0, remap = false)
    private int shaderlod$bslMipCopyHeight(int value) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.swapHeight(this, value);
        return value;
    }
    @Inject(method = {"renderAll()V", "renderFinalPass()V"}, at = @At(value = "INVOKE", target =
        "Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V", shift = At.Shift.AFTER), require = 0, remap = false)
    private void shaderlod$bslMipAfterCopy(CallbackInfo ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.afterSwapCopy(this);
    }
    @Inject(method = {"renderAll()V", "renderFinalPass()V"}, at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$bslMipLeave(CallbackInfo ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.leave(this);
    }
    @Inject(method = {"recalculateSizes()V", "recalculateSwapPassSize()V", "destroy()V"},
        at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$bslMipLifecycle(CallbackInfo ci) {
        if (shaderlod$bslMipPresent && BslMipWitness.observing()) BslMipWitness.lifecycle(this);
    }
}
