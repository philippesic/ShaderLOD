package com.pp.shaderlod.integration.makeupquad.mixin;
import com.pp.shaderlod.integration.makeupquad.*;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch",remap=false)
public abstract class PortBatchMixin implements PortBatchState {
    @Unique private long shaderlod$rendererGeneration = -1;
    public long shaderlod$rendererGeneration() {return shaderlod$rendererGeneration;}
    public void shaderlod$rendererGeneration(long value) {shaderlod$rendererGeneration=value;}
    @Shadow public int size;
    @Shadow public boolean isFilled;
    @Unique private final IndexScalarState shaderlod$scalar = new IndexScalarState();
    @Unique private boolean shaderlod$scalarHit;
    public void shaderlod$invalidateIndexScalar() {shaderlod$scalar.invalidate();}
    @Inject(method={"clear()V","delete()V"},at=@At("HEAD"),require=2,remap=false)
    private void shaderlod$invalidate(CallbackInfo ci) {shaderlod$scalar.invalidate();}
    @Inject(method="getIndexBufferSize()I",at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void shaderlod$scalarLookup(CallbackInfoReturnable<Integer> ci) {
        shaderlod$scalarHit=false;
        if (!net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() &&
            MakeUpQuadRuntime.enabled(com.pp.shaderlod.optimization.OptimizationId.INDEX_SCALAR_CACHE) &&
            shaderlod$scalar.reusable(isFilled,size,MakeUpQuadLifecycle.generation())) {
            shaderlod$scalarHit=true;ci.setReturnValue(shaderlod$scalar.maximum());
        }
    }
    @Inject(method="getIndexBufferSize()I",at=@At("RETURN"),require=1,remap=false)
    private void shaderlod$scalarRemember(CallbackInfoReturnable<Integer> ci) {
        if (!shaderlod$scalarHit) shaderlod$scalar.remember(isFilled,size,ci.getReturnValueI(),MakeUpQuadLifecycle.generation());
    }
    @Unique private int shaderlod$capacity;
    @Unique private boolean shaderlod$rendererEnabled;
    @Inject(method="<init>(I)V",at=@At("RETURN"),require=1,allow=1,remap=false)
    private void shaderlod$captureCapacity(int capacity,CallbackInfo ci) { shaderlod$capacity=capacity; }
    public int shaderlod$capacity() { return shaderlod$capacity; }
    public boolean shaderlod$rendererEnabled() { return shaderlod$rendererEnabled; }
    public void shaderlod$rendererEnabled(boolean value) { shaderlod$rendererEnabled=value; }
}
