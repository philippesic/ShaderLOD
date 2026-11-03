package com.pp.shaderlod.optimization.bslearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.pp.shaderlod.optimization.bslearlyalpha.BslEarlyAlphaRuntime;
@Mixin(targets="com.mojang.renderpearl.backend.opengl.GlProgram",remap=false)
public abstract class BslAlphaCloseMixin {@Inject(method="close()V",at=@At("HEAD"),require=1,allow=1)private void shaderlod$bslClose(CallbackInfo ci){BslEarlyAlphaRuntime.closed(this);}}
