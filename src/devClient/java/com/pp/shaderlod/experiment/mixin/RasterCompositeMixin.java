package com.pp.shaderlod.experiment.mixin;
import com.pp.shaderlod.experiment.ComplementaryRasterR1;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.gl.GLDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CompositeRenderer.class, remap=false)
public abstract class RasterCompositeMixin {
 @Invoker("setupMipmapping") public static void nativeMip(RenderTarget target, boolean alt) { throw new AssertionError(); }
 @Inject(method="renderAll()V", at=@At("HEAD"), require=1)
 private void begin(CallbackInfo ci) { ComplementaryRasterR1.debugGroup(""); ComplementaryRasterR1.leaveGeometry(); }
 @Redirect(method="renderAll()V", at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/systems/RenderSystem;viewport(IIII)V"), require=1)
 private void viewport(int x, int y, int w, int h) { ComplementaryRasterR1.passViewport(ComplementaryRasterR1.passName(), x, y, w, h); }
 @Redirect(method="renderAll()V", at=@At(value="INVOKE", target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"), require=1)
 private void draw(FullScreenQuadRenderer renderer) { if (!(ComplementaryRasterR1.ownsPrivateGraph() && ComplementaryRasterR1.auxiliary(ComplementaryRasterR1.passName()) && !ComplementaryRasterR1.isActive())) renderer.renderQuad(); }
 @Redirect(method="renderAll()V", at=@At(value="INVOKE", target="Lnet/irisshaders/iris/pipeline/CompositeRenderer;setupMipmapping(Lnet/irisshaders/iris/targets/RenderTarget;Z)V"), require=1)
 private void mip(RenderTarget target, boolean alt) { if (!ComplementaryRasterR1.skipMip(ComplementaryRasterR1.passName(), target)) nativeMip(target, alt); }
}
