package com.pp.shaderlod.measurement;

import net.minecraft.client.Minecraft;

/** Apply both the saved option and the Window state actually consumed by Minecraft. */
public final class TimingVideoSettings {
    private TimingVideoSettings() {}
    public static void apply(int fps, boolean vsync) { apply(fps, vsync, fps); }
    public static void apply(int fps, boolean vsync, int windowFps) {
        var client=Minecraft.getInstance();
        var accepted=client.options.framerateLimit().values().validateValue(fps);
        if(accepted.isEmpty()||accepted.get()!=fps||windowFps<=0)
            throw new IllegalArgumentException("A valid saved FPS option and positive Window limit are required");
        client.options.framerateLimit().set(fps);
        client.options.enableVsync().set(vsync);
        // OptionInstance skips its callback when the logical value is unchanged.
        client.getWindow().setFramerateLimit(windowFps);
        client.getWindow().updateVsync(vsync);
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
