package com.pp.shaderlod.optimization.makeupspatial;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
public interface SpatialProgramAccess{GlProgram<ChunkShaderInterface> shaderlod$spatialProgram();net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface> shaderlod$rateProgram();void shaderlod$deleteSpatialProgram();}
