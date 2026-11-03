package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.PortRendererFrames;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="com.pp.shaderlod.integration.makeupquad.SubmissionQuadCopy",remap=false)
public abstract class PortRendererCommandMixin {
    @Inject(method="compact(Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;IILorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;)I",at=@At("RETURN"),require=1,remap=false)
    private static void shaderlod$helper(PointerBuffer p,IntBuffer c,IntBuffer b,int size,int max,PointerBuffer op,IntBuffer oc,IntBuffer ob,CallbackInfoReturnable<Integer> ci) {
        PortRendererFrames.helper(size,ci.getReturnValueI());
    }
}
