package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.HalfAoUvObservation;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.mojang.blaze3d.platform.GlStateManager",remap=false)
public abstract class HalfAoUvDrawMixin {
 @Inject(method="_drawElements(IIIJ)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL11;glDrawElements(IIIJ)V",shift=At.Shift.BEFORE),require=1,allow=1)
 private static void before(int mode,int count,int type,long offset,CallbackInfo ci){HalfAoUvObservation.before(mode,count,type,offset);}
 @Inject(method="_drawElements(IIIJ)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL11;glDrawElements(IIIJ)V",shift=At.Shift.AFTER),require=1,allow=1)
 private static void after(int mode,int count,int type,long offset,CallbackInfo ci){HalfAoUvObservation.after();}
}
