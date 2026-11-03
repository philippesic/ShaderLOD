package com.pp.shaderlod.fabric.dev.indexcache;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch",remap=false)
public abstract class IndexScalarBatchMixin implements IndexScalarAccess {
    @Shadow public int size;
    @Shadow public boolean isFilled;
    @Unique private final IndexScalarState shaderlod$scalar=new IndexScalarState();
    @Unique private boolean shaderlod$memoReturn;
    public void shaderlod$invalidateIndexScalar() { shaderlod$scalar.invalidate(); }
    @Inject(method={"clear()V","delete()V"},at=@At("HEAD"),require=2,remap=false)
    private void shaderlod$invalidate(CallbackInfo ci) { shaderlod$scalar.invalidate(); }
    @Inject(method="getIndexBufferSize()I",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void shaderlod$lookup(CallbackInfoReturnable<Integer> ci) {
        shaderlod$memoReturn=false;
        if(IndexScalarScope.active() && shaderlod$scalar.reusable(isFilled,size)) {
            shaderlod$memoReturn=true;IndexScalarScope.hits++;IndexScalarScope.avoidedCountReads+=size;
            ci.setReturnValue(shaderlod$scalar.maximum());
        }
    }
    @Inject(method="getIndexBufferSize()I",at=@At("RETURN"),require=1,remap=false)
    private void shaderlod$remember(CallbackInfoReturnable<Integer> ci) {
        if(!shaderlod$memoReturn) {
            if(IndexScalarScope.admitted()) {IndexScalarScope.nativeScans++;IndexScalarScope.nativeCountReads+=Math.max(0,size);}
            shaderlod$scalar.remember(isFilled,size,ci.getReturnValueI());
        }
    }
}
