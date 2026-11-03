package com.pp.shaderlod.optimization.solaspixel.mixin;
import com.pp.shaderlod.optimization.solaspixel.SolasPixelRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class SolasPixelReloadMixin {@Inject(method="reload()V",at=@At("HEAD"),require=1)private static void shaderlod$reload(CallbackInfo ci){SolasPixelRuntime.invalidate();}}
