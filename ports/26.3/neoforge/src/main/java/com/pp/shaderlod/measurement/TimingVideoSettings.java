package com.pp.shaderlod.measurement;

import net.minecraft.client.Minecraft;

/** Apply both the saved option and the Window state actually consumed by Minecraft. */
public final class TimingVideoSettings {
    private TimingVideoSettings() {}
    public static void apply(int fps, boolean vsync) { apply(fps, vsync, fps); }
    public static void apply(int fps, boolean vsync, int windowFps) {
        var client=Minecraft.getInstance();
        // Modern Minecraft owns the limit through Options and FramerateLimitTracker.
        // Window has no independent limit; retain windowFps for restore-call compatibility.
        if(fps<=0||windowFps<=0)
            throw new IllegalArgumentException("Positive saved and requested FPS limits are required");
        int previousFps=client.options.framerateLimit().get();
        boolean previousVsync=client.options.enableVsync().get();
        try {
            client.options.framerateLimit().set(fps);
            if(client.options.framerateLimit().get()!=fps)
                throw new IllegalArgumentException("The saved FPS option rejected the requested value");
            client.options.enableVsync().set(vsync);
            client.invalidateSurfaceConfiguration();
        } catch(RuntimeException failure) {
            client.options.framerateLimit().set(previousFps);
            client.options.enableVsync().set(previousVsync);
            client.invalidateSurfaceConfiguration();
            throw failure;
        }
    }
    /** Phase-boundary observation only; unavailable driver state is never inferred from Options. */
    public static String macSwapInterval() {
        if(!System.getProperty("os.name", "").startsWith("Mac"))return "not-macOS";
        try {
            long context=org.lwjgl.opengl.CGL.CGLGetCurrentContext();
            if(context==0)return "unavailable: no current CGL context";
            int[] value=new int[1];
            int error=org.lwjgl.opengl.CGL.CGLGetParameter(context,org.lwjgl.opengl.CGL.kCGLCPSwapInterval,value);
            return error==0?Integer.toString(value[0]):"unavailable: CGL error "+error;
        } catch(Exception|LinkageError unavailable) {return "unavailable: "+unavailable.getClass().getSimpleName();}
    }
}
