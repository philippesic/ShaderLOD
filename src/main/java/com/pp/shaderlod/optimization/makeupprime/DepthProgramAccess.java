package com.pp.shaderlod.optimization.makeupprime;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
public interface DepthProgramAccess {
    GlProgram<ChunkShaderInterface> shaderlod$makeupdepthdepthProgram(TerrainRenderPass pass);
    void shaderlod$makeupdepthdeleteDepthPrograms();
}
