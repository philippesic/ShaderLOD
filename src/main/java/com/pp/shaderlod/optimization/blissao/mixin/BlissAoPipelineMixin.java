package com.pp.shaderlod.optimization.blissao.mixin;
import com.pp.shaderlod.optimization.blissao.BlissAoRuntime;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.pipeline.CompositeRenderer;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class BlissAoPipelineMixin {
 @Shadow @Final private RenderTargets renderTargets;@Shadow @Final private CompositeRenderer deferredRenderer;@Shadow @Final private CompositeRenderer compositeRenderer;
 @Inject(method="beginTranslucents()V",at=@At("HEAD"),require=1)private void shaderlod$frame(CallbackInfo ci){BlissAoRuntime.begin(this,renderTargets,deferredRenderer,compositeRenderer);}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void shaderlod$destroy(CallbackInfo ci){BlissAoRuntime.invalidate();}
}
