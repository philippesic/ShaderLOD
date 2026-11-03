package com.pp.shaderlod.integration;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

/** Public Minecraft26.1.2 observation. No GL probe or forced renderer fallback. */
public final class RendererBackend {
    private RendererBackend() {}
    private static Object recordedDevice;
    public static String observedName() {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        String name = device.getBackendName();
        if (device != recordedDevice) {
            LoggerFactory.getLogger("shaderlod").info("ShaderLOD 26.1.2 actual graphics backend: {} (device={})", name, device.getClass().getName());
            recordedDevice = device;
        }
        return name;
    }
    public static void requireOpenGl() {
        try { requireApplySupported(); }
        catch (Refusal refusal) { throw new IllegalStateException(refusal.getMessage(), refusal); }
    }
    public static void requireApplySupported() throws Refusal {
        requireApplySupported(RendererBackend::observedName);
    }
    /** Same observation/refusal policy used by Apply; unavailable context fails closed. */
    public static void requireApplySupported(Supplier<String> observation) throws Refusal {
        final String name;
        try { name = observation.get(); }
        catch (RuntimeException | LinkageError unavailable) {
            throw new Refusal("ShaderLOD 26.1.2 Apply requires an observed OpenGL backend; graphics context unavailable. Previous selection retained.", unavailable);
        }
        if (!"OpenGL".equals(name)) {
            throw new Refusal("ShaderLOD 26.1.2 Apply requires Iris1.11.4 OpenGL; observed backend=" + name + ". Previous selection retained.", null);
        }
    }
    public static final class Refusal extends IOException {
        private Refusal(String message, Throwable cause) { super(message, cause); }
    }
}
