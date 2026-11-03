package com.pp.shaderlod.mixin;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Minecraft.class,remap=false)
public abstract class MeasurementCleanupMixin {
    @Inject(method="close()V",at=@At("HEAD"),require=1,allow=1,remap=false)
    private void shaderlod$close(CallbackInfo ci) {com.pp.shaderlod.optimization.vrs.BloomVrsRuntime.invalidate();com.pp.shaderlod.benchmark.BenchmarkLiveObservations.reset();RuntimeMeasurement.close();}
}
