package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

/** Exact SDL3 observation for the private Mac smoke, never a GLFW boolean proxy. */
final class SdlViewport {
    private SdlViewport() {}
    static Facts observe(Minecraft client) {
        var window = client.getWindow();
        var target = client.gameRenderer.mainRenderTarget();
        long handle = window.handle();
        long flags = SDLVideo.SDL_GetWindowFlags(handle);
        int display = SDLVideo.SDL_GetDisplayForWindow(handle);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer lw = stack.mallocInt(1), lh = stack.mallocInt(1);
            IntBuffer pw = stack.mallocInt(1), ph = stack.mallocInt(1);
            boolean logicalOk = SDLVideo.SDL_GetWindowSize(handle, lw, lh);
            boolean pixelOk = SDLVideo.SDL_GetWindowSizeInPixels(handle, pw, ph);
            return new Facts(handle, flags, display,
                    logicalOk ? lw.get(0) : 0, logicalOk ? lh.get(0) : 0,
                    pixelOk ? pw.get(0) : 0, pixelOk ? ph.get(0) : 0,
                    window.getWidth(), window.getHeight(),
                    target == null ? 0 : target.width, target == null ? 0 : target.height,
                    (flags & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0, client.isWindowActive());
        }
    }
    /** One exact-private-profile mode/focus request; observed gates remain unchanged. */
    static void configureStartup(Minecraft client, String loader, String run,
            JsonObject report, Runnable persist) throws Exception {
        com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
        var profile = client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!profile.endsWith(java.nio.file.Path.of("run-workloads", "port-263-runtime-r1", loader))
                || !profile.equals(profile.toRealPath()))
            throw new IllegalStateException("SDL startup correction requires exact nonsymlink private profile");
        var state = new JsonObject(); report.add("sdlStartupCorrection", state);
        state.addProperty("nativeSdlVersion", org.lwjgl.sdl.SDLVersion.SDL_GetVersion());
        state.addProperty("nativeSdlRevision", org.lwjgl.sdl.SDLVersion.SDL_GetRevision());
        state.addProperty("compileLwjglSdlVersion", "3.4.3");
        observe(client).appendTo(state);
        var window = client.getWindow();
        int display = SDLVideo.SDL_GetDisplayForWindow(window.handle());
        if (display == 0) throw new IllegalStateException("SDL window has no actual display");
        var modes = SDLVideo.SDL_GetFullscreenDisplayModes(display);
        if (modes == null) throw new IllegalStateException("SDL actual fullscreen modes unavailable");
        var available = new com.google.gson.JsonArray(); state.add("availableDisplayModes", available);
        com.mojang.blaze3d.platform.VideoMode selected = null;
        try {
            for (int i = 0; i < modes.limit(); i++) {
                var nativeMode = org.lwjgl.sdl.SDL_DisplayMode.create(modes.get(i));
                var mode = new com.mojang.blaze3d.platform.VideoMode(nativeMode);
                var row = new JsonObject(); row.addProperty("width", nativeMode.w()); row.addProperty("height", nativeMode.h());
                row.addProperty("pixelDensity", nativeMode.pixel_density()); row.addProperty("refreshRate", nativeMode.refresh_rate());
                row.addProperty("videoModeJson", mode.write()); available.add(row);
                if (mode.getWidth() == 1920 && mode.getHeight() == 1200
                        && mode.getRedBits() >= 8 && mode.getGreenBits() >= 8 && mode.getBlueBits() >= 8
                        && (selected == null || mode.getRefreshRate() > selected.getRefreshRate())) selected = mode;
            }
        } finally { org.lwjgl.sdl.SDLStdinc.SDL_free(modes); }
        persist.run();
        if (selected == null) throw new IllegalStateException("Actual SDL display exposes no exact 1920x1200 fullscreen mode");
        var options = profile.resolve("options.txt");
        if (!options.equals(options.toRealPath())) throw new IllegalStateException("Private options aliases another file");
        var backup = profile.resolve("shaderlod-dev").resolve("options-before-sdl-startup-" + run + ".txt");
        java.nio.file.Files.createDirectories(backup.getParent());
        java.nio.file.Files.copy(options, backup, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        state.addProperty("optionsBeforePath", backup.toString());
        state.addProperty("optionsBeforeSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(backup))));
        state.addProperty("selectedVideoModeJson", selected.write());
        state.addProperty("exclusiveFullscreenRequested", true);
        persist.run();
        client.options.fullscreenVideoModeString = selected.write();
        client.options.exclusiveFullscreen().set(true);
        window.setPreferredFullscreenVideoMode(java.util.Optional.of(selected));
        window.changeFullscreenVideoMode(); // Exact vanilla dirty-mode API, no arbitrary resize.
        client.options.save();
        state.addProperty("showWindowReturned", SDLVideo.SDL_ShowWindow(window.handle()));
        state.addProperty("raiseWindowReturned", SDLVideo.SDL_RaiseWindow(window.handle()));
        state.addProperty("modeAndFocusRequestedOnce", true);
        state.addProperty("actualFocusRequired", true);
        persist.run();
    }
    record Facts(long handle, long flags, int display, int logicalWidth, int logicalHeight,
            int pixelWidth, int pixelHeight, int windowFramebufferWidth, int windowFramebufferHeight,
            int targetWidth, int targetHeight, boolean windowFullscreen, boolean windowFocused) {
        boolean nativeFullscreen() { return (flags & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0; }
        boolean nativeFocused() { return (flags & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0; }
        boolean eligible() {
            return handle != 0 && display != 0 && nativeFullscreen() && nativeFocused()
                    && windowFullscreen && windowFocused && logicalWidth > 0 && logicalHeight > 0
                    && pixelWidth == 1920 && pixelHeight == 1200
                    && windowFramebufferWidth == pixelWidth && windowFramebufferHeight == pixelHeight
                    && targetWidth == pixelWidth && targetHeight == pixelHeight;
        }
        void appendTo(JsonObject state) {
            state.addProperty("sdlWindowHandle", handle);
            state.addProperty("sdlWindowFlags", flags);
            state.addProperty("sdlDisplayId", display);
            state.addProperty("sdlNativeFullscreen", nativeFullscreen());
            state.addProperty("sdlNativeFocused", nativeFocused());
            state.addProperty("sdlLogicalWidth", logicalWidth);
            state.addProperty("sdlLogicalHeight", logicalHeight);
            state.addProperty("sdlPixelWidth", pixelWidth);
            state.addProperty("sdlPixelHeight", pixelHeight);
            state.addProperty("windowFramebufferWidth", windowFramebufferWidth);
            state.addProperty("windowFramebufferHeight", windowFramebufferHeight);
            state.addProperty("framebufferWidth", targetWidth);
            state.addProperty("framebufferHeight", targetHeight);
            state.addProperty("fullscreen", windowFullscreen);
            state.addProperty("focused", windowFocused);
            state.addProperty("viewportGate", eligible());
        }
    }
}
