package com.pp.shaderlod.optimization.reimaginedao.mixin;
import com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoRuntime;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class ReimaginedAoPipelineMixin {
 @Shadow @Final private RenderTargets renderTargets;@Shadow @Final private CompositeRenderer deferredRenderer;
 @Inject(method="beginLevelRendering()V",at=@At("RETURN"),require=1)private void shaderlod$frame(CallbackInfo ci){ReimaginedAoRuntime.begin(this,renderTargets,deferredRenderer);}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void shaderlod$destroy(CallbackInfo ci){ReimaginedAoRuntime.destroy(this);}
}
