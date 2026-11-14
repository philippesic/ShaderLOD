package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.DhSections;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets = "com.seibel.distanthorizons.common.wrappers.minecraft.ProfilerWrapper_neoforge",
    remap = false)
@SuppressWarnings("UnusedMethod")
public abstract class DhSectionBeginMixin {
  @Inject(
      method =
          "push(Ljava/lang/String;)Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper$IProfileBlock;",
      at = @At("HEAD"),
      require = 1,
      remap = false)
  @SuppressWarnings("UnusedVariable")
  private void shaderlod$sectionBegan(String name, CallbackInfoReturnable<Object> callback) {
    DhSections.began(name);
  }
}
