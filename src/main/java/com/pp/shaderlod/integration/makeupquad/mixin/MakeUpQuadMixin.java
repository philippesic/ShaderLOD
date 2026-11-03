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
import com.pp.shaderlod.optimization.NativeAdmissionCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Native fill has finished; native max-count/capacity, uniforms and submission remain ahead. */
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class MakeUpQuadMixin {
    @Inject(method="fillCommandBuffer(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataStorage;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Z)V",at=@At(value="RETURN",ordinal=1),require=1,allow=1,remap=false)
    private static void shaderlod$coalesce(MultiDrawBatch batch,RenderRegion region,SectionRenderDataStorage storage,
            ChunkRenderList list,CameraTransform camera,TerrainRenderPass pass,boolean cull,CallbackInfo ci) {
        if((pass!=DefaultTerrainRenderPasses.SOLID && pass!=DefaultTerrainRenderPasses.CUTOUT) ||
            ShadowRenderingState.areShadowsCurrentlyBeingRendered() || !MakeUpQuadRuntime.enabledAtTerrainConsumer(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION)) return;
        int before=batch.size;
        long wall=NativeAdmissionCost.wallStart(),cpu=NativeAdmissionCost.cpuStart(wall),compacted;
        try {compacted=AdjacentQuadCoalescer.compactWithMaximum(batch.pElementPointer,batch.pElementCount,
                batch.pBaseVertex,batch.size,batch.capacity(),Pointer.POINTER_SIZE);}
        finally {NativeAdmissionCost.finish(NativeAdmissionCost.COMPACTOR,wall,cpu,true);}
        batch.size=AdjacentQuadCoalescer.compactedSize(compacted);
        int maximum=AdjacentQuadCoalescer.checkedMaximum(compacted);
        // The same fill's begin allowed private scalar recording. Publication cannot activate it;
        // generation/count checks and the consumer's current admission remain mandatory.
        if(maximum>=0 && (Object)batch instanceof LegacyScalarBatch scalar) {
            wall=NativeAdmissionCost.wallStart();cpu=NativeAdmissionCost.cpuStart(wall);
            try {scalar.shaderlod$scalar().compacted(before,batch.size,maximum,MakeUpQuadLifecycle.generation());}
            finally {NativeAdmissionCost.finish(NativeAdmissionCost.PUBLICATION,wall,cpu,true);}
        }
        if(com.pp.shaderlod.optimization.RendererWorkDiagnostics.ENABLED)
            com.pp.shaderlod.optimization.RendererWorkDiagnostics.compactionReturned(before,batch.size);
    }
}
