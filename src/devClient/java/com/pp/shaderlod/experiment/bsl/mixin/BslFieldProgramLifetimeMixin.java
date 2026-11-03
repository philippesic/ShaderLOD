package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import net.irisshaders.iris.gl.program.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Program.class,remap=false)
public abstract class BslFieldProgramLifetimeMixin {
 @Inject(method="destroyInternal()V",at=@At("HEAD"),require=1)
 private void removed(CallbackInfo ci){
  Program program=(Program)(Object)this;
  BslShadowFieldBridge.nativeProgramDestroyed(program);
 }
}
