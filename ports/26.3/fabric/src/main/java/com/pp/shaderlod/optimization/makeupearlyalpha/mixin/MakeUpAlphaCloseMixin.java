package com.pp.shaderlod.optimization.makeupearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime;
@Mixin(targets="com.mojang.renderpearl.backend.opengl.GlProgram",remap=false)
public abstract class MakeUpAlphaCloseMixin {@Inject(method="close()V",at=@At("HEAD"),require=1,allow=1)private void shaderlod$makeupClose(CallbackInfo ci){MakeUpEarlyAlphaRuntime.closed(this);}}
