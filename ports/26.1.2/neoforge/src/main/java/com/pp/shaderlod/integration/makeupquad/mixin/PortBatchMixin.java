package com.pp.shaderlod.integration.makeupquad.mixin;
import com.pp.shaderlod.integration.makeupquad.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch",remap=false)
public abstract class PortBatchMixin implements PortBatchState {
    @Unique private long shaderlod$rendererGeneration = -1;
    public long shaderlod$rendererGeneration() {return shaderlod$rendererGeneration;}
    public void shaderlod$rendererGeneration(long value) {shaderlod$rendererGeneration=value;}
    @Unique private int shaderlod$capacity;
    @Unique private boolean shaderlod$rendererEnabled;
    @Inject(method="<init>(I)V",at=@At("RETURN"),require=1,allow=1,remap=false)
    private void shaderlod$captureCapacity(int capacity,CallbackInfo ci) { shaderlod$capacity=capacity; }
    public int shaderlod$capacity() { return shaderlod$capacity; }
    public boolean shaderlod$rendererEnabled() { return shaderlod$rendererEnabled; }
    public void shaderlod$rendererEnabled(boolean value) { shaderlod$rendererEnabled=value; }
}
