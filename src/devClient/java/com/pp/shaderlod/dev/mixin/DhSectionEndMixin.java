package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.DhSections;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    targets =
        "com.seibel.distanthorizons.common.wrappers.minecraft.ProfilerWrapper$ProfileBlock_neoforge",
    remap = false)
@SuppressWarnings("UnusedMethod")
public abstract class DhSectionEndMixin {
  @Inject(method = "close()V", at = @At("HEAD"), require = 1, remap = false)
  @SuppressWarnings("UnusedVariable")
  private void shaderlod$sectionEnded(CallbackInfo callback) {
    DhSections.ended();
  }
}
