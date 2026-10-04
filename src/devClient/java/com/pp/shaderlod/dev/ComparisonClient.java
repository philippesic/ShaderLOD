package com.pp.shaderlod.dev;

import com.pp.shaderlod.ShaderLod;
import com.pp.shaderlod.ShaderLodConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/** Matched local A/B clients, compiled only into the development source set. */
final class ComparisonClient {
    private final boolean enabled;
    private boolean opened;
    private boolean configured;
    private int ticks;
    private static final String WORLD = "ShaderLOD-Smoke";

    static void register() {
        String mode = System.getProperty("shaderlod.dev.compare", "");
        if (mode.equals("on") || mode.equals("off")) {
            var comparison = new ComparisonClient(mode.equals("on"));
            NeoForge.EVENT_BUS.addListener(comparison::tick);
            NeoForge.EVENT_BUS.addListener(comparison::draw);
        }
    }

    private ComparisonClient(boolean enabled) { this.enabled = enabled; }

    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        if (!opened && client.screen instanceof TitleScreen) {
            opened = true;
            if (!client.getLevelSource().levelExists(WORLD)) {
                throw new IllegalStateException("Prepare comparison worlds with scripts/setup_comparison.py first");
            }
            client.createWorldOpenFlows().openWorld(WORLD, () -> {});
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        if (!configured) {
            configured = true;
            ShaderLodConfig.ENABLED.set(enabled);
            ShaderLodConfig.DEBUG.set(false);
            client.options.hideGui = false;
            client.options.guiScale().set(2);
            client.options.framerateLimit().set(260);
            client.options.enableVsync().set(false);
            tileWindow(client);
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                var commands = server.getCommands();
                var source = server.createCommandSourceStack();
                for (String command : new String[] {"time set 5000", "weather clear",
                        "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                        "gamerule doMobSpawning false", "gamemode creative @a",
                        "tp @a 0 132 0 0 3"}) {
                    commands.performPrefixedCommand(source, command);
                }
            });
            ShaderLod.LOGGER.info("ShaderLOD comparison ready: LOD {}, matching camera/options, DH absent", enabled ? "ON" : "OFF");
        }
        ++ticks;
        if (ticks == 300 || ticks == 600) {
            Screenshot.grab(client.gameDirectory, "comparison-" + ticks + ".png", client.getMainRenderTarget(),
                    message -> ShaderLod.LOGGER.info("ShaderLOD comparison capture: {}", message.getString()));
            ShaderLod.LOGGER.info("ShaderLOD comparison FPS: {} (shared GPU preview)", client.getFps());
        }
    }

    private void tileWindow(Minecraft client) {
        var window = client.getWindow();
        window.setTitle("ShaderLOD comparison — LOD " + (enabled ? "ON (left)" : "OFF (right)"));
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1);
            var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            GLFW.glfwGetMonitorWorkarea(GLFW.glfwGetPrimaryMonitor(), x, y, w, h);
            int width = (w.get(0) - 24) / 2;
            int height = Math.min(h.get(0) - 80, width * 9 / 16);
            window.setWindowed(width, height);
            GLFW.glfwSetWindowSize(window.getWindow(), width, height);
            GLFW.glfwSetWindowPos(window.getWindow(), x.get(0) + 8 + (enabled ? 0 : width + 8),
                    y.get(0) + (h.get(0) - height) / 2);
        }
    }

    private void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        var graphics = event.getGuiGraphics();
        String label = "LOD " + (ShaderLodConfig.snapshot().enabled() ? "ON" : "OFF") + " | " + client.getFps() + " FPS";
        String details = client.getWindow().getWidth() + " x " + client.getWindow().getHeight() + " | Photon | shared GPU";
        graphics.fill(5, 5, Math.max(client.font.width(label), client.font.width(details)) + 13, 31, 0xC0000000);
        graphics.drawString(client.font, label, 9, 8, enabled ? 0xFF80FF80 : 0xFFFFCF80);
        graphics.drawString(client.font, details, 9, 20, 0xFFFFFFFF);
    }
}
