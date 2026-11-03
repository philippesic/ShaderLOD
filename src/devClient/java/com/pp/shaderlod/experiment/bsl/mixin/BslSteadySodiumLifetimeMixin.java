package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=GlProgram.class,remap=false)
public abstract class BslSteadySodiumLifetimeMixin {
 @Inject(method="delete()V",at=@At("HEAD"),require=1)
 private void removed(CallbackInfo ci){BslShadowFieldBridge.sodiumDeleted((GlProgram<?>)(Object)this);}
}
