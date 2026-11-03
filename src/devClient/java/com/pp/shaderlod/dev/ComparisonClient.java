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
import net.irisshaders.iris.Iris;
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
    private boolean nativeFullscreenReported;
    private final boolean cameraQualityRequested = !System.getProperty("shaderlod.dev.cameraQuality", "").isBlank();
    private CameraQualitySession cameraQuality;
    private final boolean historyCaptureRequested = PixelHistorySession.requested();
    private PixelHistorySession historyCapture;
    private int ticks;
    private int captureTick = -1;
    private String captureName;
    private String appliedFixture;
    private boolean cancelSettingsProbeConsumed;
    private boolean failedApplyProbeConsumed;
    private long controlModified = -1;
    private BenchmarkSession benchmark;
    private WorkloadSession workload;
    private PhysicalResolutionSession physical;
    private GpuTimers.Snapshot overlayTimings = GpuTimers.snapshot();
    private CpuTimers.Snapshot overlayCpuTimings = CpuTimers.snapshot();
    private long nextOverlayUpdate;
    private int dhChunks = 128;
    private static final String WORLD = "ShaderLOD-Landscape";

    static void register() {
        if (BenchmarkControlProbe.register()) return;
        // A disabled main-mod probe must not be verified in the dev-only baseline.
        if(!System.getProperty("shaderlod.dev.benchmarkTunerProbe", "").isBlank()
                && BenchmarkTunerProbe.register())return;
        String mode = System.getProperty("shaderlod.dev.compare", "");
        if (mode.equals("on") || mode.equals("off")) {
            var comparison = new ComparisonClient(mode.equals("on"));
            NeoForge.EVENT_BUS.addListener(comparison::tick);
            NeoForge.EVENT_BUS.addListener(comparison::draw);
        }
    }
    private ComparisonClient(boolean enabled) {
        this.enabled = enabled;
        PixelHistorySession.requireProperties();
        if (PhysicalResolutionSession.requested()) PhysicalResolutionSession.requireProperties();
        if (historyCaptureRequested && cameraQualityRequested) throw new IllegalArgumentException("History/camera capture sessions are mutually exclusive");
    }
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
            if (!cameraQualityRequested && !historyCaptureRequested && !PhysicalResolutionSession.requested() && ModList.get().isLoaded("shaderlod") && !Boolean.getBoolean("shaderlod.dev.measureWorkload")
                    && !Boolean.getBoolean("shaderlod.dev.benchmark")) {
                ShaderLodConfig.ENABLED.set(enabled);
                ShaderLodConfig.PRESET.set(LodPreset.PERFORMANCE);
                ShaderLodConfig.DEBUG.set(Boolean.getBoolean("shaderlod.dev.comparisonDebug"));
            }
            client.options.hideGui = cameraQualityRequested || historyCaptureRequested;
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
            if (PhysicalResolutionSession.requested()) {
                physical = new PhysicalResolutionSession(client.gameDirectory.toPath(),
                        new PhysicalResolutionSession.EvidenceSource() {
                            public void armBoundary(String label) { com.pp.shaderlod.dev.timing.PhysicalResolutionTracker.armBoundary(label); }
                            public JsonObject snapshot() { return new GsonBuilder().create().toJsonTree(com.pp.shaderlod.dev.timing.PhysicalResolutionTracker.snapshot()).getAsJsonObject(); }
                            public PhysicalResolutionSession.ResourceStamp resourceStamp() {
                                var s = com.pp.shaderlod.dev.timing.PhysicalResolutionTracker.signature();
                                if (s == null || !s.enabled()) throw new IllegalStateException("Physical resource hook disabled");
                                return new PhysicalResolutionSession.ResourceStamp(s.allocationEpoch(), s.mainColorVersion(), s.mainDepthVersion(),
                                        s.colorStorageEpoch(), s.depthStorageEpoch(), s.pipelineEpoch(), s.completedBoundaryEpoch(),
                                        s.completedAllocationEpoch(), s.irisWidth(), s.irisHeight(), s.pipelineIdentity());
                            }
                        });
            } else if (cameraQualityRequested) {
                cameraQuality = CameraQualitySession.createIfRequested(client.gameDirectory.toPath());
                if (cameraQuality == null) {
                    LOGGER.error("Camera quality request rejected; no benchmark started");
                    if (Boolean.getBoolean("shaderlod.dev.cameraQualityOnly")) client.stop();
                    return;
                }
            } else {
                if (Boolean.getBoolean("shaderlod.dev.benchmark") && ModList.get().isLoaded("shaderlod")) benchmark = new BenchmarkSession(client.gameDirectory.toPath());
                if (Boolean.getBoolean("shaderlod.dev.measureWorkload")) workload = new WorkloadSession(client.gameDirectory.toPath().toAbsolutePath().normalize());
            }
            LOGGER.info("ShaderLOD landscape ready: side={}, modPresent={}, vanillaChunks={}, DH={}", enabled ? "LOD" : "baseline", ModList.get().isLoaded("shaderlod"), client.options.renderDistance().get(), ModList.get().isLoaded("distanthorizons"));
        }
        ++ticks;
        if (ticks % 10 == 0) applyControl();
        // Startup admission for named captures ends before the unchanged cost endpoints.
        if (ticks < 600 && ticks % 20 == 0 &&
                !System.getProperty("shaderlod.dev.controlCaptureReceipt", "").isBlank()) {
            var readinessWindow = client.getWindow();
            var readinessTarget = client.getMainRenderTarget();
            LOGGER.info("ShaderLOD capture readiness: tick={}, focused={}, paused={}, screen={}, fullscreen={}, framebuffer={}x{}, target={}x{}, monitor={}",
                    ticks, client.isWindowActive(), client.isPaused(),
                    client.screen == null ? "none" : client.screen.getClass().getName(),
                    readinessWindow.isFullscreen(), readinessWindow.getWidth(), readinessWindow.getHeight(),
                    readinessTarget.width, readinessTarget.height,
                    org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(readinessWindow.getWindow()));
        }
        if (historyCaptureRequested) {
            if (historyCapture == null) historyCapture = PixelHistorySession.createIfRequested(client);
            if (historyCapture != null) historyCapture.tick(client);
            GpuTimers.drainSamples(); CpuTimers.drainSamples();
            return;
        }
        if (cameraQualityRequested) {
            if (cameraQuality != null) cameraQuality.tick(client);
            GpuTimers.drainSamples(); CpuTimers.drainSamples();
            return;
        }
        if (ticks == captureTick) {
            ControlCaptureReceipt.capture(client, captureName);
            captureTick = -1;
        }
        if (physical != null) physical.tick();
        else if (workload != null) workload.tick();
        else if (benchmark != null) benchmark.tick();
        else { GpuTimers.drainSamples(); CpuTimers.drainSamples(); }
        if (physical == null && benchmark == null && workload == null && (ticks == 600 || ticks == 1800)) {
            var timingWindow = client.getWindow();
            var timingTarget = client.getMainRenderTarget();
            int timingFps = client.getFps();
            boolean timingFocused = client.isWindowActive(), timingPaused = client.isPaused();
            String timingScreen = client.screen == null ? "none" : client.screen.getClass().getName();
            boolean timingFullscreen = timingWindow.isFullscreen();
            int timingWidth = timingWindow.getWidth(), timingHeight = timingWindow.getHeight();
            int timingTargetWidth = timingTarget.width, timingTargetHeight = timingTarget.height;
            long timingMonitor = org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(timingWindow.getWindow());
            if (com.pp.shaderlod.dev.timing.MacWindowStateEvidence.requested()) {
                var macWindowState = com.pp.shaderlod.dev.timing.MacWindowStateEvidence.capture(
                        timingWindow.getWindow(), "landscape_endpoint_" + ticks);
                macWindowState.addProperty("comparisonTick", ticks);
                macWindowState.addProperty("minecraftWindowActive", timingFocused);
                macWindowState.addProperty("minecraftPaused", timingPaused);
                macWindowState.addProperty("minecraftScreen", timingScreen);
                macWindowState.addProperty("minecraftFullscreen", timingFullscreen);
                macWindowState.addProperty("nativeMonitor", Long.toUnsignedString(timingMonitor));
                LOGGER.info("ShaderLOD mac window state: {}", macWindowState);
            }
            Screenshot.grab(client.gameDirectory, "landscape-" + ticks + ".png", client.getMainRenderTarget(), message -> LOGGER.info("ShaderLOD landscape capture: {}", message.getString()));
            LOGGER.info("ShaderLOD landscape timing: FPS={}, {}, focused={}, paused={}, screen={}, fullscreen={}, framebuffer={}x{}, target={}x{}, monitor={}",
                    timingFps, GpuTimers.summary(), timingFocused, timingPaused, timingScreen, timingFullscreen,
                    timingWidth, timingHeight, timingTargetWidth, timingTargetHeight, timingMonitor);
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
            if (control.has("optimizationMeasurement")) {
                controlModified = modified; // One-shot including rejected schema/guard/API requests.
                OptimizationMeasurementControl.consume(control, path, this::requireUntimedSettingsInspection);
                return; // Preserve all existing camera/time/weather/preset handlers and paired pins.
            }
            if (control.has("cancelSettingsProbe") && control.get("cancelSettingsProbe").getAsBoolean()) {
                controlModified = modified; // Explicit one-shot probe, including rejection.
                if (cancelSettingsProbeConsumed) throw new IllegalStateException("Cancel settings probe already consumed");
                cancelSettingsProbeConsumed = true;
                requireUntimedSettingsInspection();
                if (control.has("queueShaderOptions") || control.has("applyPackSettings")
                        && control.get("applyPackSettings").getAsBoolean()
                        || control.has("failedApplyProbe") && control.get("failedApplyProbe").getAsBoolean())
                    throw new IllegalArgumentException("Cancel settings probe cannot queue or Apply settings");
                String packId = requireNativeSettingsProbeSource();
                SettingsCancelProbe.run(Minecraft.getInstance(), packId);
                return; // Never apply control config/camera/weather changes for this probe.
            }
            if (control.has("failedApplyProbe") && control.get("failedApplyProbe").getAsBoolean()) {
                controlModified = modified;
                if (failedApplyProbeConsumed) throw new IllegalStateException("Failed Apply probe already consumed");
                failedApplyProbeConsumed = true;
                requireUntimedSettingsInspection();
                if (control.has("queueShaderOptions") || control.has("applyPackSettings")
                        && control.get("applyPackSettings").getAsBoolean()
                        || control.has("cancelSettingsProbe") && control.get("cancelSettingsProbe").getAsBoolean())
                    throw new IllegalArgumentException("Failed Apply probe cannot combine other settings probes");
                if (!requireNativeSettingsProbeSource().equals("makeup-ultra-fast-shaders"))
                    throw new IllegalArgumentException("Failed Apply probe requires exact original MakeUp 9.5g");
                SettingsFailedApplyProbe.run(Minecraft.getInstance());
                return;
            }
            double x = control.get("x").getAsDouble(), y = control.get("y").getAsDouble(), z = control.get("z").getAsDouble();
            float yaw = control.get("yaw").getAsFloat(), pitch = control.get("pitch").getAsFloat();
            int time = control.get("time").getAsInt();
            String weather = control.has("weather") ? control.get("weather").getAsString() : "clear";
            String fixture = control.has("fixture") ? control.get("fixture").getAsString() : "";
            if (!fixture.isEmpty() && !fixture.equals("pbr_wall")) throw new IllegalArgumentException("Unknown manual fixture");
            if (!fixture.isEmpty() && (benchmark != null || workload != null || physical != null || cameraQualityRequested || historyCaptureRequested))
                throw new IllegalArgumentException("Manual fixture cannot modify a captured workload");
            if (!List.of("clear", "rain", "thunder").contains(weather)) throw new IllegalArgumentException("Invalid weather");
            if (!Double.isFinite(x + y + z) || !Float.isFinite(yaw + pitch)) throw new IllegalArgumentException("Invalid camera");
            var queuedOptions = new java.util.LinkedHashMap<String, String>();
            if (control.has("queueShaderOptions")) {
                controlModified = modified;
                if (!ModList.get().isLoaded("shaderlod") || !control.has("applyPackSettings")
                        || !control.get("applyPackSettings").getAsBoolean())
                    throw new IllegalArgumentException("Native queue probe requires explicit settings Apply");
                requireUntimedSettingsInspection();
                if (!ShaderLodConfig.ENABLED.get() || ShaderLodConfig.PRESET.get() != LodPreset.QUALITY
                        || !control.has("enabled") || !control.get("enabled").getAsBoolean()
                        || !control.has("preset") || !control.get("preset").getAsString().equals("QUALITY"))
                    throw new IllegalArgumentException("Native queue probe requires current and requested enabled Quality");
                requireNativeSettingsProbeSource();
                if (!Iris.getShaderPackOptionQueue().isEmpty()) throw new IllegalStateException("Refusing preexisting native option queue");
                if (!control.get("queueShaderOptions").isJsonObject()) throw new IllegalArgumentException("Native queue probe requires an option object");
                var request = control.getAsJsonObject("queueShaderOptions");
                if (request.size() < 1 || request.size() > 4) throw new IllegalArgumentException("Native queue probe requires one to four options");
                var declared = Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionSet();
                for (var entry : request.entrySet()) {
                    if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                        throw new IllegalArgumentException("Native queue values must be strings");
                    String key = entry.getKey(), value = entry.getValue().getAsString();
                    boolean legal = declared.getBooleanOptions().containsKey(key) && List.of("true", "false").contains(value);
                    var stringOption = declared.getStringOptions().get(key);
                    legal |= stringOption != null && stringOption.getOption().getAllowedValues().contains(value);
                    if (!legal) throw new IllegalArgumentException("Undeclared or illegal native option: " + key);
                    queuedOptions.put(key, value);
                }
            }
            if (control.has("capture")) {
                String name = control.get("capture").getAsString();
                if (!name.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid capture name");
                captureName = name;
                captureTick = ticks + 40;
            }
            if (ModList.get().isLoaded("shaderlod")) {
                boolean explicitSettings = control.has("applyPackSettings") && control.get("applyPackSettings").getAsBoolean();
                if (explicitSettings) {
                    // An explicit action is consumed once even when a reload is rejected.
                    controlModified = modified;
                    if (benchmark != null || workload != null || physical != null || cameraQualityRequested || historyCaptureRequested)
                        throw new IllegalArgumentException("Explicit settings actions cannot run during capture sessions");
                    boolean requestedEnabled = control.has("enabled") ? control.get("enabled").getAsBoolean() : ShaderLodConfig.ENABLED.get();
                    var requestedPreset = control.has("preset") ? LodPreset.valueOf(control.get("preset").getAsString()) : ShaderLodConfig.PRESET.get();
                    if (!queuedOptions.isEmpty()) {
                        LOGGER.info("ShaderLOD native queue probe before: queue={}, loadedOptions={}, requested={}",
                                new java.util.TreeMap<>(Iris.getShaderPackOptionQueue()), WorkloadInputs.effectiveOptions(), queuedOptions);
                        Iris.getShaderPackOptionQueue().putAll(queuedOptions);
                    }
                    boolean applied = com.pp.shaderlod.ShaderLodSettings.apply(requestedEnabled, requestedPreset);
                    if (!queuedOptions.isEmpty()) LOGGER.info("ShaderLOD native queue probe after: applied={}, queue={}, loadedOptions={}",
                            applied, new java.util.TreeMap<>(Iris.getShaderPackOptionQueue()), WorkloadInputs.effectiveOptions());
                    if (!applied) {
                        LOGGER.error("ShaderLOD explicit settings failed: {}", com.pp.shaderlod.ShaderLodSettings.failureMessage());
                        throw new IllegalStateException("Explicit settings apply failed");
                    }
                    SettledConsumerUniforms.afterApply();
                    LOGGER.info("ShaderLOD explicit settings applied: pack={}, enabled={}, preset={}",
                            net.irisshaders.iris.Iris.getCurrentPackName(), requestedEnabled, requestedPreset);
                } else if (control.has("preset")) ShaderLodConfig.PRESET.set(LodPreset.valueOf(control.get("preset").getAsString()));
                if (control.has("debug")) ShaderLodConfig.DEBUG.set(control.get("debug").getAsBoolean());
                if (control.has("terrainEnabled")) ShaderLodConfig.TERRAIN_ENABLED.set(control.get("terrainEnabled").getAsBoolean());
                if (control.has("terrainChunkCap")) ShaderLodConfig.TERRAIN_VANILLA_CHUNK_CAP.set(control.get("terrainChunkCap").getAsInt());
                if (control.has("warmSourceConfirmed")) ShaderLodConfig.TERRAIN_WARM_SOURCE_CONFIRMED.set(control.get("warmSourceConfirmed").getAsBoolean());
                if (!explicitSettings && control.has("enabled") && benchmark == null) ShaderLodConfig.ENABLED.set(control.get("enabled").getAsBoolean());
            }
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                var source = server.createCommandSourceStack();
                if (fixture.equals("pbr_wall") && !fixture.equals(appliedFixture)) {
                    for (String command : List.of(
                            "fill 489 63 650 496 63 671 minecraft:smooth_stone",
                            "fill 490 64 653 490 70 656 minecraft:stone",
                            "fill 490 64 657 490 70 660 minecraft:cobblestone",
                            "fill 490 64 661 490 70 664 minecraft:oak_planks",
                            "fill 490 64 665 490 70 668 minecraft:bricks"))
                        server.getCommands().performPrefixedCommand(source, command);
                    appliedFixture = fixture;
                    LOGGER.info("ShaderLOD manual PBR wall created in this development world");
                }
                for (String command : List.of("time set " + time, "weather " + weather, "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamemode spectator @a", String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.3f %.3f", x, y, z, yaw, pitch))) server.getCommands().performPrefixedCommand(source, command);
            });
            controlModified = modified;
            GpuTimers.resetSamples();
            LOGGER.info("ShaderLOD landscape camera applied: {}", control);
        } catch (Exception exception) { LOGGER.warn("Landscape control rejected: {}", exception.toString()); }
    }
    private void requireUntimedSettingsInspection() {
        if (!ModList.get().isLoaded("shaderlod") || benchmark != null || workload != null || physical != null
                || cameraQualityRequested || historyCaptureRequested
                || Boolean.getBoolean("shaderlod.dev.benchmark") || Boolean.getBoolean("shaderlod.dev.measureWorkload")
                || Boolean.getBoolean("shaderlod.dev.benchmarkOnly") || Boolean.getBoolean("shaderlod.dev.measureOnly")
                || Boolean.parseBoolean(System.getProperty("shaderlod.dev.gpuTiming", "true"))
                || Boolean.parseBoolean(System.getProperty("shaderlod.dev.cpuTiming", "true")))
            throw new IllegalArgumentException("Settings probe requires untimed inspection");
    }
    private String requireNativeSettingsProbeSource() throws java.io.IOException {
        String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
        if (selected.isBlank() || selected.contains("/") || selected.contains("\\") || selected.contains(":")
                || selected.equals(".") || selected.equals("..") || selected.indexOf(0) >= 0
                || !selected.equals(Iris.getCurrentPackName()) || Iris.isFallback()
                || !net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse())
            throw new IllegalArgumentException("Settings probe requires a loaded original shader pack");
        var source = com.pp.shaderlod.integration.PackSourceReader.read(Iris.getShaderpacksDirectory().resolve(selected));
        var recognized = com.pp.shaderlod.packs.SupportedPackCatalog.recognize(source.memberSha256());
        if (recognized.isEmpty() || recognized.get().stage()
                != com.pp.shaderlod.packs.SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL)
            throw new IllegalArgumentException("Settings probe requires known original source");
        String id = recognized.get().pack().id();
        if (id.equals("photon") && selected.equals("photon_v1.3b.zip")) return id;
        if (id.equals("makeup-ultra-fast-shaders") && source.sourceSha256().equals(
                "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8")) return id;
        throw new IllegalArgumentException("Settings probe requires original Photon or exact MakeUp 9.5g");
    }
    private void tileWindow(Minecraft client) {
        var window = client.getWindow();
        boolean serialMeasurement = PhysicalResolutionSession.requested() || Boolean.getBoolean("shaderlod.dev.measureOnly")
                || Boolean.getBoolean("shaderlod.dev.benchmarkOnly") || Boolean.getBoolean("shaderlod.dev.cameraQualityOnly")
                || Boolean.getBoolean("shaderlod.dev.pixelHistoryCaptureOnly");
        window.setTitle(historyCaptureRequested ? "ShaderLOD history diagnostic" : serialMeasurement ? (enabled ? "ShaderLOD serial measurement" : "Shader serial baseline")
                : enabled ? "ShaderLOD landscape — LOD ON (left)" : "Shader landscape — BASELINE (right)");
        if (BackgroundLaunch.enabled()) {
            client.options.pauseOnLostFocus = false;
            client.setWindowActive(GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowAttrib(window.getWindow(), GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            LOGGER.info("ShaderLOD background launch: hidden window, no activation or focus request; timing unqualified");
            return;
        }
        // Do not detach the startup fullscreen monitor: setWindowed followed by
        // a toggle can leave Minecraft's requested/actual flags equal while the
        // native window is still tiled. Let normal display processing enter fullscreen.
        if (Boolean.getBoolean("shaderlod.dev.comparisonFullscreen")) {
            if (!window.isFullscreen()) window.toggleFullScreen();
            client.options.fullscreen().set(true);
            // This finite development window must keep its requested video mode if
            // focus changes. Do not resize or refocus it during measurements.
            int previousAutoIconify = GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_AUTO_ICONIFY);
            GLFW.glfwSetWindowAttrib(window.getWindow(), GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
            GLFW.glfwShowWindow(window.getWindow());
            GLFW.glfwFocusWindow(window.getWindow());
            LOGGER.info("ShaderLOD comparison fullscreen startup focus requested; autoIconify {} -> false (owned window lifetime)", previousAutoIconify);
            LOGGER.info("ShaderLOD inspection requested fullscreen: {}", window.isFullscreen());
            return;
        }
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            GLFW.glfwGetMonitorWorkarea(GLFW.glfwGetPrimaryMonitor(), x, y, w, h);
            int width = (w.get(0) - 24) / 2, height = Math.min(h.get(0) - 80, width * 9 / 16);
            int windowScale = Integer.getInteger("shaderlod.dev.benchmarkWindowScale", 1);
            if (windowScale < 1 || windowScale > 2) throw new IllegalArgumentException("Benchmark window scale must be 1 or 2");
            width *= windowScale;
            height *= windowScale;
            if (width > w.get(0) || height > h.get(0)) throw new IllegalArgumentException("Benchmark window exceeds monitor work area");
            window.setWindowed(width, height);
            GLFW.glfwSetWindowSize(window.getWindow(), width, height);
            int horizontal = serialMeasurement ? x.get(0) + (w.get(0) - width) / 2
                    : x.get(0) + 8 + (enabled ? 0 : width + 8);
            int vertical = y.get(0) + (h.get(0) - height) / 2;
            GLFW.glfwSetWindowPos(window.getWindow(), horizontal, vertical);
            if (serialMeasurement) {
                if (com.pp.shaderlod.dev.timing.MacWindowStateEvidence.requested())
                    LOGGER.info("ShaderLOD mac window state: {}", com.pp.shaderlod.dev.timing.MacWindowStateEvidence.capture(window.getWindow(), "before_startup_focus"));
                GLFW.glfwShowWindow(window.getWindow());
                GLFW.glfwFocusWindow(window.getWindow());
                if (com.pp.shaderlod.dev.timing.MacWindowStateEvidence.requested())
                    LOGGER.info("ShaderLOD mac window state: {}", com.pp.shaderlod.dev.timing.MacWindowStateEvidence.capture(window.getWindow(), "after_startup_focus"));
            }
            LOGGER.info("ShaderLOD window layout: serial={}, x={}, y={}, width={}, height={}",
                    serialMeasurement, horizontal, vertical, width, height);
        }
    }
    private void draw(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (!nativeFullscreenReported && BackgroundLaunch.enabled()) {
            var window = client.getWindow(); var target = client.getMainRenderTarget();
            if (window.getWidth() > 0 && window.getHeight() > 0 && target.width == window.getWidth() && target.height == window.getHeight()) {
                LOGGER.info("ShaderLOD background window ready: framebuffer={}x{}, target={}x{}, visible={}, focused={}, iconified={}",
                    window.getWidth(), window.getHeight(), target.width, target.height,
                    GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_VISIBLE),
                    GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_FOCUSED),
                    GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_ICONIFIED));
                nativeFullscreenReported = true;
            }
        }
        if (!nativeFullscreenReported && Boolean.getBoolean("shaderlod.dev.comparisonFullscreen")) {
            var window = client.getWindow();
            var target = client.getMainRenderTarget();
            long monitor = GLFW.glfwGetWindowMonitor(window.getWindow());
            if (window.isFullscreen() && monitor != 0 && window.getWidth() > 0 && window.getHeight() > 0
                    && target.width == window.getWidth() && target.height == window.getHeight()) {
                LOGGER.info("ShaderLOD inspection native fullscreen: true, monitor={}, framebuffer={}x{}, target={}x{}, focused={}",
                        monitor, window.getWidth(), window.getHeight(), target.width, target.height,
                        GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
                nativeFullscreenReported = true;
            }
        }
        if (historyCaptureRequested) return;
        if (cameraQuality != null) {
            event.getGuiGraphics().flush();
            cameraQuality.render(client);
            return;
        }
        if (client.level == null || client.options.hideGui) return;
        boolean mod = ModList.get().isLoaded("shaderlod");
        var lines = new ArrayList<String>();
        lines.add((mod ? "LOD " + (ShaderLodConfig.snapshot().enabled() ? "ON " : "OFF ") + ShaderLodConfig.PRESET.get() : "SHADER BASELINE (no ShaderLOD)") + " | " + client.getFps() + " FPS");
        lines.add("Pack: " + Iris.getCurrentPackName());
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
                lines.add(String.format(Locale.ROOT, "%s %.2f ms", scope, stats.meanMs()));
            }
        }
        String distantReach = ModList.get().isLoaded("distanthorizons") ? "DH " + dhChunks + "ch" : "DH absent";
        lines.add(client.getWindow().getWidth() + " x " + client.getWindow().getHeight() + " | MC " + client.options.renderDistance().get() + "/" + client.options.getEffectiveRenderDistance() + "ch | " + distantReach);
        boolean serial = Boolean.getBoolean("shaderlod.dev.measureOnly") || Boolean.getBoolean("shaderlod.dev.benchmarkOnly");
        lines.add(timings.status() + (serial ? " | serial measurement" : " | shared GPU preview"));
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
