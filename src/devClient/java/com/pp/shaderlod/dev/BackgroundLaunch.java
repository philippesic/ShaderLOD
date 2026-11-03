package com.pp.shaderlod.dev;

/** Development-only launch preference. Hidden windows are diagnostic, not timing-qualified. */
public final class BackgroundLaunch {
    private static boolean registered, reported;
    private BackgroundLaunch() {}
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("shaderlod.dev.backgroundLaunch",
                Boolean.toString(System.getProperty("os.name", "").startsWith("Mac"))));
    }
    public static void requireForeground(String action) {
        if (enabled()) throw new IllegalStateException(action + " refused by background launch preference");
    }
    /** Quality-only framebuffer captures may run hidden; performance checks do not use this. */
    public static boolean qualityWindowReady(net.minecraft.client.Minecraft client) {
        if (client.isWindowActive()) return true;
        var window = client.getWindow();
        return enabled() && window != null && !window.isFullscreen()
            && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window.getWindow(), org.lwjgl.glfw.GLFW.GLFW_VISIBLE) == 0
            && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window.getWindow(), org.lwjgl.glfw.GLFW.GLFW_ICONIFIED) == 0;
    }
    public static void register() {
        if (!enabled() || registered) return;
        registered = true;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BackgroundLaunch::tick);
    }
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var client = net.minecraft.client.Minecraft.getInstance();
        var window = client.getWindow();
        if (window == null) return;
        client.options.pauseOnLostFocus = false;
        // A never-focused hidden window may not deliver an initial focus callback.
        // Synchronize the game's cached field with the real native state.
        client.setWindowActive(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window.getWindow(), org.lwjgl.glfw.GLFW.GLFW_FOCUSED) != 0);
        var target = client.getMainRenderTarget();
        if (!reported && target != null && window.getWidth() > 0 && window.getHeight() > 0
                && target.width == window.getWidth() && target.height == window.getHeight()) {
            com.mojang.logging.LogUtils.getLogger().info(
                "ShaderLOD background window ready: framebuffer={}x{}, target={}x{}, visible={}, focused={}, iconified={}",
                window.getWidth(), window.getHeight(), target.width, target.height,
                org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window.getWindow(), org.lwjgl.glfw.GLFW.GLFW_VISIBLE),
                org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window.getWindow(), org.lwjgl.glfw.GLFW.GLFW_FOCUSED),
                org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window.getWindow(), org.lwjgl.glfw.GLFW.GLFW_ICONIFIED));
            reported = true;
        }
    }
}
