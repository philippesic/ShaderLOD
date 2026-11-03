package com.pp.shaderlod.optimization.solasmerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface SolasMergedProgramAccess {
    GlProgram<ChunkShaderInterface> solasOriginalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> solasMergedProgram(TerrainRenderPass pass);
    void solasDeleteMergedPrograms();
}
