package com.pp.shaderlod.dev.mixin;

import net.irisshaders.iris.gl.program.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Pinned Iris 1.8.12 actual Pass fields; physical GL IDs are checked only after this association. */
@Mixin(targets = "net.irisshaders.iris.pipeline.CompositeRenderer$Pass", remap = false)
public interface SpatialPassAccessor {
    @Accessor("name") String shaderlod$name();
    @Accessor("program") Program shaderlod$program();
    @Accessor("drawBuffers") int[] shaderlod$drawBuffers();
    @Accessor("mipmappedBuffers") com.google.common.collect.ImmutableSet<Integer> shaderlod$mipmappedBuffers();
    @Accessor("stageReadsFromAlt") com.google.common.collect.ImmutableSet<Integer> shaderlod$stageReadsFromAlt();
}
