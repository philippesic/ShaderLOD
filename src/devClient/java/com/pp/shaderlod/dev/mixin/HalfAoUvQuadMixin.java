package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.HalfAoUvObservation;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.pathways.FullScreenQuadRenderer",remap=false)
public abstract class HalfAoUvQuadMixin {
 @Inject(method="renderQuad()V",at=@At("HEAD"),require=1) private void enter(CallbackInfo ci){HalfAoUvObservation.quad(true);}
 @Inject(method="renderQuad()V",at=@At("RETURN"),require=1) private void leave(CallbackInfo ci){HalfAoUvObservation.quad(false);}
}
