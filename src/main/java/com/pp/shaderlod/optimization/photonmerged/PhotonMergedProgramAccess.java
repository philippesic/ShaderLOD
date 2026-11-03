package com.pp.shaderlod.optimization.photonmerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface PhotonMergedProgramAccess {
    GlProgram<ChunkShaderInterface> photonOriginalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> photonMergedProgram(TerrainRenderPass pass);
    void photonDeleteMergedPrograms();
}
