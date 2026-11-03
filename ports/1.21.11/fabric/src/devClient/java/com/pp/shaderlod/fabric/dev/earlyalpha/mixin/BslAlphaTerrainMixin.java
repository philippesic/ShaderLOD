package com.pp.shaderlod.fabric.dev.earlyalpha.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.fabric.dev.earlyalpha.BslEarlyAlpha;

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
public abstract class BslAlphaTerrainMixin {
    private static TerrainRenderPass current;
    @org.spongepowered.asm.mixin.injection.Inject(method="executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V",at=@org.spongepowered.asm.mixin.injection.At("HEAD"),require=1,allow=1,remap=false)
    private static void shaderlod$binding(CommandList commands,net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation tessellation,net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch batch,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {BslEarlyAlpha.beforeDraw(current);com.pp.shaderlod.fabric.dev.zeroemission.BslZeroEmissionObserver.draw(current);}

    @WrapMethod(method="render(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderListIterable;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZLcom/mojang/blaze3d/textures/GpuSampler;)V",remap=false)
    private void shaderlod$terrain(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,FogParameters fog,boolean flag,GpuSampler sampler,Operation<Void> original) {
        if(!BslEarlyAlpha.REQUESTED && !com.pp.shaderlod.fabric.dev.earlyalpha.BslEarlyAlphaObserver.REQUESTED && !com.pp.shaderlod.fabric.dev.zeroemission.BslZeroEmissionObserver.REQUESTED) { original.call(matrices,commands,lists,pass,camera,fog,flag,sampler); return; }
        var prior=current;current=pass;

        try { original.call(matrices,commands,lists,pass,camera,fog,flag,sampler); } finally { current=prior; }
    }
}
