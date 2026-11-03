package com.pp.shaderlod.benchmark.mixin;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public interface BenchmarkSectionQueuesAccessor {
    @Accessor("buildResults") ConcurrentLinkedDeque<?> shaderlod$benchmarkBuildResults();
    @Accessor("taskLists") Map<?, ? extends Collection<?>> shaderlod$benchmarkTaskLists();
}
