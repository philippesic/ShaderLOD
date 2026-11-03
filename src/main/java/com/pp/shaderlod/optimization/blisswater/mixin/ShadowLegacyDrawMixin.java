package com.pp.shaderlod.optimization.blisswater.mixin;
import com.pp.shaderlod.optimization.blisswater.BlissWaterShadowRuntime;import com.pp.shaderlod.optimization.blissspatial.BlissSpatialDrawScope;import com.llamalad7.mixinextras.injector.wrapoperation.*;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList",remap=false)
public abstract class ShadowLegacyDrawMixin{
 @WrapOperation(method="multiDrawElementsBaseVertex",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1,allow=1)
 private void shaderlod$waterDraw(int mode,long counts,int type,long indices,int drawCount,long baseVertices,Operation<Void> original){BlissSpatialDrawScope.draw(BlissWaterShadowRuntime::draw,()->original.call(mode,counts,type,indices,drawCount,baseVertices),BlissWaterShadowRuntime::returned,BlissWaterShadowRuntime::failed,BlissWaterShadowRuntime::revoke);}
}
