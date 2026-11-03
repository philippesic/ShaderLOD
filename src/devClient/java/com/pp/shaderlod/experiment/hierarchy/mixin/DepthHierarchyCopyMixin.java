package com.pp.shaderlod.experiment.hierarchy.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.targets.RenderTargets",remap=false)
public abstract class DepthHierarchyCopyMixin {
 @Inject(method="copyPreTranslucentDepth()V",at=@At("RETURN"),require=1)
 private void copied(CallbackInfo callback){com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.copied((net.irisshaders.iris.targets.RenderTargets)(Object)this);}
}
