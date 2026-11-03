package com.pp.shaderlod.optimization.solasearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.pp.shaderlod.optimization.solasearlyalpha.SolasEarlyAlphaRuntime;
@Mixin(targets="com.mojang.blaze3d.opengl.GlProgram",remap=false)
public abstract class SolasAlphaCloseMixin {@Inject(method="close()V",at=@At("HEAD"),require=1,allow=1)private void shaderlod$solasClose(CallbackInfo ci){SolasEarlyAlphaRuntime.closed(this);}}
