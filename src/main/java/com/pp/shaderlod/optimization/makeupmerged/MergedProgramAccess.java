package com.pp.shaderlod.optimization.makeupmerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface MergedProgramAccess {
    GlProgram<ChunkShaderInterface> originalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> mergedProgram(TerrainRenderPass pass);
    void deleteMergedPrograms();
}
