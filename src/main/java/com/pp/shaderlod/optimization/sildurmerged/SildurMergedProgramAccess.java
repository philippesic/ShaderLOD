package com.pp.shaderlod.optimization.sildurmerged;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
/** Original getProgram remains untouched; renderer explicitly acquires derivative. */
public interface SildurMergedProgramAccess {
    GlProgram<ChunkShaderInterface> sildurOriginalProgram(TerrainRenderPass pass);
    GlProgram<ChunkShaderInterface> sildurMergedProgram(TerrainRenderPass pass);
    void sildurDeleteMergedPrograms();
}
