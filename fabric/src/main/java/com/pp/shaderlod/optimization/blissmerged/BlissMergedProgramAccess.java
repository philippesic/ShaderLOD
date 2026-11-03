package com.pp.shaderlod.optimization.blissmerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface BlissMergedProgramAccess {
    GlProgram<ChunkShaderInterface> blissOriginalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> blissMergedProgram(TerrainRenderPass pass);
    void blissDeleteMergedPrograms();
}
