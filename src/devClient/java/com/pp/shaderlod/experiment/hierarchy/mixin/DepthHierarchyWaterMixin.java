package com.pp.shaderlod.experiment.hierarchy.mixin;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class DepthHierarchyWaterMixin {
 @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="render",require=1)
 private void scoped(ChunkRenderMatrices m,CommandList c,ChunkRenderListIterable l,TerrainRenderPass p,CameraTransform t,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){Throwable primary=null;try{original.call(m,c,l,p,t);}catch(RuntimeException|Error e){primary=e;throw e;}finally{try{com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.unbindWater();}catch(RuntimeException|Error cleanup){if(primary==null)throw cleanup;primary.addSuppressed(cleanup);}}}
 @Inject(method="render",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setModelViewMatrix(Lorg/joml/Matrix4fc;)V",shift=At.Shift.AFTER),require=1,allow=1)
 private void bind(ChunkRenderMatrices m,CommandList c,ChunkRenderListIterable l,TerrainRenderPass p,CameraTransform t,CallbackInfo ci){com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.bindWater(p);}
 @Inject(method="render",at=@At("HEAD"),require=1)private void begin(ChunkRenderMatrices m,CommandList c,ChunkRenderListIterable l,TerrainRenderPass p,CameraTransform t,CallbackInfo ci){com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.waterBegin(p);}
 @Inject(method="render",at=@At("RETURN"),require=1)private void end(ChunkRenderMatrices m,CommandList c,ChunkRenderListIterable l,TerrainRenderPass p,CameraTransform t,CallbackInfo ci){com.pp.shaderlod.experiment.hierarchy.DepthHierarchyR50.waterEnd(p);}
}
