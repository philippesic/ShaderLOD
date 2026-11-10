package com.pp.shaderlod.integration.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.pp.shaderlod.integration.SamplerIndexPolicy;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.irisshaders.iris.gl.sampler.SamplerLimits", remap = false)
@SuppressWarnings("UnusedMethod")
public abstract class SamplerUnitBudgetMixin {
  @ModifyExpressionValue(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/mojang/blaze3d/platform/GlStateManager;_getInteger(I)I",
              ordinal = 0),
      require = 1)
  private int shaderlod$bindingIndexBudget(int fragmentResources) {
    return SamplerIndexPolicy.observe(
        fragmentResources,
        GL20C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),
        java.lang.reflect.Array.getLength(GlStateManagerAccessor.getTEXTURES()));
  }
}
