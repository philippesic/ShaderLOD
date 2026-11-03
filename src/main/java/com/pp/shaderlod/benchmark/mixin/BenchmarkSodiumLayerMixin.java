package com.pp.shaderlod.benchmark.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.benchmark.BenchmarkLiveWork;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class BenchmarkSodiumLayerMixin {
    @WrapMethod(method = "drawChunkLayer", require = 1)
    private void shaderlod$benchmarkLayer(RenderType type, ChunkRenderMatrices matrices,
                                          double x, double y, double z, Operation<Void> original) {
        if (!BenchmarkLiveWork.enabled()) { original.call(type, matrices, x, y, z); return; }
        BenchmarkLiveWork.enterLayer(type == RenderType.translucent());
        try { original.call(type, matrices, x, y, z); }
        finally { BenchmarkLiveWork.leaveLayer(); }
    }
}
