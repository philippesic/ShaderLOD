package com.pp.shaderlod.dev.timing;

import ca.weblite.objc.Client;
import ca.weblite.objc.NSObject;
import ca.weblite.objc.Proxy;
import com.google.gson.JsonObject;
import com.sun.jna.Pointer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.system.MemoryStack;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/** Opt-in startup/endpoint evidence. Every native operation here is a query. */
public final class MacWindowStateEvidence {
    public static final String PROPERTY = "shaderlod.dev.macWindowState";

    private MacWindowStateEvidence() {}

    public static boolean requested() { return Boolean.getBoolean(PROPERTY); }

    /** Call at an existing startup or finite endpoint hook; log the returned JSON. */
    public static JsonObject capture(long handle, String stage) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("stage", stage);
        result.addProperty("enabled", requested());
        if (!requested()) {
            result.addProperty("availability", "disabled");
            return result; // Do not initialize GLFW, JNA or the Objective-C bridge when disabled.
        }
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("javaThread", Thread.currentThread().getName());
        result.addProperty("glfwHandle", Long.toUnsignedString(handle));
        result.addProperty("os", System.getProperty("os.name", "unknown"));
        result.addProperty("note", "Read-only point observations; native activation can complete between observations. No event pumping or focus/activation/policy changes.");
        JsonObject glfw = new JsonObject();
        result.add("glfw", glfw);
        observe(glfw, "focused", () -> windowAttribute(handle, GLFW.GLFW_FOCUSED), MacWindowStateEvidence::glfwBoolean);
        observe(glfw, "visible", () -> windowAttribute(handle, GLFW.GLFW_VISIBLE), MacWindowStateEvidence::glfwBoolean);
        observe(glfw, "iconified", () -> windowAttribute(handle, GLFW.GLFW_ICONIFIED), MacWindowStateEvidence::glfwBoolean);
        observe(glfw, "logicalSize", () -> windowSize(handle, false), value -> value);
        observe(glfw, "framebufferSize", () -> windowSize(handle, true), value -> value);
        JsonObject cocoa = new JsonObject();
        result.add("cocoa", cocoa);
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            cocoa.addProperty("availability", "unsupported_platform");
            result.addProperty("availability", "GLFW_only");
            return result;
        }
        observe(cocoa, "isMainThread", () -> Client.getRawClient().send("NSThread", "isMainThread"), MacWindowStateEvidence::objcBoolean);
        // Follow pinned Minecraft MacosUtil's existing bridge and GLFWNativeCocoa pattern.
        observe(cocoa, "activationPolicy", () -> application().sendRaw("activationPolicy"), MacWindowStateEvidence::activationPolicy);
        observe(cocoa, "isActive", () -> application().sendRaw("isActive"), MacWindowStateEvidence::objcBoolean);
        observe(cocoa, "windowHandle", () -> cocoaHandle(handle), value -> Long.toUnsignedString(integer(value)));
        observe(cocoa, "isKeyWindow", () -> nativeWindow(handle).sendRaw("isKeyWindow"), MacWindowStateEvidence::objcBoolean);
        observe(cocoa, "canBecomeKeyWindow", () -> nativeWindow(handle).sendRaw("canBecomeKeyWindow"), MacWindowStateEvidence::objcBoolean);
        result.addProperty("availability", "queries_recorded");
        return result;
    }

    private static Proxy application() {
        Proxy application = Client.getInstance().sendProxy("NSApplication", "sharedApplication");
        if (application == null || Pointer.nativeValue(application.getPeer()) == 0)
            throw new IllegalStateException("NSApplication sharedApplication returned nil");
        return application;
    }

    private static NSObject nativeWindow(long handle) { return new NSObject(new Pointer(cocoaHandle(handle))); }

    private static long cocoaHandle(long handle) {
        requireHandle(handle);
        long pointer = GLFWNativeCocoa.glfwGetCocoaWindow(handle);
        if (pointer == 0) throw new IllegalStateException("GLFW Cocoa window returned nil");
        return pointer;
    }

    private static int windowAttribute(long handle, int attribute) {
        requireHandle(handle);
        return GLFW.glfwGetWindowAttrib(handle, attribute);
    }

    private static String windowSize(long handle, boolean framebuffer) {
        requireHandle(handle);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            if (framebuffer) GLFW.glfwGetFramebufferSize(handle, width, height);
            else GLFW.glfwGetWindowSize(handle, width, height);
            return width.get(0) + "x" + height.get(0);
        }
    }

    private static void requireHandle(long handle) {
        if (handle == 0) throw new IllegalArgumentException("GLFW window handle unavailable");
    }

    static long integer(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
            return ((Number) value).longValue();
        throw new IllegalArgumentException("Expected integral native value, got " + (value == null ? "null" : value.getClass().getName()));
    }

    static Object objcBoolean(Object value) { return value instanceof Boolean ? value : integer(value) != 0; }

    static Object glfwBoolean(Object value) {
        long raw = integer(value);
        if (raw != GLFW.GLFW_TRUE && raw != GLFW.GLFW_FALSE)
            throw new IllegalArgumentException("Non-boolean GLFW attribute: " + raw);
        return raw == GLFW.GLFW_TRUE;
    }

    static String activationPolicy(Object value) {
        long raw = integer(value);
        if (raw == 0) return "regular";
        if (raw == 1) return "accessory";
        if (raw == 2) return "prohibited";
        return "unknown(" + raw + ")";
    }

    /** Keep each failed query explicit; successful sibling queries are still retained. */
    static void observe(JsonObject target, String field, Supplier<?> query, Function<Object, Object> convert) {
        JsonObject observation = new JsonObject();
        target.add(field, observation);
        try {
            Object raw = query.get();
            if (raw == null) throw new IllegalStateException("Native query returned null");
            observation.addProperty("nativeType", raw.getClass().getName());
            observation.addProperty("raw", raw.toString());
            Object value = convert.apply(raw);
            if (value instanceof Boolean flag) observation.addProperty("value", flag);
            else observation.addProperty("value", value.toString());
            observation.addProperty("availability", "available");
        } catch (RuntimeException | LinkageError failure) {
            observation.addProperty("availability", "unavailable");
            observation.addProperty("errorType", failure.getClass().getName());
            observation.addProperty("errorMessage", failure.getMessage() == null ? "(no message)" : failure.getMessage());
        }
    }
}
