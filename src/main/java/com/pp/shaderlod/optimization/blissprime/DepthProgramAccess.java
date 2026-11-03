package com.pp.shaderlod.optimization.blissprime;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
public interface DepthProgramAccess {
    GlProgram<ChunkShaderInterface> shaderlod$blissdepthProgram(TerrainRenderPass pass);
    void shaderlod$blissdeleteDepthPrograms();
}
