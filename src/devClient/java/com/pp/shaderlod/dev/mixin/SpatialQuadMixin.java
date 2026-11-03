package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.timing.SpatialLayoutProbe;
import com.pp.shaderlod.dev.PixelHistorySession;
import com.pp.shaderlod.dev.SettledConsumerUniforms;
import com.pp.shaderlod.dev.timing.PixelCoverageProbe;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FullScreenQuadRenderer.class, remap = false)
public abstract class SpatialQuadMixin {
    @Inject(method = "renderQuad()V", at = @At("HEAD"), require = 1, remap = false)
    private void shaderlod$before(CallbackInfo callback) {
        if (com.pp.shaderlod.dev.BslMipParity.replaying()) return;
        SpatialLayoutProbe.beforeQuadDraw();
        PixelCoverageProbe.beforeQuadDraw();
        PixelHistorySession.beforeQuadDraw();
        SettledConsumerUniforms.beforeQuadDraw();
        com.pp.shaderlod.dev.BlissChainWitness.beforeQuad();
        com.pp.shaderlod.dev.SildurReceiverWitness.beforeQuad();
        com.pp.shaderlod.dev.MakeupCloudWitness.beforeQuad();
        com.pp.shaderlod.dev.BslMipWitness.beforeQuad();
        com.pp.shaderlod.dev.BslMipParity.beforeQuad();
        com.pp.shaderlod.dev.CompositeCopyWitness.beforeQuad();
        com.pp.shaderlod.dev.MakeupMrtProbe.beforeQuad();
        com.pp.shaderlod.dev.ComplementaryCopyGraph.beforeQuad();
        com.pp.shaderlod.dev.ComplementaryRasterActivationProbe.beforeQuad();
    }
    @Inject(method = "renderQuad()V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V", shift = At.Shift.AFTER), require = 1, remap = false)
    private void shaderlod$graphNativeDrawReturned(CallbackInfo callback) {
        com.pp.shaderlod.dev.MakeupMrtProbe.nativeDrawReturned();
        com.pp.shaderlod.dev.ComplementaryCopyGraph.nativeDrawReturned();
    }
    @Inject(method = "renderQuad()V", at = @At("RETURN"), require = 1, remap = false)
    private void shaderlod$after(CallbackInfo callback) {
        if (com.pp.shaderlod.dev.BslMipParity.replaying()) return;
        SpatialLayoutProbe.afterQuadDraw();
        com.pp.shaderlod.dev.BslMipParity.afterQuad();
        com.pp.shaderlod.dev.CompositeCopyWitness.afterQuad();
        com.pp.shaderlod.dev.MakeupMrtProbe.afterQuad();
        com.pp.shaderlod.dev.ComplementaryCopyGraph.afterQuad();
    }
}
