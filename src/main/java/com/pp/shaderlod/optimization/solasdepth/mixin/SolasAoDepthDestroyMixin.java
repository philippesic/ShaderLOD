package com.pp.shaderlod.optimization.solasdepth.mixin;
import com.pp.shaderlod.optimization.solasdepth.SolasAoDepthRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public abstract class SolasAoDepthDestroyMixin {
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void destroy(CallbackInfo ci){SolasAoDepthRuntime.destroy(this);}
}
