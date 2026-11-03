package com.pp.shaderlod.experiment.hierarchy.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class DepthHierarchyLifetimeMixin {
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)
 private void destroy(CallbackInfo callback){com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.destroy(this);}
}
