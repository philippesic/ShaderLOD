package com.pp.shaderlod.fabric.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.pp.shaderlod.fabric.FabricLodConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Explicitly opt-in observation only: one settled world screenshot, no client control. */
public final class FabricSmoke implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("shaderlod-dev");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Object world;
    private long entered;
    private boolean captured;

    @Override public void onInitializeClient() {
        if (FabricOptimizationControl.requested()) FabricOptimizationControl.register();
        if (FabricRoundtrip.requested()) { FabricRoundtrip.register(); return; }
        if (!Boolean.getBoolean("shaderlod.dev.fabricSmoke")) return;
        LOGGER.info("ShaderLOD Fabric dev smoke enabled: one capture after 30 seconds in-world; client remains open");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (captured) return;
        if (client.level == null || client.player == null) { world = null; return; }
        if (world != client.level) {
            world = client.level;
            entered = System.nanoTime();
            return;
        }
        if (System.nanoTime() - entered < 30_000_000_000L || client.screen != null || client.isPaused()) return;
        captured = true;
        try {
            var target = client.getMainRenderTarget();
            var window = client.getWindow();
            String stamp = Long.toString(System.currentTimeMillis());
            String screenshotName = "shaderlod-fabric-smoke-" + stamp + ".png";
            Path evidence = client.gameDirectory.toPath().resolve("shaderlod-dev/fabric-smoke-" + stamp + ".json");
            Path screenshot = client.gameDirectory.toPath().resolve("screenshots").resolve(screenshotName);
            var state = new JsonObject();
            state.addProperty("capturedAtUtc", Instant.now().toString());
            state.addProperty("settledWorldSeconds", (System.nanoTime() - entered) / 1_000_000_000.0);
            state.addProperty("shaderPack", Iris.getCurrentPackName());
            state.addProperty("irisFallback", Iris.isFallback());
            state.addProperty("shaderPackInUse", IrisApi.getInstance().isShaderPackInUse());
            state.add("requestedCoreConfig", GSON.toJsonTree(FabricLodConfig.snapshot()));
            state.addProperty("linkedUniformReadbackPerformed", false);
            state.addProperty("framebufferWidth", target.width);
            state.addProperty("framebufferHeight", target.height);
            state.addProperty("windowFramebufferWidth", window.getWidth());
            state.addProperty("windowFramebufferHeight", window.getHeight());
            state.addProperty("windowScreenWidth", window.getScreenWidth());
            state.addProperty("windowScreenHeight", window.getScreenHeight());
            state.addProperty("fullscreen", window.isFullscreen());
            state.addProperty("focused", client.isWindowActive());
            state.addProperty("dimension", client.level.dimension().location().toString());
            state.addProperty("player", client.player.getGameProfile().getName());
            var server = client.getSingleplayerServer();
            if (server != null) state.addProperty("worldName", server.getWorldData().getLevelName());
            for (String id : new String[]{"fabricloader", "iris", "sodium", "distanthorizons"}) {
                state.addProperty(id + "Version", FabricLoader.getInstance().getModContainer(id)
                        .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent"));
            }
            state.addProperty("screenshot", screenshot.toAbsolutePath().toString());
            state.addProperty("screenshotStatus", "requested");
            write(evidence, state);
            LOGGER.info("ShaderLOD Fabric settled state: {}", GSON.toJson(state));
            Screenshot.grab(client.gameDirectory, screenshotName, target, message -> client.execute(() -> {
                state.addProperty("screenshotStatus", Files.isRegularFile(screenshot) ? "saved" : "failed");
                state.addProperty("screenshotMessage", message.getString());
                write(evidence, state);
                LOGGER.info("ShaderLOD Fabric smoke screenshot: {}; evidence={}", message.getString(), evidence);
            }));
        } catch (RuntimeException failure) {
            LOGGER.error("ShaderLOD Fabric dev smoke capture failed; client remains open", failure);
        }
    }

    private static void write(Path path, JsonObject state) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(state) + "\n");
        } catch (Exception failure) { LOGGER.error("Could not write Fabric smoke evidence {}", path, failure); }
    }
}
