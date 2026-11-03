package com.pp.shaderlod.optimization.unboundmerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface UnboundMergedProgramAccess {
    GlProgram<ChunkShaderInterface> unboundOriginalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> unboundMergedProgram(TerrainRenderPass pass);
    void unboundDeleteMergedPrograms();
}
