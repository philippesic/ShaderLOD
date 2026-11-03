package com.pp.shaderlod.optimization.unboundspatial.mixin;
import com.pp.shaderlod.optimization.unboundspatial.UnboundSpatialRuntime;import net.irisshaders.iris.targets.RenderTargets;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)public abstract class UnboundSpatialPipelineMixin {
 @Shadow @Final private RenderTargets renderTargets;
 @Inject(method="beginLevelRendering()V",at=@At("RETURN"),require=1)private void frame(CallbackInfo c){UnboundSpatialRuntime.beginFrame(this,renderTargets);}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void destroy(CallbackInfo c){UnboundSpatialRuntime.destroyed(this);}
}
