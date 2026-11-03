package com.pp.shaderlod.optimization.bslprime;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
public interface DepthProgramAccess {
    GlProgram<ChunkShaderInterface> shaderlod$bsldepthdepthProgram(TerrainRenderPass pass);
    void shaderlod$bsldepthdeleteDepthPrograms();
}
