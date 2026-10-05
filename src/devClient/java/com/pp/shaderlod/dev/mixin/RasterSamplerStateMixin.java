package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.CloudSamplerStateProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opt-in readback immediately before the pinned Sodium native raster submission. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList", remap = false)
public abstract class RasterSamplerStateMixin {
    @Inject(method = "multiDrawElementsBaseVertex(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlIndexType;)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V",
                    shift = At.Shift.BEFORE), require = 1, allow = 1, remap = false)
    private void shaderlod$observeRasterSamplers(CallbackInfo callback) {
        CloudSamplerStateProbe.beforeRasterDraw();
    }
}
