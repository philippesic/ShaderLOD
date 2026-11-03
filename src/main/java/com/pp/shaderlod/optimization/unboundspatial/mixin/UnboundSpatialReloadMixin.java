package com.pp.shaderlod.optimization.unboundspatial.mixin;
import com.pp.shaderlod.optimization.unboundspatial.UnboundSpatialRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)public abstract class UnboundSpatialReloadMixin {@Inject(method="reload()V",at=@At("HEAD"),require=1)private static void reset(CallbackInfo c){UnboundSpatialRuntime.reload();}}

