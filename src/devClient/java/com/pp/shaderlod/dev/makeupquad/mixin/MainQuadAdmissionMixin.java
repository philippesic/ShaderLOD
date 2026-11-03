package com.pp.shaderlod.dev.makeupquad.mixin;
import com.pp.shaderlod.dev.makeupquad.MainQuadAdmission;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Observe only main invocation arguments/result; never read/write native arrays or return values. */
@Mixin(targets="com.pp.shaderlod.integration.makeupquad.AdjacentQuadCoalescer",remap=false)
public abstract class MainQuadAdmissionMixin {
    @Inject(method="compact(JJJIII)I",at=@At("HEAD"),require=1,allow=1,remap=false)
    private static void shaderlod$mainHead(long pointers,long counts,long bases,int size,int capacity,int stride,
            CallbackInfoReturnable<Integer> ci) { MainQuadAdmission.head(size); }
    @Inject(method="compact(JJJIII)I",at=@At("RETURN"),require=1,remap=false)
    private static void shaderlod$mainReturn(long pointers,long counts,long bases,int size,int capacity,int stride,
            CallbackInfoReturnable<Integer> ci) { MainQuadAdmission.returned(ci.getReturnValueI()); }
}
