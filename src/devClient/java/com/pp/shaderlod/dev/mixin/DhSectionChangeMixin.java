package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.RenderGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    targets = "com.seibel.distanthorizons.common.wrappers.minecraft.ProfilerWrapper_neoforge",
    remap = false)
@SuppressWarnings("UnusedMethod")
public abstract class DhSectionChangeMixin {
  @Inject(method = "popPush(Ljava/lang/String;)V", at = @At("HEAD"), require = 1, remap = false)
  @SuppressWarnings("UnusedVariable")
  private void shaderlod$sectionChanged(String name, CallbackInfo callback) {
    RenderGroups.ended();
    RenderGroups.began("DH " + name);
  }
}
