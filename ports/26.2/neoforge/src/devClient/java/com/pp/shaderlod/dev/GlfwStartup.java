package com.pp.shaderlod.dev;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
/** One private pre-world native mode/focus request; never a framebuffer or shader-quality resize. */
final class GlfwStartup {
    private GlfwStartup() {}
    static boolean eligible(Minecraft client) {
        var window=client.getWindow();var target=client.gameRenderer.mainRenderTarget();
        try(var stack=MemoryStack.stackPush()) {
            IntBuffer width=stack.mallocInt(1),height=stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(window.handle(),width,height);
            return client.isWindowActive() && window.isFocused() && !window.isIconified() && window.isFullscreen() &&
                GLFW.glfwGetWindowAttrib(window.handle(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE && GLFW.glfwGetWindowMonitor(window.handle())!=0 &&
                width.get(0)==1920 && height.get(0)==1200 && window.getWidth()==1920 && window.getHeight()==1200 &&
                target!=null && target.width==1920 && target.height==1200;
        }
    }
    static void configure(Minecraft client,String run,JsonObject report,Runnable persist) throws Exception {
        com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
        var profile=client.gameDirectory.toPath().toAbsolutePath().normalize();
        if(!profile.endsWith(Path.of("run-workloads","port-262-runtime-r1","neoforge")) || !profile.equals(profile.toRealPath()))
            throw new IllegalStateException("GLFW startup requires exact nonsymlink private262NeoForge profile");
        var window=client.getWindow();var monitor=window.findBestMonitor();
        if(monitor==null)throw new IllegalStateException("No actual GLFW monitor for private startup");
        var state=new JsonObject();report.add("glfwStartupCorrection",state);var modes=new JsonArray();state.add("actualVideoModes",modes);
        state.addProperty("glfwVersion",GLFW.glfwGetVersionString());state.addProperty("framebufferBeforeWidth",window.getWidth());state.addProperty("framebufferBeforeHeight",window.getHeight());
        com.mojang.blaze3d.platform.VideoMode selected=null;
        for(var mode:monitor.videoModes()) {
            var entry=new JsonObject();entry.addProperty("mode",mode.write());entry.addProperty("width",mode.getWidth());entry.addProperty("height",mode.getHeight());entry.addProperty("refreshRate",mode.getRefreshRate());modes.add(entry);
            if(mode.getWidth()==1920 && mode.getHeight()==1200 && mode.getRedBits()>=8 && mode.getGreenBits()>=8 && mode.getBlueBits()>=8 &&
                (selected==null || mode.getRefreshRate()>selected.getRefreshRate()))selected=mode;
        }
        persist.run();if(selected==null)throw new IllegalStateException("Actual monitor exposes no exact1920x1200 fullscreen mode");
        var options=profile.resolve("options.txt");if(!options.equals(options.toRealPath()))throw new IllegalStateException("Private options path aliases another file");
        var backup=profile.resolve("shaderlod-dev/options-before-glfw-startup-"+run+".txt");Files.createDirectories(backup.getParent());Files.copy(options,backup);
        state.addProperty("optionsBeforePath",backup.toString());state.addProperty("selectedVideoMode",selected.write());
        // The actual vanilla preferred-mode path invokes glfwSetWindowMonitor and framebufferSizeChanged.
        client.options.fullscreenVideoModeString=selected.write();window.setPreferredFullscreenVideoMode(Optional.of(selected));window.changeFullscreenVideoMode();client.options.save();
        state.addProperty("previousAutoIconify",GLFW.glfwGetWindowAttrib(window.handle(),GLFW.GLFW_AUTO_ICONIFY));
        GLFW.glfwSetWindowAttrib(window.handle(),GLFW.GLFW_AUTO_ICONIFY,GLFW.GLFW_FALSE);
        GLFW.glfwShowWindow(window.handle());GLFW.glfwFocusWindow(window.handle());
        state.addProperty("modeAndFocusRequestedOnce",true);state.addProperty("actual1920FramebufferAndFocusStillRequired",true);persist.run();
    }
}
