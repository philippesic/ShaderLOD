package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Texture unit indices use the combined namespace. The fragment-stage limit
 * counts active sampler resources, not the largest legal binding index.
 * Reserved holes must not consume that stage's sampler resource budget.
 * GL linking still enforces every per-stage limit. Keep Iris cleanup's bound
 * equal to its allocation bound and stay inside Minecraft's tracked array.
 * This development-runtime correction changes no pack bytes or shader math.
 */
@Mixin(targets="net.irisshaders.iris.gl.sampler.SamplerLimits",remap=false)
public abstract class SamplerUnitBudgetMixin {
    @ModifyExpressionValue(method="<init>",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/GlStateManager;_getInteger(I)I",ordinal=0),require=1)
    private int shaderlod$unitIndexBudget(int stageLimit) {
        int combined=GL20C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        int tracked=java.lang.reflect.Array.getLength(GlStateManagerAccessor.getTEXTURES());
        int budget=Math.min(combined,tracked);
        if (budget<stageLimit) throw new IllegalStateException("Combined/tracked texture unit budget below native stage limit");
        System.err.println("ShaderLOD development sampler-index repair v1: fragment="+stageLimit+
            " combined="+combined+" tracked="+tracked+" allocatorAndCleanup="+budget);
        return budget;
    }
}
