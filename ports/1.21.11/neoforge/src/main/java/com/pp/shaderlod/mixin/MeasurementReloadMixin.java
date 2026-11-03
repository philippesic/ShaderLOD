package com.pp.shaderlod.mixin;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Iris.class,remap=false)
public abstract class MeasurementReloadMixin {
    @Inject(method="reload()V",at=@At("HEAD"),require=1,allow=1,remap=false)
    private static void shaderlod$reload(CallbackInfo ci) {RuntimeMeasurement.onReload();com.pp.shaderlod.optimization.mips.DeadMipAdmission.invalidate();com.pp.shaderlod.optimization.CompileOptionControl.beforeReload();}
}
