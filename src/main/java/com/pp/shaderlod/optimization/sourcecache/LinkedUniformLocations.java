package com.pp.shaderlod.optimization.sourcecache;

import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;

/** Registered only at the pinned successful Sodium program-link boundary. */
public final class LinkedUniformLocations {
    private LinkedUniformLocations() {}
    private static final LinkedUniformLocationCache<GlProgram<?>> CACHE=
        new LinkedUniformLocationCache<>(GlProgram::handle,
            (int program,String name)->GL20.glGetUniformLocation(program,name));
    public static void register(Object pipeline,GlProgram<?> program) {
        CACHE.register(program.handle(),pipeline,GL.getCapabilities(),program);
    }
    public static int location(int program,String name) {
        return CACHE.location(program,GL.getCapabilities(),name);
    }
    public static void clear() {CACHE.clear();}
    public static void destroy(Object pipeline) {CACHE.destroy(pipeline);}
}
