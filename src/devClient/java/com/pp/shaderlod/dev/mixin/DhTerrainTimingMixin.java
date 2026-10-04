package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.GpuSamplingPolicy.Category;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact pinned DH overload: opaque=true, transparent=false; not the deferred flag overload. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.LodRenderer", remap = false)
public abstract class DhTerrainTimingMixin {
    @Inject(method = "renderTerrain(Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/IDhTerrainRenderer;Lcom/seibel/distanthorizons/core/render/RenderBufferHandler;Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At("HEAD"), remap = false)
    private void shaderlod$begin(@Coerce Object renderer, @Coerce Object buffers, @Coerce Object params,
                                 boolean opaque, @Coerce Object profiler, CallbackInfo ci) {
        String scope = scope(opaque);
        CpuTimers.beginLayer(scope);
        GpuTimers.beginCategory(scope, Category.DH_DRAW);
    }

    @Inject(method = "renderTerrain(Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/IDhTerrainRenderer;Lcom/seibel/distanthorizons/core/render/RenderBufferHandler;Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At("RETURN"), remap = false)
    private void shaderlod$end(@Coerce Object renderer, @Coerce Object buffers, @Coerce Object params,
                               boolean opaque, @Coerce Object profiler, CallbackInfo ci) {
        GpuTimers.endCategory();
        CpuTimers.endLayer(scope(opaque));
    }

    @org.spongepowered.asm.mixin.Unique
    private static String scope(boolean opaque) {
        return "dh/" + (ShadowRenderer.ACTIVE ? "shadow/" : "world/") + (opaque ? "opaque" : "translucent");
    }
}
