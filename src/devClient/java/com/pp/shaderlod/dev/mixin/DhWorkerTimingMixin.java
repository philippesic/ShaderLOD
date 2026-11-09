package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.DhTaskTimings;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker$TrackedRunnable",remap=false)
public abstract class DhWorkerTimingMixin {
    @Unique private DhTaskTimings.Window shaderlod$owner;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void shaderlod$submitted(CallbackInfo ci){shaderlod$owner=DhTaskTimings.submitted();}
    @WrapMethod(method="run")
    private void shaderlod$run(Operation<Void> original){
        var t=DhTaskTimings.begin(shaderlod$owner,"worker",0);boolean failed=true;
        try{original.call();failed=false;}finally{DhTaskTimings.end(t,failed);}
    }
}
