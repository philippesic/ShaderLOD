package com.pp.shaderlod;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;

import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SildurQuadSettingsSelectionTest {
    private Loaded loaded;
    private Object previousRequests;
    private Method restoreRequests;
    @TempDir Path temporary;
    /** Use FML's actual sealed LoadedConfig, real event bus and temporary TOML persistence. */
    private static final class Loaded {
        private final CommentedConfig config = CommentedConfig.inMemory();
        private final IConfigSpec.ILoadedConfig delegate;
        private final ModConfig modConfig;
        private final Path path;
        private int saves;
        public CommentedConfig config() { return config; }

        Loaded(Path path) throws ReflectiveOperationException {
            this.path = path;
            var bus = BusBuilder.builder().markerType(IModBusEvent.class).build();
            bus.addListener((ModConfigEvent.Reloading event) -> {
                assertFalse(Thread.holdsLock(ShaderLodConfig.class), "Save must not invert FML's reload lock order");
                saves++;
            });
            var info = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[]{IModInfo.class},
                    (proxy, method, args) -> method.getName().equals("getModId") ? "shaderlod" : null);
            var container = new ModContainer(info) {
                @Override public IEventBus getEventBus() { return bus; }
            };
            var constructor = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class, IConfigSpec.class,
                    ModContainer.class, String.class, ReentrantLock.class);
            constructor.setAccessible(true);
            modConfig = constructor.newInstance(ModConfig.Type.CLIENT, ShaderLodConfig.SPEC, container,
                    path.getFileName().toString(), new ReentrantLock());
            var loadedConstructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                    .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
            loadedConstructor.setAccessible(true);
            delegate = (IConfigSpec.ILoadedConfig) loadedConstructor.newInstance(config, path, modConfig);
        }
    }

    @BeforeEach void load() throws ReflectiveOperationException {
        FMLPaths.loadAbsolutePaths(temporary);
        FMLConfig.load();
        loaded = new Loaded(temporary.resolve("shaderlod-effects-client.toml"));
        ShaderLodConfig.SPEC.correct(loaded.config());
        ShaderLodConfig.SPEC.acceptConfig(loaded.delegate);
    }

    @AfterEach void unload() throws ReflectiveOperationException {
        if (previousRequests != null) restoreRequests.invoke(null, previousRequests);
        ShaderLodConfig.SPEC.acceptConfig(null);
    }

    private final class Backend implements PackSelectionTransaction.Backend {
        String selected = "Photon.zip";
        boolean nativeRendererAvailable;
        // Synthetic selection availability only; this fixture does not establish a live GL certificate.
        public boolean nativeRendererAvailable() { return nativeRendererAvailable; }
        final Map<String, String> queue = new HashMap<>();
        public Path directory() { return temporary; }
        public String selected() { return selected; }
        public Map<String, String> queue() { return queue; }
        public Map<String, String> effectiveOptions() {
            return com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions();
        }
        public void select(String name) { selected = name; }
        public void save() {}
    }
    private Backend fixture() throws IOException {
        Files.copy(Path.of("dependencies/upstream/photon_v1.3b.zip"), temporary.resolve("Photon.zip"));
        Files.writeString(temporary.resolve("Photon.zip.txt"), "TAA=true\nTAAU=false\nINFO=2\n");
        return new Backend();
    }
    private Map<String, byte[]> snapshot() throws IOException {
        var result = new HashMap<String, byte[]>();
        try (var files = Files.list(temporary)) {
            for (var file : files.toList()) if (file.toString().endsWith(".zip") || file.toString().endsWith(".txt")
                    || file.getFileName().toString().startsWith(".shaderlod-selection-"))
                result.put(file.getFileName().toString(), Files.readAllBytes(file));
        }
        return result;
    }
    private void irisWritePending(Backend backend) throws Exception {
        var properties = new java.util.Properties();
        Path file = temporary.resolve(backend.selected + ".txt");
        if (Files.exists(file)) try (var input = Files.newInputStream(file)) { properties.load(input); }
        properties.putAll(backend.queue); backend.queue.clear();
        if (properties.isEmpty()) Files.deleteIfExists(file);
        else try (var output = Files.newOutputStream(file)) { properties.store(output, null); }
        PackSelectionTransaction.irisOptionsWritten(file, properties); // Exact pinned writer RETURN.
    }
    private Backend sildur() throws IOException {
        Files.copy(Path.of("dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"),
                temporary.resolve("Sildur.zip"));
        Files.writeString(temporary.resolve("Sildur.zip.txt"),
                "# My native shader quality\r\nTAA=true\r\nClouds=3\r\nVolumetric_Lighting=false\r\n");
        var backend = new Backend(); backend.selected = "Sildur.zip";
        backend.nativeRendererAvailable = true; return backend;
    }
    @Test void sildurNativeSelectionsPreserveSourceOptionsAndIndependentControls() throws Exception {
        var backend = sildur(); var before = snapshot();
        ShaderLodConfig.TERRAIN_ENABLED.set(true); ShaderLodConfig.NEAR.set(48.0);
        var generation = com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();
        // Balanced/legacy Aggressive intentionally use shader-work recipes, even with a renderer route.
        for (LodPreset preset : java.util.List.of(LodPreset.PERFORMANCE, LodPreset.QUALITY)) {
            assertTrue(ShaderLodSettings.apply(true, preset, () -> new PackSelectionTransaction(backend), () -> {
                assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                assertEquals("Sildur.zip", backend.selected);
            }, () -> false));
            assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
            assertTrue(ShaderLodConfig.ENABLED.get()); assertEquals(preset, ShaderLodConfig.PRESET.get());
            assertEquals("Sildur.zip", backend.selected);
            var after = snapshot(); assertEquals(before.keySet(), after.keySet());
            before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        }
        assertTrue(ShaderLodSettings.apply(false, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        assertFalse(ShaderLodConfig.ENABLED.get()); assertEquals("Sildur.zip", backend.selected);
        assertTrue(ShaderLodConfig.TERRAIN_ENABLED.get()); assertEquals(48.0, ShaderLodConfig.NEAR.get());
        assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation() > generation);
    }
    @Test void sildurRepeatedPerformanceConsumesPendingNativeQualityEdit() throws Exception {
        var backend = sildur(); var count = new AtomicInteger();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        byte[] archive = Files.readAllBytes(temporary.resolve(backend.selected));
        backend.queue.put("TAA", "false");
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    count.incrementAndGet();
                    assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                    irisWritePending(backend);
                }, () -> false));
        assertEquals(1, count.get()); assertTrue(backend.queue.isEmpty());
        assertEquals("Sildur.zip", backend.selected);
        assertTrue(Files.readString(temporary.resolve("Sildur.zip.txt")).contains("TAA=false"));
        assertArrayEquals(archive, Files.readAllBytes(temporary.resolve(backend.selected)));
    }
    @Test void sildurFailedNativeReloadRestoresConfigSidecarAndQueue() throws Exception {
        sildurFailure(false, true);
    }
    @Test void sildurNativeFallbackRestoresConfigSidecarAndQueue() throws Exception {
        sildurFailure(true, true);
    }
    @Test void sildurConfigOnlyReloadFailureRestoresUnqueuedCanonicalWriterOutput() throws Exception {
        sildurFailure(false, false);
    }
    private void sildurFailure(boolean fallback, boolean queued) throws Exception {
        var backend = sildur();
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); var before = snapshot();
        var path = temporary.resolve("Sildur.zip.txt");
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        var mode = Files.getPosixFilePermissions(path);
        if (queued) backend.queue.put("TAA", "false");
        var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                    assertEquals("Sildur.zip", backend.selected);
                    if (reloads.incrementAndGet() == 1) {
                        irisWritePending(backend);
                        if (!fallback) throw new IOException("Rejected MakeUp after canonical Iris write");
                    } else {
                        assertEquals(core, ShaderLodConfig.snapshot());
                        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
                        assertTrue(backend.queue.isEmpty());
                        assertArrayEquals(before.get("Sildur.zip.txt"), Files.readAllBytes(path));
                        irisWritePending(backend);
                    }
                }, () -> fallback && reloads.get() == 1));
        assertEquals(2, reloads.get()); assertEquals(core, ShaderLodConfig.snapshot());
        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
        assertEquals(queued ? Map.of("TAA", "false") : Map.of(), backend.queue);
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        assertEquals(mode, Files.getPosixFilePermissions(path));
        assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
    }

    /** Scope this migration to the actual independent CPU pair, restoring all global request state after the test. */
    private void rendererRequests() throws Exception {
        var type = com.pp.shaderlod.optimization.OptimizationConfig.class;
        var capture = type.getDeclaredMethod("state"); capture.setAccessible(true);
        restoreRequests = type.getDeclaredMethod("restoreState", capture.getReturnType()); restoreRequests.setAccessible(true);
        previousRequests = capture.invoke(null);
        com.pp.shaderlod.optimization.OptimizationConfig.load(temporary.resolve("renderer-requests.properties"));
        com.pp.shaderlod.optimization.OptimizationConfig.enableShaderProfiles();
        com.pp.shaderlod.optimization.OptimizationConfig.selectFamily("sildurs-vibrant-shaders");
        com.pp.shaderlod.optimization.OptimizationConfig.replace(com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested()
            .with(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION, true)
            .with(com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE, true));
    }
    private Backend currentGeneratedSildur() throws Exception {
        rendererRequests();
        var backend = sildur();
        // A stale historical recipe must refuse On. Produce the current graph for migration/recovery instead.
        var result = new com.pp.shaderlod.packs.PackSelectionService().apply(temporary, backend.selected, true,
            LodPreset.BALANCED, backend.effectiveOptions(), false);
        assertTrue(result.accepted(), result.detail()); assertTrue(result.generatedArchive());
        assertNotEquals("Sildur.zip", result.packFile());
        backend.selected = result.packFile();
        assertTrue(backend.nativeRendererAvailable);
        return backend;
    }

    private Backend legacySildur() throws IOException {
        Path fixture = Path.of("dev/logs/cross-shader-performance-r3/sildur-quad-transfer-r1/integration-r2/legacy-fixture/sildur");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(fixture), "Prepared legacy Sildur fixture required");
        try (var paths = Files.list(fixture)) {
            for (var path : paths.toList()) if (!path.getFileName().toString().equals("selected.txt"))
                Files.copy(path, temporary.resolve(path.getFileName()));
        }
        var backend = new Backend(); backend.selected = Files.readString(fixture.resolve("selected.txt"));
        backend.nativeRendererAvailable = true; return backend;
    }
    @Test void historicalSildurPerformanceRefusesBeforeReloadAndLeavesFilesAndQueueUntouched() throws Exception {
        rendererRequests();
        var backend = legacySildur(); String selected = backend.selected;
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); var before = snapshot();
        backend.queue.put("TAA", "false"); var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
            () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals(0, reloads.get()); assertEquals(selected, backend.selected);
        assertEquals(Map.of("TAA", "false"), backend.queue); assertEquals(core, ShaderLodConfig.snapshot());
        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
        assertTrue(ShaderLodSettings.failureMessage().contains("recipe changed"));
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
    }

    @Test void currentGeneratedSildurPerformanceReturnsOriginalAndPreservesPendingQualityEdits() throws Exception {
        var backend = currentGeneratedSildur();
        byte[] original = Files.readAllBytes(temporary.resolve("Sildur.zip"));
        backend.queue.put("TAA", "false");
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> irisWritePending(backend), () -> false));
        assertEquals("Sildur.zip", backend.selected); assertTrue(backend.queue.isEmpty());
        String options = Files.readString(temporary.resolve("Sildur.zip.txt"));
        assertTrue(options.contains("TAA=false")); assertFalse(options.contains("SHADERLOD_EFFECT_ADAPTER"));
        assertArrayEquals(original, Files.readAllBytes(temporary.resolve("Sildur.zip")));
    }
    @Test void rejectedCurrentGeneratedSildurMigrationRestoresBothCanonicalWritesAndPreviousSelection() throws Exception {
        var backend = currentGeneratedSildur(); String previous = backend.selected;
        Path originalPath = temporary.resolve("Sildur.zip.txt"), recoveryPath = temporary.resolve(previous + ".txt");
        Files.setPosixFilePermissions(originalPath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        Files.setPosixFilePermissions(recoveryPath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var originalMode = Files.getPosixFilePermissions(originalPath);
        var recoveryMode = Files.getPosixFilePermissions(recoveryPath);
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); var before = snapshot();
        backend.queue.put("TAA", "false"); var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                    if (reloads.incrementAndGet() == 1) {
                        assertEquals("Sildur.zip", backend.selected);
                        irisWritePending(backend);
                        throw new IOException("Reject migration after original native write");
                    }
                    assertEquals(previous, backend.selected); assertTrue(backend.queue.isEmpty());
                    assertEquals(core, ShaderLodConfig.snapshot());
                    irisWritePending(backend); // Recovery writes the prior generated sidecar.
                }, () -> false));
        assertEquals(2, reloads.get()); assertEquals(previous, backend.selected);
        assertEquals(originalMode, Files.getPosixFilePermissions(originalPath));
        assertEquals(recoveryMode, Files.getPosixFilePermissions(recoveryPath));
        assertEquals(Map.of("TAA", "false"), backend.queue); assertEquals(core, ShaderLodConfig.snapshot());
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
    }

    @Test void rejectedQueuedEditRestoresAbsentNativeSidecarThroughRecoveryDeletion() throws Exception {
        var backend = sildur(); var path = temporary.resolve("Sildur.zip.txt"); Files.delete(path);
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); backend.queue.put("TAA", "false");
        var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    if (reloads.incrementAndGet() == 1) {
                        irisWritePending(backend); assertTrue(Files.exists(path));
                        throw new IOException("Rejected queued Sildur TAA after first native sidecar creation");
                    }
                    assertTrue(backend.queue.isEmpty()); assertFalse(Files.exists(path));
                    irisWritePending(backend); // Iris deletes the empty canonical options map.
                }, () -> false));
        assertEquals(2, reloads.get()); assertFalse(Files.exists(path));
        assertEquals(Map.of("TAA", "false"), backend.queue); assertEquals(core, ShaderLodConfig.snapshot());
    }

}
