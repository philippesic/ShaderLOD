package com.pp.shaderlod.optimization.blissearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.pp.shaderlod.optimization.blissearlyalpha.BlissEarlyAlphaRuntime;
@Mixin(targets="com.mojang.blaze3d.opengl.GlProgram",remap=false)
public abstract class BlissAlphaCloseMixin {@Inject(method="close()V",at=@At("HEAD"),require=1,allow=1)private void shaderlod$blissClose(CallbackInfo ci){BlissEarlyAlphaRuntime.closed(this);}}
