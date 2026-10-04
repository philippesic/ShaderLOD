package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.CpuTimers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Matched landscape clients; timing harness remains available without ShaderLOD. */
final class ComparisonClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final boolean enabled;
    private boolean opened, configured;
    private int ticks;
    private long controlModified = -1;
    private BenchmarkSession benchmark;
    private WorkloadSession workload;
    private GpuTimers.Snapshot overlayTimings = GpuTimers.snapshot();
    private CpuTimers.Snapshot overlayCpuTimings = CpuTimers.snapshot();
    private long nextOverlayUpdate;
    private int dhChunks = 128;
    private static final String WORLD = "ShaderLOD-Landscape";

    static void register() {
        String mode = System.getProperty("shaderlod.dev.compare", "");
        if (mode.equals("on") || mode.equals("off")) {
            var comparison = new ComparisonClient(mode.equals("on"));
            NeoForge.EVENT_BUS.addListener(comparison::tick);
            NeoForge.EVENT_BUS.addListener(comparison::draw);
        }
    }
    private ComparisonClient(boolean enabled) { this.enabled = enabled; }
    private Path controlFile() {
        return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().getParent().resolve("control.json");
    }
    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        if (!opened && client.screen instanceof TitleScreen) {
            opened = true;
            if (!client.getLevelSource().levelExists(WORLD)) throw new IllegalStateException("Prepare landscape profiles with scripts/setup_comparison.py first");
            client.createWorldOpenFlows().openWorld(WORLD, () -> {});
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        if (!configured) {
            configured = true;
            if (enabled && !ModList.get().isLoaded("shaderlod")) throw new IllegalStateException("LOD client is missing ShaderLOD");
            if (ModList.get().isLoaded("shaderlod")) {
                ShaderLodConfig.ENABLED.set(enabled);
                ShaderLodConfig.PRESET.set(LodPreset.PERFORMANCE);
                ShaderLodConfig.DEBUG.set(false);
            }
            client.options.hideGui = false;
            client.options.guiScale().set(2);
            client.options.framerateLimit().set(260);
            client.options.enableVsync().set(false);
            try {
                String dhConfig = Files.readString(client.gameDirectory.toPath().resolve("config/DistantHorizons.toml"));
                var match = java.util.regex.Pattern.compile("(?m)^\\s*lodChunkRenderDistanceRadius\\s*=\\s*(\\d+)").matcher(dhConfig);
                if (match.find()) dhChunks = Integer.parseInt(match.group(1));
            } catch (Exception exception) { LOGGER.warn("Cannot read DH display radius: {}", exception.toString()); }
            tileWindow(client);
            if (!Files.exists(controlFile())) chooseLandscape();
            if (Boolean.getBoolean("shaderlod.dev.benchmark") && ModList.get().isLoaded("shaderlod")) benchmark = new BenchmarkSession(client.gameDirectory.toPath());
            if (Boolean.getBoolean("shaderlod.dev.measureWorkload")) workload = new WorkloadSession(client.gameDirectory.toPath().toAbsolutePath().normalize());
            LOGGER.info("ShaderLOD landscape ready: side={}, modPresent={}, vanillaChunks={}, DH={}", enabled ? "LOD" : "baseline", ModList.get().isLoaded("shaderlod"), client.options.renderDistance().get(), ModList.get().isLoaded("distanthorizons"));
        }
        ++ticks;
        if (ticks % 10 == 0) applyControl();
        if (workload != null) workload.tick();
        else if (benchmark != null) benchmark.tick();
        else { GpuTimers.drainSamples(); CpuTimers.drainSamples(); }
        if (ticks == 600 || ticks == 1800) {
            Screenshot.grab(client.gameDirectory, "landscape-" + ticks + ".png", client.getMainRenderTarget(), message -> LOGGER.info("ShaderLOD landscape capture: {}", message.getString()));
            LOGGER.info("ShaderLOD landscape timing: FPS={}, {}", client.getFps(), GpuTimers.summary());
            reportDhStats();
        }
    }
    private void chooseLandscape() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            try {
                int selectedX = 512, selectedZ = 512, height = -64;
                for (int x : new int[] {256, 512, 768}) for (int z : new int[] {256, 512, 768}) {
                    server.overworld().getChunk(x >> 4, z >> 4);
                    int candidate = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (candidate > height) { height = candidate; selectedX = x; selectedZ = z; }
                }
                JsonObject control = new JsonObject();
                control.addProperty("x", selectedX + 0.5); control.addProperty("y", height + 16.0); control.addProperty("z", selectedZ + 0.5);
                control.addProperty("yaw", 135.0); control.addProperty("pitch", 9.0); control.addProperty("time", 7000);
                control.addProperty("preset", "PERFORMANCE"); control.addProperty("debug", false);
                Path path = controlFile(), temporary = path.resolveSibling("control.json.tmp");
                Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(control));
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("ShaderLOD selected landscape: {}", control);
            } catch (Exception exception) { LOGGER.error("Could not prepare landscape camera", exception); }
        });
    }
    private void applyControl() {
        try {
            Path path = controlFile();
            if (!Files.exists(path)) return;
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (modified == controlModified) return;
            var control = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            double x = control.get("x").getAsDouble(), y = control.get("y").getAsDouble(), z = control.get("z").getAsDouble();
            float yaw = control.get("yaw").getAsFloat(), pitch = control.get("pitch").getAsFloat();
            int time = control.get("time").getAsInt();
            String weather = control.has("weather") ? control.get("weather").getAsString() : "clear";
            if (!List.of("clear", "rain", "thunder").contains(weather)) throw new IllegalArgumentException("Invalid weather");
            if (!Double.isFinite(x + y + z) || !Float.isFinite(yaw + pitch)) throw new IllegalArgumentException("Invalid camera");
            if (ModList.get().isLoaded("shaderlod")) {
                if (control.has("preset")) ShaderLodConfig.PRESET.set(LodPreset.valueOf(control.get("preset").getAsString()));
                if (control.has("debug")) ShaderLodConfig.DEBUG.set(control.get("debug").getAsBoolean());
                if (control.has("terrainEnabled")) ShaderLodConfig.TERRAIN_ENABLED.set(control.get("terrainEnabled").getAsBoolean());
                if (control.has("terrainChunkCap")) ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.set(control.get("terrainChunkCap").getAsInt());
                if (control.has("warmSourceConfirmed")) ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(control.get("warmSourceConfirmed").getAsBoolean());
                if (control.has("enabled") && benchmark == null) ShaderLodConfig.ENABLED.set(control.get("enabled").getAsBoolean());
            }
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                var source = server.createCommandSourceStack();
                for (String command : List.of("time set " + time, "weather " + weather, "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamemode spectator @a", String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.3f %.3f", x, y, z, yaw, pitch))) server.getCommands().performPrefixedCommand(source, command);
            });
            controlModified = modified;
            GpuTimers.resetSamples();
            LOGGER.info("ShaderLOD landscape camera applied: {}", control);
        } catch (Exception exception) { LOGGER.warn("Landscape control rejected: {}", exception.toString()); }
    }
    private void tileWindow(Minecraft client) {
        var window = client.getWindow();
        window.setTitle(enabled ? "ShaderLOD landscape — LOD ON (left)" : "Photon landscape — BASELINE (right)");
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            GLFW.glfwGetMonitorWorkarea(GLFW.glfwGetPrimaryMonitor(), x, y, w, h);
            int width = (w.get(0) - 24) / 2, height = Math.min(h.get(0) - 80, width * 9 / 16);
            window.setWindowed(width, height);
            GLFW.glfwSetWindowSize(window.getWindow(), width, height);
            GLFW.glfwSetWindowPos(window.getWindow(), x.get(0) + 8 + (enabled ? 0 : width + 8), y.get(0) + (h.get(0) - height) / 2);
        }
    }
    private void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        boolean mod = ModList.get().isLoaded("shaderlod");
        var lines = new ArrayList<String>();
        lines.add((mod ? "LOD " + (ShaderLodConfig.snapshot().enabled() ? "ON " : "OFF ") + ShaderLodConfig.PRESET.get() : "PHOTON BASELINE (no ShaderLOD)") + " | " + client.getFps() + " FPS");
        long now = System.nanoTime();
        if (now >= nextOverlayUpdate) {
            overlayTimings = GpuTimers.snapshot();
            overlayCpuTimings = CpuTimers.snapshot();
            nextOverlayUpdate = now + 250_000_000L;
        }
        var timings = overlayTimings;
        var frameStats = timings.frame();
        lines.add(frameStats != null && frameStats.samples() > 0
                ? String.format(Locale.ROOT, "GPU render %.2f ms | median %.2f | p95 %.2f", frameStats.meanMs(), frameStats.medianMs(), frameStats.p95Ms())
                : "GPU: " + timings.status());
        for (String scope : List.of("iris/shadow-map", "iris/deferred1", "iris/deferred3", "iris/deferred4", "iris/composite")) {
            var stats = timings.scopes().get(scope);
            if (stats != null && stats.samples() > 0) {
                String label = switch (scope) {
                    case "iris/deferred1" -> "Clouds";
                    case "iris/deferred3" -> "AO";
                    case "iris/deferred4" -> "Opaque lighting";
                    case "iris/composite" -> "Fog";
                    default -> "Shadow map";
                };
                lines.add(String.format(Locale.ROOT, "%s %.2f ms", label, stats.meanMs()));
            }
        }
        lines.add(client.getWindow().getWidth() + " x " + client.getWindow().getHeight() + " | MC " + client.options.renderDistance().get() + "/" + client.options.getEffectiveRenderDistance() + "ch | DH " + dhChunks + "ch");
        lines.add(timings.status() + " | shared GPU preview");
        if (benchmark != null) lines.add(benchmark.status());
        if (workload != null) lines.add(workload.status());
        var cpuFrame = overlayCpuTimings.scopes().get("run-tick");
        if (cpuFrame != null && cpuFrame.samples() > 0) lines.add(String.format(Locale.ROOT, "CPU frame wall %.2f | thread %.2f ms", cpuFrame.meanWallMs(), cpuFrame.meanThreadMs() == null ? Double.NaN : cpuFrame.meanThreadMs()));
        var graphics = event.getGuiGraphics();
        int width = lines.stream().mapToInt(client.font::width).max().orElse(100);
        graphics.fill(5, 5, width + 13, 11 + lines.size() * 12, 0xC0000000);
        for (int i = 0; i < lines.size(); ++i) graphics.drawString(client.font, lines.get(i), 9, 8 + 12 * i, i == 0 ? (mod ? 0xFF80FF80 : 0xFFFFCF80) : 0xFFFFFFFF);
    }
    private void reportDhStats() {
        if (!ModList.get().isLoaded("distanthorizons")) return;
        try {
            var api = Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi");
            var world = api.getMethod("tryGetDhClientWorld").invoke(null);
            var state = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi").getField("RENDER_STATE").get(null);
            var wrapper = state.getClass().getField("clientLevelWrapper").get(state);
            var type = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper");
            var level = world.getClass().getMethod("getClientLevel", type).invoke(world, wrapper);
            var buffers = level.getClass().getMethod("getRenderBufferHandler").invoke(level);
            LOGGER.info("ShaderLOD landscape DH: {}", buffers.getClass().getMethod("getVboRenderDebugMenuString").invoke(buffers));
        } catch (Exception exception) { LOGGER.warn("DH stats unavailable: {}", exception.toString()); }
    }
}
