package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity vertices are buffered: this scope intentionally reports CPU only. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityBuildTimingMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void shaderlod$begin(CallbackInfo ci) { CpuTimers.beginLayer(scope()); }
    @Inject(method = "render", at = @At("RETURN"))
    private void shaderlod$end(CallbackInfo ci) { CpuTimers.endLayer(scope()); }
    @org.spongepowered.asm.mixin.Unique
    private static String scope() { return "entity-build/" + (ShadowRenderer.ACTIVE ? "shadow" : "world"); }
}
