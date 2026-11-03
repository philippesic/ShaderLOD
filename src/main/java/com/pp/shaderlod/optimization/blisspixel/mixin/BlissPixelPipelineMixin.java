package com.pp.shaderlod.optimization.blisspixel.mixin;
import com.pp.shaderlod.optimization.blisspixel.BlissPixelRuntime;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.pipeline.CompositeRenderer;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class BlissPixelPipelineMixin {
 @Shadow @Final private RenderTargets renderTargets;@Shadow @Final private CompositeRenderer compositeRenderer;
 @Inject(method="beginLevelRendering()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/CompositeRenderer;renderAll()V",shift=At.Shift.BEFORE),require=1,allow=1)private void shaderlod$begin(CallbackInfo ci){BlissPixelRuntime.begin(this,renderTargets,compositeRenderer);}
 @Inject(method="finalizeLevelRendering()V",at=@At("RETURN"),require=1)private void shaderlod$end(CallbackInfo ci){BlissPixelRuntime.end(this);}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void shaderlod$destroy(CallbackInfo ci){BlissPixelRuntime.invalidate();}
}
