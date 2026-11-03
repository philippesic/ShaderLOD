package com.pp.shaderlod.experiment.mixin;
import com.pp.shaderlod.experiment.ComplementaryRasterR1;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class, remap=false)
public abstract class RasterPipelineMixin {
 @Shadow @Final private RenderTargets renderTargets;
 @Shadow @Final private net.irisshaders.iris.uniforms.FrameUpdateNotifier updateNotifier;
 @Inject(method="beginLevelRendering()V", at=@At("HEAD"), require=1)
 private void begin(CallbackInfo ci) { ComplementaryRasterR1.begin(this, renderTargets); }
 @Inject(method="beginLevelRendering()V", at=@At(value="INVOKE", target="Lnet/irisshaders/iris/targets/RenderTargets;resizeIfNeeded(IIIILnet/irisshaders/iris/gl/texture/DepthBufferFormat;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)Z", shift=At.Shift.AFTER), require=1)
 private void resized(CallbackInfo ci) { ComplementaryRasterR1.resized(); }
 @Inject(method="beginLevelRendering()V", at=@At(value="INVOKE", target="Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;onNewFrame()V", shift=At.Shift.AFTER), require=1)
 private void notified(CallbackInfo ci) { ComplementaryRasterR1.notified(updateNotifier); }
 @Inject(method="beginHand()V", at=@At("HEAD"), require=1)
 private void handAuxiliaryBegin(CallbackInfo ci) { ComplementaryRasterR1.beginHandAuxiliary(); }
 @Inject(method="beginHand()V", at=@At("RETURN"), require=1)
 private void handAuxiliaryEnd(CallbackInfo ci) { ComplementaryRasterR1.endHandAuxiliary(); }
 @Inject(method="setPhase", at=@At("TAIL"), require=1)
 private void phase(WorldRenderingPhase phase, CallbackInfo ci) { if (phase != WorldRenderingPhase.NONE) ComplementaryRasterR1.geometry(); }
 @Inject(method="renderShadows", at=@At("HEAD"), require=1)
 private void shadowBegin(CallbackInfo ci) { ComplementaryRasterR1.beginShadow(); }
 @Inject(method="renderShadows", at=@At("TAIL"), require=1)
 private void shadowEnd(CallbackInfo ci) { ComplementaryRasterR1.endShadow(); }
 @Inject(method="beginTranslucents", at=@At("TAIL"), require=1)
 private void resume(CallbackInfo ci) { ComplementaryRasterR1.geometry(); }
 @Inject(method="finalizeLevelRendering()V", at=@At("HEAD"), require=1)
 private void finishBegin(CallbackInfo ci) { ComplementaryRasterR1.leaveGeometry(); }
 @Inject(method="finalizeLevelRendering()V", at=@At("RETURN"), require=1)
 private void finish(CallbackInfo ci) { ComplementaryRasterR1.finish(); }
 @Inject(method="destroy()V", at=@At("HEAD"), require=1)
 private void dispose(CallbackInfo ci) { ComplementaryRasterR1.dispose(this); }
}
