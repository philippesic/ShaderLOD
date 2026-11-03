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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShaderLodSettingsSelectionTest {
    private Loaded loaded;
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

    @AfterEach void unload() {
        ShaderLodConfig.SPEC.acceptConfig(null);
    }

    private final class Backend implements PackSelectionTransaction.Backend {
        String selected = "Photon.zip";
        final Map<String, String> queue = new HashMap<>();
        public Path directory() { return temporary; }
        public String selected() { return selected; }
        public Map<String, String> queue() { return queue; }
        public Map<String, String> effectiveOptions() { return Map.of(); }
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
    @Test void loaderApplyForwardsQualityAndKeepsCoreEnabledAndIndependentControls() throws Exception {
        var backend = fixture(); var reloads = new AtomicInteger();
        ShaderLodConfig.NEAR.set(48.0);
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        String generated = backend.selected; assertNotEquals("Photon.zip", generated);
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals("Photon.zip", backend.selected);
        assertTrue(ShaderLodConfig.ENABLED.get()); assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
        assertEquals(48.0, ShaderLodConfig.NEAR.get());
        int settled = reloads.get();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals(settled, reloads.get());
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals(generated, backend.selected);
        assertTrue(ShaderLodSettings.apply(false, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals("Photon.zip", backend.selected); assertFalse(ShaderLodConfig.ENABLED.get());
        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
    }
    @Test void rejectedQualityReloadRestoresPriorCoreGraphFilesAndQueue() throws Exception {
        var backend = fixture();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        String generated = backend.selected; var before = snapshot(); backend.queue.put("INFO", "3");
        var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"), () -> new PackSelectionTransaction(backend), () -> {
            if (reloads.incrementAndGet() == 1) throw new IOException("Injected shader rejection");
            assertEquals(generated, backend.selected);
            assertEquals(LodPreset.PERFORMANCE, ShaderLodConfig.PRESET.get());
        }, () -> false));
        assertEquals(2, reloads.get()); assertTrue(ShaderLodConfig.ENABLED.get());
        assertEquals(LodPreset.PERFORMANCE, ShaderLodConfig.PRESET.get());
        assertEquals(generated, backend.selected); assertEquals(Map.of("INFO", "3"), backend.queue);
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
    }

    @Test void repeatedEnabledQualityConsumesQueuedNativeOptionsThroughReload() throws Exception {
        var backend = fixture();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        var config = ShaderLodConfig.snapshot();
        backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), () -> {
                    reloads.incrementAndGet();
                    assertEquals("Photon.zip", backend.selected);
                    assertEquals(Map.of("INFO", "3"), backend.queue);
                    assertTrue(ShaderLodConfig.ENABLED.get()); assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
                    irisWritePending(backend);
                }, () -> false));
        assertEquals(1, reloads.get()); assertTrue(backend.queue.isEmpty());
        assertEquals("Photon.zip", backend.selected); assertEquals(config, ShaderLodConfig.snapshot()); assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
    }
    private void irisWritePending(Backend backend) throws Exception {
        var properties = new java.util.Properties();
        Path file = temporary.resolve("Photon.zip.txt");
        try (var input = Files.newInputStream(file)) { properties.load(input); }
        properties.putAll(backend.queue); backend.queue.clear();
        try (var output = Files.newOutputStream(file)) { properties.store(output, null); }
        PackSelectionTransaction.irisOptionsWritten(file, properties); // Exact pinned writer RETURN.
    }
    @Test void repeatedQualityFailedReloadRestoresConsumedNativeQueueAndOriginalGraph() throws Exception {
        queuedNativeWriteFailure(false);
    }
    @Test void repeatedQualityFallbackRestoresNativeFileAndDefersRejectedQueueUntilAfterRecovery() throws Exception {
        queuedNativeWriteFailure(true);
    }
    private void queuedNativeWriteFailure(boolean useFallback) throws Exception {
        var backend = fixture();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        var core = ShaderLodConfig.snapshot(); var preset = ShaderLodConfig.PRESET.get();
        Path nativeFile = temporary.resolve("Photon.zip.txt");
        Files.setPosixFilePermissions(nativeFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        var mode = Files.getPosixFilePermissions(nativeFile);
        byte[] original = Files.readAllBytes(nativeFile);
        var before = snapshot(); backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), () -> {
                    if (reloads.incrementAndGet() == 1) {
                        irisWritePending(backend);
                        if (!useFallback) throw new IOException("Rejected after Iris option-file write");
                    } else {
                        assertEquals("Photon.zip", backend.selected);
                        assertEquals(core, ShaderLodConfig.snapshot()); assertEquals(preset, ShaderLodConfig.PRESET.get());
                        assertTrue(backend.queue.isEmpty(), "Rejected native options must not be retried during recovery");
                        assertArrayEquals(original, Files.readAllBytes(nativeFile));
                        assertEquals(mode, Files.getPosixFilePermissions(nativeFile));
                        irisWritePending(backend); // Real recovery also rewrites valid options canonically.
                    }
                }, () -> useFallback && reloads.get() == 1));
        assertEquals(core, ShaderLodConfig.snapshot()); assertEquals(preset, ShaderLodConfig.PRESET.get());
        assertEquals(2, reloads.get()); assertEquals(Map.of("INFO", "3"), backend.queue);
        assertEquals("Photon.zip", backend.selected);
        assertArrayEquals(original, Files.readAllBytes(nativeFile));
        assertEquals(mode, Files.getPosixFilePermissions(nativeFile));
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
    }
    @Test void independentlyEditedNativeFileRefusesOverwriteAndSkipsRecoveryReload() throws Exception {
        var backend = fixture();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        String external = "# independent edit\nTAA=true\nTAAU=false\nINFO=1\n";
        assertFalse(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> com.pp.shaderlod.integration.RendererBackend.requireApplySupported(() -> "OpenGL"),
                () -> new PackSelectionTransaction(backend), () -> {
                    reloads.incrementAndGet(); irisWritePending(backend);
                    Files.writeString(temporary.resolve("Photon.zip.txt"), external);
                    throw new IOException("Failed after a concurrent external edit");
                }, () -> false));
        assertEquals(1, reloads.get()); assertEquals(external, Files.readString(temporary.resolve("Photon.zip.txt")));
        assertEquals("Photon.zip", backend.selected); assertEquals(Map.of("INFO", "3"), backend.queue);
    }
}
