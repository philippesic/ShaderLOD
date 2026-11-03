package com.pp.shaderlod.optimization.astraao.mixin;
import com.pp.shaderlod.optimization.astraao.AstraAoRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class AstraAoReloadMixin {@Inject(method="reload()V",at=@At("HEAD"),require=1)private static void shaderlod$reload(CallbackInfo ci){AstraAoRuntime.reloadHead();}}
