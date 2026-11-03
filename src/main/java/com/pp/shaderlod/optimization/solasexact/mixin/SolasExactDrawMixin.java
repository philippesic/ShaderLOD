package com.pp.shaderlod.optimization.solasexact.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;import com.llamalad7.mixinextras.injector.wrapoperation.*;import com.pp.shaderlod.optimization.solasexact.SolasExactSsrRuntime;
/** Permission belongs to this exact native invocation, including exceptional exits. */
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList",remap=false)
public abstract class SolasExactDrawMixin{
 @WrapOperation(method="multiDrawElementsBaseVertex",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1)
 private void draw(int mode,long counts,int type,long indices,int drawcount,long bases,Operation<Void> original){com.pp.shaderlod.optimization.solasexact.SolasReceiverRuntime.currentDraw(()->SolasExactSsrRuntime.currentDraw(()->original.call(mode,counts,type,indices,drawcount,bases)));}
}
