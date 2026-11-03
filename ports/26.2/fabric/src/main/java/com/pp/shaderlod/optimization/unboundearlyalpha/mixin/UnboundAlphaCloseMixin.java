package com.pp.shaderlod.optimization.unboundearlyalpha.mixin;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime;
@Mixin(targets="com.mojang.blaze3d.opengl.GlProgram",remap=false)
public abstract class UnboundAlphaCloseMixin {@Inject(method="close()V",at=@At("HEAD"),require=1,allow=1)private void shaderlod$unboundClose(CallbackInfo ci){UnboundEarlyAlphaRuntime.closed(this);}}
