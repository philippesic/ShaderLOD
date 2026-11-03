package com.pp.shaderlod.fabric.dev.indexcache;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.*;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class IndexScalarRendererMixin {
    @Inject(method="fillCommandBuffer(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataStorage;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;ZZ)V",at=@At("HEAD"),require=1,remap=false)
    private static void shaderlod$beforeWrite(MultiDrawBatch batch,RenderRegion region,SectionRenderDataStorage storage,
        ChunkRenderList list,CameraTransform camera,TerrainRenderPass pass,boolean cull,boolean indexed,CallbackInfo ci) {
        ((IndexScalarAccess)(Object)batch).shaderlod$invalidateIndexScalar();
    }
    @Inject(method="render",at=@At("HEAD"),require=1,remap=false)
    private void shaderlod$scope(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,
        TerrainRenderPass pass,CameraTransform camera,net.caffeinemc.mods.sodium.client.util.FogParameters fog,
        boolean sorted,com.mojang.blaze3d.textures.GpuSampler sampler,CallbackInfo ci) {
        IndexScalarScope.begin(pass==DefaultTerrainRenderPasses.SOLID || pass==DefaultTerrainRenderPasses.CUTOUT);
    }
    @Inject(method="render",at=@At("RETURN"),require=1,remap=false)
    private void shaderlod$end(CallbackInfo ci) { IndexScalarScope.end(); }
}
