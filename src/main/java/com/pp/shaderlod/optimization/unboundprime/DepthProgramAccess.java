package com.pp.shaderlod.optimization.unboundprime;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
public interface DepthProgramAccess {
    GlProgram<ChunkShaderInterface> shaderlod$depthProgram(TerrainRenderPass pass);
    void shaderlod$deleteDepthPrograms();
}
