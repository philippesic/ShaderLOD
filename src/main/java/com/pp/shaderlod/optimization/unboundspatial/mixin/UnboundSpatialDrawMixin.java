package com.pp.shaderlod.optimization.unboundspatial.mixin;
import com.pp.shaderlod.optimization.unboundspatial.*;import com.llamalad7.mixinextras.injector.wrapoperation.*;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList",remap=false)public abstract class UnboundSpatialDrawMixin {
 @WrapOperation(method="multiDrawElementsBaseVertex",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1,allow=1)
 private void draw(int mode,long count,int type,long indices,int draws,long base,Operation<Void> original){UnboundExactSsrRuntime.currentDraw(()->{UnboundWorldPixelRuntime.beforeNativeDraw();UnboundSpatialRuntime.draw(()->original.call(mode,count,type,indices,draws,base));});}
}
