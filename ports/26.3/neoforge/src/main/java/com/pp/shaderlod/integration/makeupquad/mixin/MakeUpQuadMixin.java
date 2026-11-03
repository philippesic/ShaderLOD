package com.pp.shaderlod.integration.makeupquad.mixin;
import com.pp.shaderlod.integration.makeupquad.*;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.*;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import net.caffeinemc.mods.sodium.client.gpu.device.context.GLDrawContext;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Only redirects the final submission; native cached commands and max-index reservation remain intact. */
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class MakeUpQuadMixin {
    @Redirect(method="render(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderListIterable;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZLcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;Lnet/minecraft/client/renderer/oit/OitStage;)V",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/gpu/device/batch/MultiDrawBatch;draw(Lnet/caffeinemc/mods/sodium/client/gpu/device/context/DrawContext;)V"),require=1,allow=1,remap=false)
    private void shaderlod$submit(MultiDrawBatch batch,DrawContext context,ChunkRenderMatrices matrices, ChunkRenderListIterable lists, TerrainRenderPass pass, CameraTransform camera, FogParameters fog, boolean sorted, com.mojang.renderpearl.api.commands.RenderPass renderPass, com.mojang.renderpearl.api.textures.GpuSampler sampler, com.mojang.renderpearl.api.buffers.GpuBufferSlice globals, com.mojang.renderpearl.api.buffers.GpuBuffer times, net.minecraft.client.renderer.oit.OitStage oit) {
        if ((pass!=DefaultTerrainRenderPasses.SOLID && pass!=DefaultTerrainRenderPasses.CUTOUT) ||
            ShadowRenderingState.areShadowsCurrentlyBeingRendered() || !MakeUpQuadRuntime.enabled(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION) ||
            batch.getClass()!=GLDrawBatch.class || context.getClass()!=GLDrawContext.class || batch.size<2 || batch.size>SubmissionQuadCopy.MAX_COMMANDS ||
            org.lwjgl.system.Pointer.POINTER_SIZE!=8) { batch.draw(context); return; }
        var original=(GLDrawBatchAccessor)(Object)batch;
        // getMaxElementCount was ensured against the shared IBO before this point. No larger draw is emitted.
        try (var stack=MemoryStack.stackPush()) {
            if (stack.getPointer()<16L*batch.size+128) { batch.draw(context); return; }
            var pointers=stack.mallocPointer(batch.size);var counts=stack.mallocInt(batch.size);var bases=stack.mallocInt(batch.size);
            int count=SubmissionQuadCopy.compact(MemoryUtil.memPointerBuffer(original.shaderlod$pointers(),batch.size),
                MemoryUtil.memIntBuffer(original.shaderlod$counts(),batch.size),MemoryUtil.memIntBuffer(original.shaderlod$bases(),batch.size),
                batch.size,batch.getMaxElementCount(),pointers,counts,bases);
            if (count<0 || count==batch.size) { batch.draw(context); return; }
            // Synchronous pinned OpenGL consumption. Do not retry native draw after a driver submission failure.
            pointers.limit(count);counts.limit(count);bases.limit(count);
            context.getPass().multiDrawIndexed(pointers,counts,bases,count);
            SubmissionCopyEvidence.record(batch.size,count);
        }
    }
}
