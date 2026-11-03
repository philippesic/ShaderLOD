package com.pp.shaderlod.optimization.sourcecache.mixin;
import com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class SourceDestroyMixin {
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)
 private void revoke(CallbackInfo ci){IrisSourceLifecycle.destroy(this);}
}
