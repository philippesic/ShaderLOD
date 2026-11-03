package com.pp.shaderlod.optimization.sildurao.mixin;
import com.pp.shaderlod.optimization.sildurao.SildurAoRuntime;import net.irisshaders.iris.targets.RenderTargets;import com.google.common.collect.ImmutableList;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class SildurAoCompositeMixin {
 @Shadow @Final private net.irisshaders.iris.pipeline.WorldRenderingPipeline pipeline;
 @Inject(method="createProgram",at=@At("RETURN"),require=1)
 private void shaderlod$receiverBuilt(net.irisshaders.iris.shaderpack.programs.ProgramSource source,com.google.common.collect.ImmutableSet<Integer> reads,com.google.common.collect.ImmutableSet<Integer> flipped,java.util.function.Supplier<net.irisshaders.iris.shadows.ShadowRenderTargets> shadows,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.irisshaders.iris.gl.program.Program> ci){com.pp.shaderlod.optimization.sildurreceiver.SildurReceiverRuntime.built(pipeline,renderTargets,source,ci.getReturnValue(),reads);}
 @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/Program;use()V"),require=1)
 private void shaderlod$receiverUse(net.irisshaders.iris.gl.program.Program program,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){com.pp.shaderlod.optimization.sildurreceiver.SildurReceiverRuntime.mark(pipeline,renderTargets,program);original.call(program);}
 @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="renderAll()V")
 private void shaderlod$receiverScope(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){try{original.call();}finally{com.pp.shaderlod.optimization.sildurreceiver.SildurReceiverRuntime.leave();SildurAoRuntime.endComposite();com.pp.shaderlod.optimization.sildurspatial.SildurSpatialRuntime.endComposite();}}

 @Shadow @Final private RenderTargets renderTargets;@Shadow @Final private ImmutableList<?> passes;
 @Inject(method="renderAll()V",at=@At("HEAD"),require=1)private void shaderlod$start(CallbackInfo ci){SildurAoRuntime.beginComposite(this,renderTargets,passes);com.pp.shaderlod.optimization.sildurspatial.SildurSpatialRuntime.beginComposite(this,renderTargets,passes);com.pp.shaderlod.optimization.sildurworld.SildurWorldRuntime.beginComposite(this,renderTargets,passes);}
}
