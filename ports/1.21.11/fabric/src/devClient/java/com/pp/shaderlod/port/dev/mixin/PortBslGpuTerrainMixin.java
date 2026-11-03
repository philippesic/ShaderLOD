package com.pp.shaderlod.port.dev.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.fabric.dev.budget.BslGpuBudget;
import com.pp.shaderlod.fabric.dev.budget.BslAlphaCapture;
import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class PortBslGpuTerrainMixin {
    @WrapMethod(method="render(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderListIterable;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZLcom/mojang/blaze3d/textures/GpuSampler;)V",remap=false)
    private void shaderlod$terrain(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,FogParameters fog,boolean flag,GpuSampler sampler,Operation<Void> original) {
        if(!BslGpuBudget.requested()) { original.call(matrices,commands,lists,pass,camera,fog,flag,sampler); return; }
        var prior=BslAlphaCapture.enter(pass);
        BslGpuBudget.terrainBegin("terrain/"+(ShadowRenderingState.areShadowsCurrentlyBeingRendered()?"shadow/":"world/")+pass);
        try { original.call(matrices,commands,lists,pass,camera,fog,flag,sampler); } finally { BslGpuBudget.terrainEnd(); BslAlphaCapture.restore(prior); }
    }
}
