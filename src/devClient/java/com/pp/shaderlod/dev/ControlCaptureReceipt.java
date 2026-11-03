package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.ShaderLodConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

/** Explicit untimed receipt on an existing named control capture; no actor or automatic exit. */
final class ControlCaptureReceipt {
    private static final String DESTINATION = System.getProperty("shaderlod.dev.controlCaptureReceipt", "");
    private static final com.google.gson.Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private ControlCaptureReceipt() {}

    static void capture(Minecraft client, String name) {
        if (DESTINATION.isBlank()) {
            Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(),
                    message -> com.mojang.logging.LogUtils.getLogger().info("ShaderLOD control capture: {}", message.getString()));
            return;
        }
        var state = new JsonObject();
        Path receipt = Path.of(DESTINATION).toAbsolutePath().resolve(name + ".json");
        boolean receiptOwned = false;
        try {
            if (!name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid capture name");
            if (Files.exists(receipt)) throw new IllegalStateException("Refusing existing named receipt");
            String original = System.getProperty("shaderlod.dev.controlCaptureOriginal", "ComplementaryReimagined_r5.9.3.zip");
            if (!original.matches("[A-Za-z0-9][A-Za-z0-9._+() '-]{0,191}\\.zip"))
                throw new IllegalArgumentException("Original archive must be a single .zip basename");
            Path originalArchive = Iris.getShaderpacksDirectory().resolve(original);
            if (!Files.isRegularFile(originalArchive)) throw new IllegalArgumentException("Original archive is missing: " + original);
            var target = client.getMainRenderTarget();
            var window = client.getWindow();
            String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
            if (client.level == null || client.player == null || client.screen != null || client.isPaused()
                    || !client.isWindowActive() || !window.isFullscreen()
                    || GLFW.glfwGetWindowMonitor(window.getWindow()) == 0
                    || target.width != 1920 || target.height != 1200
                    || target.width != window.getWidth() || target.height != window.getHeight()
                    || !selected.equals(Iris.getCurrentPackName()) || Iris.isFallback()
                    || !IrisApi.getInstance().isShaderPackInUse())
                throw new IllegalStateException("Named capture requires focused native 1920x1200 world and current shader");
            state.addProperty("status", "capture-requested");
            state.addProperty("capturedAtUtc", Instant.now().toString());
            state.addProperty("evidenceKind", "Untimed control screenshot; loaded options are not linked uniforms or executed work");
            state.addProperty("irisSelectedPack", selected);
            state.addProperty("irisLoadedPack", Iris.getCurrentPackName());
            state.addProperty("shaderPackInUse", true);
            state.addProperty("irisFallback", false);
            state.add("loadedIrisOptions", WorkloadInputs.effectiveOptions());
            state.add("pendingIrisOptionQueue", JSON.toJsonTree(new java.util.TreeMap<>(Iris.getShaderPackOptionQueue())));
            var witnessMethods = java.util.Arrays.stream(Iris.class.getDeclaredMethods())
                    .map(java.lang.reflect.Method::getName).filter(method -> method.contains("shaderlod$witnessOwnedOptions"))
                    .sorted().toList();
            state.add("irisOptionWriteWitnessMethodNames", JSON.toJsonTree(witnessMethods));
            state.addProperty("irisOptionWriteWitnessMethodPresent", !witnessMethods.isEmpty());
            boolean mainModPresent = ModList.get().isLoaded("shaderlod");
            state.addProperty("mainModPresent", mainModPresent);
            if (mainModPresent) {
                state.add("coreConfig", JSON.toJsonTree(ShaderLodConfig.snapshot()));
                state.addProperty("makeUpQuadEligibilityAtCapture",
                        com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled());
                state.addProperty("makeUpQuadEligibilityEvidence",
                        "Actual normal backend loaded-input gate at untimed capture; not a count of executed merges");
            }
            else {
                state.add("coreConfig", com.google.gson.JsonNull.INSTANCE);
                state.addProperty("coreConfigStatus", "absent: main mod not loaded in Off task");
            }
            state.addProperty("originalArchive", original);
            state.addProperty("originalArchiveSha256", WorkloadInputs.hash(originalArchive));
            state.addProperty("selectedArchiveSha256", WorkloadInputs.hash(Iris.getShaderpacksDirectory().resolve(selected)));
            state.add("selectedResourcePacks", JSON.toJsonTree(client.getResourcePackRepository().getSelectedIds()));
            state.add("actuallyLoadedResourcePacks", JSON.toJsonTree(client.getResourceManager().listPacks().map(pack -> pack.packId()).toList()));
            var resourceArchives = new JsonObject();
            for (String id : client.getResourcePackRepository().getSelectedIds()) {
                if (!id.startsWith("file/")) continue;
                String filename = id.substring("file/".length());
                if (filename.isBlank() || filename.contains("/") || filename.contains("\\") || !filename.endsWith(".zip")) continue;
                Path archive = client.gameDirectory.toPath().resolve("resourcepacks").resolve(filename);
                if (!Files.isRegularFile(archive)) continue;
                String hash = WorkloadInputs.hash(archive);
                resourceArchives.addProperty(id, hash);
                if (filename.equals("replete-1.2.0+mc1.20-1.21.4.zip")) state.addProperty("repleteArchiveSha256", hash);
            }
            state.add("selectedFileResourceArchivesSha256", resourceArchives);
            state.addProperty("hasSelectedFileResourceArchives", resourceArchives.size() > 0);
            state.addProperty("fullscreen", window.isFullscreen());
            state.addProperty("nativeMonitor", GLFW.glfwGetWindowMonitor(window.getWindow()));
            state.addProperty("focused", client.isWindowActive());
            state.addProperty("framebufferWidth", target.width);
            state.addProperty("framebufferHeight", target.height);
            state.addProperty("windowFramebufferWidth", window.getWidth());
            state.addProperty("windowFramebufferHeight", window.getHeight());
            state.addProperty("dimension", client.level.dimension().location().toString());
            state.addProperty("dayTime", client.level.getDayTime());
            state.addProperty("rainLevel", client.level.getRainLevel(1.0f));
            state.addProperty("thunderLevel", client.level.getThunderLevel(1.0f));
            state.addProperty("cameraX", client.player.getX());
            state.addProperty("cameraY", client.player.getY());
            state.addProperty("cameraZ", client.player.getZ());
            state.addProperty("yaw", client.player.getYRot());
            state.addProperty("pitch", client.player.getXRot());
            state.addProperty("vanillaRenderDistance", client.options.renderDistance().get());
            state.addProperty("dhPresent", ModList.get().isLoaded("distanthorizons"));
            state.addProperty("glVendor", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));
            state.addProperty("glRenderer", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            state.addProperty("glVersion", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
            state.add("glCapabilityFlags", capabilityFlags());
            state.addProperty("irisFrameCounterSource", net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt());
            state.addProperty("irisFrameTimeCounterSource", net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getFrameTimeCounter());
            state.addProperty("irisLastFrameTimeSource", net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.getLastFrameTime());
            state.addProperty("temporalStateEvidence", "Iris CPU uniform sources at capture request; not linked-program values or synchronized histories");
            Path png = client.gameDirectory.toPath().resolve("screenshots").resolve(name + ".png");
            if (Files.exists(png) || Files.exists(receipt)) throw new IllegalStateException("Refusing existing named capture/receipt");
            state.addProperty("screenshot", png.toAbsolutePath().toString());
            Files.createDirectories(receipt.getParent());
            Files.writeString(receipt, JSON.toJson(state), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            receiptOwned = true;
            Screenshot.grab(client.gameDirectory, name + ".png", target, message -> client.execute(() -> {
                try {
                    state.addProperty("screenshotMessage", message.getString());
                    if (!Files.isRegularFile(png)) throw new IllegalStateException("Screenshot not saved");
                    state.addProperty("screenshotSha256", WorkloadInputs.hash(png));
                    state.addProperty("status", "saved-observations");
                    state.addProperty("savedAtUtc", Instant.now().toString());
                    write(receipt, state);
                    com.mojang.logging.LogUtils.getLogger().info("ShaderLOD control capture receipt saved: {}", receipt);
                } catch (Exception failure) { fail(receipt, state, failure, true); }
            }));
        } catch (Exception failure) { fail(receipt, state, failure, receiptOwned); }
    }
    private static JsonObject capabilityFlags() {
        var capabilities = org.lwjgl.opengl.GL.getCapabilities();
        var flags = new JsonObject();
        flags.addProperty("OpenGL41", capabilities.OpenGL41);
        flags.addProperty("OpenGL42", capabilities.OpenGL42);
        flags.addProperty("OpenGL43", capabilities.OpenGL43);
        flags.addProperty("GL_ARB_compute_shader", capabilities.GL_ARB_compute_shader);
        flags.addProperty("GL_ARB_shader_storage_buffer_object", capabilities.GL_ARB_shader_storage_buffer_object);
        flags.addProperty("GL_ARB_shader_image_load_store", capabilities.GL_ARB_shader_image_load_store);
        flags.addProperty("GL_ARB_multi_draw_indirect", capabilities.GL_ARB_multi_draw_indirect);
        flags.addProperty("GL_ARB_draw_indirect", capabilities.GL_ARB_draw_indirect);
        flags.addProperty("GL_NV_shading_rate_image", capabilities.GL_NV_shading_rate_image);
        flags.addProperty("GL_NV_mesh_shader", capabilities.GL_NV_mesh_shader);
        // LWJGL 3.3.3 has no EXT mesh-stage capability field; retain advertised support separately.
        boolean extMeshAdvertised = false;
        int extensionCount = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_NUM_EXTENSIONS);
        for (int index = 0; index < extensionCount; index++) {
            if ("GL_EXT_mesh_shader".equals(org.lwjgl.opengl.GL30.glGetStringi(org.lwjgl.opengl.GL11.GL_EXTENSIONS, index))) {
                extMeshAdvertised = true;
                break;
            }
        }
        flags.addProperty("GL_EXT_mesh_shader_advertised", extMeshAdvertised);
        flags.addProperty("evidence", "Current-context LWJGL capability flags; EXT mesh advertisement alone is not a usable API or execution proof");
        return flags;
    }
    private static void write(Path path, JsonObject state) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(state));
    }
    private static void fail(Path path, JsonObject state, Exception failure, boolean receiptOwned) {
        state.addProperty("status", "failed"); state.addProperty("failure", failure.toString());
        try {
            if (receiptOwned) write(path, state);
            else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, JSON.toJson(state), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
        } catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        com.mojang.logging.LogUtils.getLogger().error("ShaderLOD control receipt failed", failure);
    }
}
