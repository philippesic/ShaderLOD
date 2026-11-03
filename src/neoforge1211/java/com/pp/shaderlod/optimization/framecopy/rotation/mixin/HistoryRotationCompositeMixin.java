package com.pp.shaderlod.optimization.framecopy.rotation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationRuntime;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPreparation;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.sugar.Local;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.pipeline.CompositeRenderer", remap = false)
public abstract class HistoryRotationCompositeMixin {
    @ModifyArgs(method = "createProgram", at = @At(value = "INVOKE", target =
        "Lnet/irisshaders/iris/gl/program/ProgramBuilder;begin(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/common/collect/ImmutableSet;)Lnet/irisshaders/iris/gl/program/ProgramBuilder;"), require = 1, allow = 1, order = 2000)
    private void rotation$compileInput(Args args, @Local(argsOnly = true) ProgramSource source) {
        HistoryRotationPreparation.compileInput(this, source, args.get(0), args.get(1), args.get(2), args.get(3));
    }
    @Inject(method = "createProgram", at = @At("RETURN"), require = 1, allow = 1, order = 2000)
    private void rotation$linked(CallbackInfoReturnable<Program> ci, @Local(argsOnly = true) ProgramSource source) {
        HistoryRotationPreparation.programReturned(this, source, ci.getReturnValue());
    }
    @Inject(method = "destroy()V", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$destroy(CallbackInfo ci) { HistoryRotationPreparation.destroyed(this); }
    @Inject(method = "recalculateSizes", at = @At("HEAD"), require = 1, allow = 1)
    private void rotation$recalculate(CallbackInfo ci) { HistoryRotationPreparation.beforeRecalculate(this); }
    @ModifyExpressionValue(method = "renderAll()V", at = @At(value = "FIELD", target =
        "Lnet/irisshaders/iris/pipeline/CompositeRenderer$Pass;framebuffer:Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;"), require = 1, allow = 1)
    private GlFramebuffer rotation$fbo(GlFramebuffer original) {
        return HistoryRotationRuntime.selectFramebuffer(this, original);
    }
    @WrapOperation(method = "renderAll()V", at = @At(value = "INVOKE", target =
        "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"), require = 1, allow = 1)
    private void rotation$quad(FullScreenQuadRenderer quad, Operation<Void> original) {
        HistoryRotationPreparation.observeQuad(this);
        HistoryRotationRuntime.beforeQuad(this);
        try { original.call(quad); HistoryRotationRuntime.quadReturned(this); }
        catch (RuntimeException | Error e) { HistoryRotationRuntime.quadFailed(this, e); throw e; }
        finally { HistoryRotationPreparation.quadScopeReturned(); }
    }
}
