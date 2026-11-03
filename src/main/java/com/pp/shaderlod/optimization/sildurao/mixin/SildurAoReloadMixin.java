package com.pp.shaderlod.optimization.sildurao.mixin;
import com.pp.shaderlod.optimization.sildurao.SildurAoRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class SildurAoReloadMixin {@Inject(method="reload()V",at=@At("HEAD"),require=1)private static void shaderlod$reload(CallbackInfo ci){com.pp.shaderlod.optimization.sildurreceiver.SildurReceiverRuntime.invalidate();SildurAoRuntime.reloadHead();com.pp.shaderlod.optimization.sildurspatial.SildurSpatialRuntime.invalidate();}}
