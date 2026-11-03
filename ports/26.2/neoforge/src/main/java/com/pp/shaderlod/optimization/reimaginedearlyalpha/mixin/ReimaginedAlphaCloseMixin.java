package com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime;
@Mixin(targets="com.mojang.blaze3d.opengl.GlProgram",remap=false)
public abstract class ReimaginedAlphaCloseMixin {@Inject(method="close()V",at=@At("HEAD"),require=1,allow=1)private void shaderlod$reimaginedClose(CallbackInfo ci){ReimaginedEarlyAlphaRuntime.closed(this);}}
