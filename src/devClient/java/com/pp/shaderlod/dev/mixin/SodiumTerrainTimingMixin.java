package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.GpuSamplingPolicy.Category;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumTerrainTimingMixin {
    @Inject(method = "setupTerrain", at = @At("HEAD"), remap = false)
    private void shaderlod$beginSetup(CallbackInfo ci) {
        String scope = "terrain-setup/" + (ShadowRenderer.ACTIVE ? "shadow" : "world");
        CpuTimers.beginLayer(scope);
        GpuTimers.beginCategory(scope, Category.TERRAIN);
    }

    @Inject(method = "setupTerrain", at = @At("RETURN"), remap = false)
    private void shaderlod$endSetup(CallbackInfo ci) {
        GpuTimers.endCategory();
        CpuTimers.endLayer("terrain-setup/" + (ShadowRenderer.ACTIVE ? "shadow" : "world"));
    }

    @Inject(method = "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;Lnet/minecraft/client/Camera;FLcom/llamalad7/mixinextras/sugar/ref/LocalBooleanRef;)V", at = @At("HEAD"), remap = false)
    private void shaderlod$beginBlockEntities(CallbackInfo ci) { CpuTimers.beginLayer("block-entity-build/" + (ShadowRenderer.ACTIVE ? "shadow" : "world")); }

    @Inject(method = "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;Lnet/minecraft/client/Camera;FLcom/llamalad7/mixinextras/sugar/ref/LocalBooleanRef;)V", at = @At("RETURN"), remap = false)
    private void shaderlod$endBlockEntities(CallbackInfo ci) { CpuTimers.endLayer("block-entity-build/" + (ShadowRenderer.ACTIVE ? "shadow" : "world")); }

    @Inject(method = "drawChunkLayer", at = @At("HEAD"), remap = false)
    private void shaderlod$begin(RenderType type, @Coerce Object matrices, double x, double y, double z, CallbackInfo ci) {
        if (type != RenderType.solid() && type != RenderType.translucent()) return;
        String scope = scope(type);
        CpuTimers.beginLayer(scope);
        GpuTimers.beginCategory(scope, Category.TERRAIN);
    }

    @Inject(method = "drawChunkLayer", at = @At("RETURN"), remap = false)
    private void shaderlod$end(RenderType type, @Coerce Object matrices, double x, double y, double z, CallbackInfo ci) {
        if (type != RenderType.solid() && type != RenderType.translucent()) return;
        GpuTimers.endCategory();
        CpuTimers.endLayer(scope(type));
    }

    @org.spongepowered.asm.mixin.Unique
    private static String scope(RenderType type) {
        return "terrain/" + (ShadowRenderer.ACTIVE ? "shadow/" : "world/")
                + (type == RenderType.solid() ? "opaque+cutout" : "translucent");
    }
}
