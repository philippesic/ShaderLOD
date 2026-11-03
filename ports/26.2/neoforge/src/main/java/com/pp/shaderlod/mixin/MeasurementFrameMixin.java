package com.pp.shaderlod.mixin;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Explicit pinned normal GameRenderer boundary; disabled instrumentation is one branch. */
@Mixin(value=GameRenderer.class,remap=false)
public abstract class MeasurementFrameMixin {
    @Inject(method="render(Lnet/minecraft/client/DeltaTracker;Z)V",at=@At("HEAD"),require=1,allow=1,remap=false)
    private void shaderlod$begin(CallbackInfo ci) {if(!RuntimeMeasurement.needed())return;
        var client=Minecraft.getInstance();
        RuntimeMeasurement.context(client.level,net.irisshaders.iris.Iris.getCurrentPack().orElse(null));
        RuntimeMeasurement.begin(client.level!=null, client.gui.screen()!=null);}
    @Inject(method="render(Lnet/minecraft/client/DeltaTracker;Z)V",at=@At("RETURN"),require=1,remap=false)
    private void shaderlod$end(CallbackInfo ci) {RuntimeMeasurement.end();}
}
