package com.pp.shaderlod.fabric.dev;

import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.fabric.dev.mixin.FabricMinecraftLimiterInvoker;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import org.lwjgl.glfw.GLFW;

/** Frame-wide native-only observations at the same completed GameRenderer branch. */
public final class FabricNativeCostFrames {
    public static final FabricNativeCostWindow WINDOW = new FabricNativeCostWindow();
    private FabricNativeCostFrames() {}
    public static void observe(Minecraft client) {
        try {
            var w = client.getWindow(); var target = client.getMainRenderTarget(); long handle = w.getWindow();
            WINDOW.observe(client.options.framerateLimit().get(), w.getFramerateLimit(),
                    ((FabricMinecraftLimiterInvoker) client).shaderlod$effectiveFramerateLimit(),
                    client.isWindowActive(), GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE,
                    GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE,
                    w.isFullscreen() && GLFW.glfwGetWindowMonitor(handle) != 0 && w.getWidth() == 1920
                            && w.getHeight() == 1200 && target.width == 1920 && target.height == 1200,
                    client.level != null && client.player != null && client.screen == null && !client.isPaused()
                            && IrisApi.getInstance().isShaderPackInUse() && !Iris.isFallback(),
                    client.options.enableVsync().get(), !FabricLodConfig.snapshot().enabled(), !MakeUpQuadRuntime.enabled());
        } catch (RuntimeException unavailable) {
            // Preserve the completed frame and invalidate the whole window; do not drop its denominator.
            WINDOW.observe(-1, -1, -1, false, false, true, false, false, true, false, false);
        }
    }
}
