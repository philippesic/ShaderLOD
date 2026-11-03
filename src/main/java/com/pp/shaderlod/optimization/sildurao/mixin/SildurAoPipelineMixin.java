package com.pp.shaderlod.optimization.sildurao.mixin;
import com.pp.shaderlod.optimization.sildurao.SildurAoRuntime;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class SildurAoPipelineMixin {
 @Shadow @Final private RenderTargets renderTargets;@Shadow @Final private CompositeRenderer deferredRenderer;
 @Inject(method="beginLevelRendering()V",at=@At("RETURN"),require=1)private void shaderlod$frame(CallbackInfo ci){com.pp.shaderlod.optimization.sildurreceiver.SildurReceiverRuntime.frame();SildurAoRuntime.begin(this,renderTargets,deferredRenderer);com.pp.shaderlod.optimization.sildurspatial.SildurSpatialRuntime.begin(this,renderTargets,deferredRenderer);com.pp.shaderlod.optimization.sildurworld.SildurWorldRuntime.begin(this,renderTargets,deferredRenderer);}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void shaderlod$destroy(CallbackInfo ci){com.pp.shaderlod.optimization.sildurreceiver.SildurReceiverRuntime.destroy(this);SildurAoRuntime.destroy(this);com.pp.shaderlod.optimization.sildurspatial.SildurSpatialRuntime.invalidate();com.pp.shaderlod.optimization.sildurworld.SildurWorldRuntime.invalidate();}
}
