package com.pp.shaderlod.optimization.blissspatial.mixin;
import com.pp.shaderlod.optimization.blissspatial.BlissSpatialRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class BlissSpatialReloadMixin {@Inject(method="reload()V",at=@At("HEAD"),require=1)private static void shaderlod$reload(CallbackInfo ci){BlissSpatialRuntime.invalidate();}}
