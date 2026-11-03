package com.pp.shaderlod.optimization.reimaginedmerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface ReimaginedMergedProgramAccess {
    GlProgram<ChunkShaderInterface> reimaginedOriginalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> reimaginedMergedProgram(TerrainRenderPass pass);
    void reimaginedDeleteMergedPrograms();
}
