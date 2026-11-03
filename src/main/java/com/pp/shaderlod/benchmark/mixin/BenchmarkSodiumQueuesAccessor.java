package com.pp.shaderlod.benchmark.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public interface BenchmarkSodiumQueuesAccessor {
    @Accessor("renderSectionManager") RenderSectionManager shaderlod$benchmarkSections();
}
