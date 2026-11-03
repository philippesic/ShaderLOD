package com.pp.shaderlod.optimization.unboundao.mixin;
import com.pp.shaderlod.optimization.unboundao.UnboundAoRuntime;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class UnboundAoPipelineMixin {
 @Shadow @Final private RenderTargets renderTargets;@Shadow @Final private CompositeRenderer deferredRenderer;
 @Inject(method="beginLevelRendering()V",at=@At("RETURN"),require=1)private void shaderlod$frame(CallbackInfo ci){UnboundAoRuntime.begin(this,renderTargets,deferredRenderer);}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void shaderlod$destroy(CallbackInfo ci){UnboundAoRuntime.destroy(this);}
}
