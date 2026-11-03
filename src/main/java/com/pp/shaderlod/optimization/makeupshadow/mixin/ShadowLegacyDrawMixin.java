package com.pp.shaderlod.optimization.makeupshadow.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;import com.pp.shaderlod.optimization.makeupshadow.*;
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList",remap=false)
public abstract class ShadowLegacyDrawMixin {
 @WrapOperation(method="multiDrawElementsBaseVertex",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1)
 private void draw(int mode,long counts,int type,long indices,int count,long bases,Operation<Void> original){MakeUpReflectionRuntime.currentDraw(()->MakeUpShadowRuntime.currentDraw(()->original.call(mode,counts,type,indices,count,bases)));}
}
