package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.optimization.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The diagnostic process cannot activate a parked optimization, including terrain caps. */
@Mixin(value=OptimizationConfig.class,remap=false)
public abstract class CostMapNativeMixin {
    @Inject(method="requested",at=@At("HEAD"),cancellable=true,remap=false)
    private static void shaderlod$native(OptimizationId id,CallbackInfoReturnable<Boolean> ci){ci.setReturnValue(false);}
}
