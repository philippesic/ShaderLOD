package com.pp.shaderlod.integration;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

/** 26.3 public Renderpearl observation. Iris 1.11.7 pack shaders require OpenGL. */
public final class RendererBackend {
    private RendererBackend() {}
    private static Object recordedDevice;
    public static boolean eligible(String backendName) { return "OpenGL".equals(backendName); }
    public static String observedName() {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        var info = device.getDeviceInfo();
        if (device != recordedDevice) {
            LoggerFactory.getLogger("shaderlod").info("ShaderLOD 26.3 actual graphics backend: {} (device={})", info.backendName(), device.getClass().getName());
            recordedDevice = device;
        }
        return info.backendName();
    }
    public static void requireOpenGl() {
        var name = observedName();
        if (!eligible(name)) throw new IllegalStateException(detail(name));
    }
    /** Refuse before mutating config, pack ownership or reload; never force backend fallback. */
    public static void requireApplySupported() throws Refusal {
        requireApplySupported(RendererBackend::observedName);
    }
    /** Observation-only seam: unavailable context refuses before any Apply mutation. */
    static void requireApplySupported(Supplier<String> observation) throws Refusal {
        final String name;
        try { name = observation.get(); }
        catch (RuntimeException | LinkageError unavailable) {
            throw new Refusal("ShaderLOD 26.3 Apply requires an observed Iris 1.11.7 OpenGL backend; graphics context unavailable. Retry on the render thread after renderer initialization. Previous selection retained.", unavailable);
        }
        if (!eligible(name)) throw new Refusal(detail(name));
    }
    private static String detail(String name) {
        return "ShaderLOD 26.3 Apply requires Iris 1.11.7 OpenGL; observed backend=" + name + ". Previous selection retained.";
    }
    public static final class Refusal extends IOException {
        private Refusal(String message) { super(message); }
        private Refusal(String message, Throwable cause) { super(message, cause); }
    }
}
