package com.pp.shaderlod.fabric.dev.earlyalpha.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.fabric.dev.earlyalpha.BslEarlyAlphaObserver;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(GameRenderer.class)
public abstract class BslEarlyAlphaObserverFrameMixin {
 @WrapMethod(method="render(Lnet/minecraft/client/DeltaTracker;Z)V")
 private void shaderlod$privateObserver(DeltaTracker tracker,boolean renderLevel,Operation<Void> original){
  if(!BslEarlyAlphaObserver.REQUESTED&&!com.pp.shaderlod.fabric.dev.zeroemission.BslZeroEmissionObserver.REQUESTED){original.call(tracker,renderLevel);return;}
  BslEarlyAlphaObserver.before(renderLevel);com.pp.shaderlod.fabric.dev.zeroemission.BslZeroEmissionObserver.before(renderLevel);boolean completed=false;
  try{original.call(tracker,renderLevel);completed=true;}finally{BslEarlyAlphaObserver.after(completed);com.pp.shaderlod.fabric.dev.zeroemission.BslZeroEmissionObserver.after(completed);}
 }
}
