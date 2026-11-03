package com.pp.shaderlod.optimization.blisspixel.mixin;
import com.pp.shaderlod.optimization.blisspixel.BlissPixelRuntime;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** MC1.21.11 actual final draw, after Iris CustomPass and command encoder setup. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlStateManager",remap=false)
public abstract class BlissPixelRenderPassDrawMixin {
 @Inject(method="_drawElements(IIIJ)V",at=@At("HEAD"),require=1)private static void shaderlod$before(int mode,int count,int type,long pointer,CallbackInfo ci){BlissPixelRuntime.beforeDraw();}
 @Inject(method="_drawElements(IIIJ)V",at=@At("RETURN"),require=1)private static void shaderlod$after(int mode,int count,int type,long pointer,CallbackInfo ci){BlissPixelRuntime.afterDraw();}
}
