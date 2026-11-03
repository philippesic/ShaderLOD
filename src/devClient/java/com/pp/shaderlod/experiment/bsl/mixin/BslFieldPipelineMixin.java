package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class BslFieldPipelineMixin {
 @Inject(method="beginLevelRendering()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/uniforms/FrameUpdateNotifier;onNewFrame()V",shift=At.Shift.AFTER),require=1)
 private void frame(CallbackInfo ci){BslShadowFieldBridge.begin((WorldRenderingPipeline)(Object)this);}
 @Inject(method="renderShadows",at=@At("HEAD"),require=1)
 private void shadowBegin(CallbackInfo ci){BslShadowFieldBridge.shadowBegin((WorldRenderingPipeline)(Object)this);}
 @Inject(method="renderShadows",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/shadows/ShadowRenderer;renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/client/Camera;)V",shift=At.Shift.AFTER),require=1)
 private void shadowReturn(CallbackInfo ci){BslShadowFieldBridge.shadowReturned((WorldRenderingPipeline)(Object)this);}
 @Inject(method="finalizeLevelRendering()V",at=@At("RETURN"),require=1)
 private void finish(CallbackInfo ci){BslShadowFieldBridge.finish((WorldRenderingPipeline)(Object)this);}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)
 private void dispose(CallbackInfo ci){BslShadowFieldBridge.dispose((WorldRenderingPipeline)(Object)this);}
}
