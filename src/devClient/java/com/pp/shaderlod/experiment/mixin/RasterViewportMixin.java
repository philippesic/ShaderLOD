package com.pp.shaderlod.experiment.mixin;
import com.pp.shaderlod.experiment.ComplementaryRasterR1;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=RenderSystem.class, remap=false)
public abstract class RasterViewportMixin {
 @Inject(method="viewport(IIII)V", at=@At("HEAD"), cancellable=true, require=1)
 private static void viewport(int x, int y, int w, int h, CallbackInfo ci) { if (ComplementaryRasterR1.replaceGeometryViewport(x, y, w, h)) ci.cancel(); }
}
