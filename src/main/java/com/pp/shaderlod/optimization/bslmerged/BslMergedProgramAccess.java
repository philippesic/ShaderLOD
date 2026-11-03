package com.pp.shaderlod.optimization.bslmerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface BslMergedProgramAccess {
    GlProgram<ChunkShaderInterface> bslOriginalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> bslMergedProgram(TerrainRenderPass pass);
    void bslDeleteMergedPrograms();
}
