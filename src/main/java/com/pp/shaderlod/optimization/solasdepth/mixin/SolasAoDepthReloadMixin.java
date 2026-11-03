package com.pp.shaderlod.optimization.solasdepth.mixin;
import com.pp.shaderlod.optimization.solasdepth.SolasAoDepthRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class SolasAoDepthReloadMixin {
 @Inject(method="reload()V",at=@At("HEAD"),require=1)private static void reload(CallbackInfo ci){SolasAoDepthRuntime.invalidate();}
}
