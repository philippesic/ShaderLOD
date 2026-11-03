package com.pp.shaderlod.integration.makeupquad.mixin;
import com.pp.shaderlod.integration.makeupquad.*;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.lwjgl.system.Pointer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Native fill has finished; native max-count/capacity, uniforms and submission remain ahead. */
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class MakeUpQuadMixin {
    // Newer Sodium caches filled batches. A gate transition must reconstruct native commands,
    // including Off/Quality, Apply suspension, fallback and source/options/stack mismatch.
    @Redirect(method="render",at=@At(value="FIELD",target="Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;isFilled:Z",opcode=180),require=1,allow=1,remap=false)
    private boolean shaderlod$cacheGate(MultiDrawBatch batch) {
        var state=(PortBatchState)(Object)batch;
        boolean enabled=!ShadowRenderingState.areShadowsCurrentlyBeingRendered() && MakeUpQuadRuntime.enabled(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION);
        long generation=MakeUpQuadLifecycle.generation();
        if (state.shaderlod$rendererEnabled()!=enabled || state.shaderlod$rendererGeneration()!=generation) {
            batch.clear(); state.shaderlod$rendererEnabled(enabled); state.shaderlod$rendererGeneration(generation);
        }
        return batch.isFilled;
    }
    @Inject(method="fillCommandBuffer(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataStorage;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;ZZ)V",at=@At(value="RETURN",ordinal=1),require=1,allow=1,remap=false)
    private static void shaderlod$coalesce(MultiDrawBatch batch,RenderRegion region,SectionRenderDataStorage storage,
            ChunkRenderList list,CameraTransform camera,TerrainRenderPass pass,boolean cull,boolean indexed,CallbackInfo ci) {
        if((pass!=DefaultTerrainRenderPasses.SOLID && pass!=DefaultTerrainRenderPasses.CUTOUT) ||
            ShadowRenderingState.areShadowsCurrentlyBeingRendered() || !MakeUpQuadRuntime.enabled(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION)) return;
        int previousSize=batch.size;
        batch.size=AdjacentQuadCoalescer.compact(batch.pElementPointer,batch.pElementCount,
                batch.pBaseVertex,batch.size,((PortBatchState)(Object)batch).shaderlod$capacity(),Pointer.POINTER_SIZE);
        if (batch.size != previousSize) {
            long maximum=0;
            for (int i=0;i<batch.size;i++) maximum=Math.max(maximum,
                org.lwjgl.system.MemoryUtil.memGetInt(batch.pElementCount+4L*i));
            batch.maxElementCount=maximum; // native ensureCapacity consumes this cached maximum
        }
    }
}
