package com.pp.shaderlod.integration;

import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.mixin.GpuDeviceAccessor;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/** Exact Iris 1.11.4/Minecraft 26.2 backend seam; observation occurs on render thread. */
public final class RendererBackend {
    private RendererBackend() {}
    private static Object recordedBackend;
    public static void requireOpenGl() {
        RenderSystem.assertOnRenderThread();
        var backend = ((GpuDeviceAccessor) RenderSystem.getDevice()).getBackend();
        if (backend != recordedBackend) {
            LoggerFactory.getLogger("shaderlod").info("ShaderLOD 26.2 actual graphics backend: {} ({})", backend.getDeviceInfo().backendName(), backend.getClass().getName());
            recordedBackend = backend;
        }
        requireSupportedIdentity(backend.getDeviceInfo().backendName(), backend.getClass().getName());
    }
    @FunctionalInterface public interface ApplyPreflight { void run() throws Refusal; }
    /** Fail closed before selecting, persisting or reloading; no GL probes are performed here. */
    public static void requireApplySupported() throws Refusal {
        try { requireOpenGl(); }
        catch (RuntimeException | LinkageError unavailable) {
            throw new Refusal("ShaderLOD 26.2 Apply requires a certified Iris OpenGL backend; backend unsupported or context unavailable. Previous selection retained.", unavailable);
        }
    }
    public static void requireApplySupportedIdentity(String name, String implementation) throws Refusal {
        try { requireSupportedIdentity(name, implementation); }
        catch (RuntimeException unsupported) { throw new Refusal(unsupported.getMessage() + ". Previous selection retained.", unsupported); }
    }
    public static final class Refusal extends IOException {
        private Refusal(String message, Throwable cause) { super(message, cause); }
    }
    /** Pure refusal policy; no preference or synthetic fallback can certify the backend. */
    public static void requireSupportedIdentity(String backendName, String className) {
        if (!"OpenGL".equals(backendName) || !"com.mojang.blaze3d.opengl.GlDevice".equals(className)) {
            throw new IllegalStateException("ShaderLOD Iris1.11.4 port requires OpenGL; observed backend=" + backendName + " (" + className + ")");
        }
    }
}
