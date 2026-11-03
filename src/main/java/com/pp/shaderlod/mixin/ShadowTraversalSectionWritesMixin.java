package com.pp.shaderlod.mixin;

import com.pp.shaderlod.ShadowTraversalTouches;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned Sodium 0.6.13 native writes. Every injection runs after the original body. */
@Mixin(value = RenderSection.class, remap = false)
public abstract class ShadowTraversalSectionWritesMixin {
    @Inject(method = "setLastVisibleFrame(I)V", at = @At("RETURN"), require = 1)
    private void shaderlod$stamp(int frame, CallbackInfo ci) {
        if (!ShadowTraversalTouches.isCapturing()) return;
        var section = (RenderSection) (Object) this;
        ShadowTraversalTouches.recordStamp(section, section.getLastVisibleFrame(), section.isDisposed());
    }

    @Inject(method = "setIncomingDirections(I)V", at = @At("RETURN"), require = 1)
    private void shaderlod$directions(int directions, CallbackInfo ci) {
        if (!ShadowTraversalTouches.isCapturing()) return;
        var section = (RenderSection) (Object) this;
        ShadowTraversalTouches.recordDirections(section, section.getIncomingDirections(), section.isDisposed(),
                ShadowTraversalTouches.Writer.DIRECTION_SET);
    }

    @Inject(method = "addIncomingDirections(I)V", at = @At("RETURN"), require = 1)
    private void shaderlod$union(int directions, CallbackInfo ci) {
        if (!ShadowTraversalTouches.isCapturing()) return;
        var section = (RenderSection) (Object) this;
        ShadowTraversalTouches.recordDirections(section, section.getIncomingDirections(), section.isDisposed(),
                ShadowTraversalTouches.Writer.DIRECTION_UNION);
    }

    @Inject(method = "delete()V", at = @At("RETURN"), require = 1)
    private void shaderlod$disposed(CallbackInfo ci) {
        if (ShadowTraversalTouches.isCapturing()) ShadowTraversalTouches.recordDisposal(this);
    }
}
