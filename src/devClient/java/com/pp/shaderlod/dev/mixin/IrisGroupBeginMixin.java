package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.RenderGroups;
import net.irisshaders.iris.gl.GLDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GLDebug.class, remap = false)
@SuppressWarnings("UnusedMethod")
public abstract class IrisGroupBeginMixin {
  @Inject(method = "pushGroup(ILjava/lang/String;)V", at = @At("HEAD"), require = 1, remap = false)
  @SuppressWarnings("UnusedVariable")
  private static void shaderlod$groupBegan(int id, String name, CallbackInfo callback) {
    RenderGroups.began(name);
  }
}
