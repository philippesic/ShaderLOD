package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.GpuSamplingPolicy.Category;
import net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource;
import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real Iris entity/block-entity buffer draws; individual entity callbacks do not draw GPU work. */
@Mixin(value = FullyBufferedMultiBufferSource.class, remap = false)
public abstract class BufferedDrawTimingMixin {
    @Inject(method = "endBatch()V", at = @At("HEAD"), remap = false)
    private void shaderlod$begin(CallbackInfo ci) { begin("remaining"); }
    @Inject(method = "endBatch()V", at = @At("RETURN"), remap = false)
    private void shaderlod$end(CallbackInfo ci) { end("remaining"); }
    @Inject(method = "endBatchWithType", at = @At("HEAD"), remap = false)
    private void shaderlod$beginType(TransparencyType type, CallbackInfo ci) { begin(type.name().toLowerCase(java.util.Locale.ROOT)); }
    @Inject(method = "endBatchWithType", at = @At("RETURN"), remap = false)
    private void shaderlod$endType(TransparencyType type, CallbackInfo ci) { end(type.name().toLowerCase(java.util.Locale.ROOT)); }
    @org.spongepowered.asm.mixin.Unique
    private static String scope(String type) { return "buffered-draws/" + (ShadowRenderer.ACTIVE ? "shadow/" : "world/") + type; }
    @org.spongepowered.asm.mixin.Unique
    private static void begin(String type) {
        CpuTimers.beginLayer(scope(type));
        GpuTimers.beginCategory(scope(type), Category.BUFFER_DRAWS);
    }
    @org.spongepowered.asm.mixin.Unique
    private static void end(String type) {
        GpuTimers.endCategory();
        CpuTimers.endLayer(scope(type));
    }
}
