package com.pp.shaderlod.optimization.astramerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface AstraMergedProgramAccess {
    GlProgram<ChunkShaderInterface> astraOriginalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> astraMergedProgram(TerrainRenderPass pass);
    void astraDeleteMergedPrograms();
}
