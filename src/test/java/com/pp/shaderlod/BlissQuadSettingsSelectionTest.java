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

class BlissQuadSettingsSelectionTest {
    private Loaded loaded;
    private String previousResearch;
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
        previousResearch = System.getProperty(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES);
        System.setProperty(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES, "true");
        FMLPaths.loadAbsolutePaths(temporary);
        FMLConfig.load();
        loaded = new Loaded(temporary.resolve("shaderlod-effects-client.toml"));
        ShaderLodConfig.SPEC.correct(loaded.config());
        ShaderLodConfig.SPEC.acceptConfig(loaded.delegate);
    }

    @AfterEach void unload() {
        ShaderLodConfig.SPEC.acceptConfig(null);
        if (previousResearch == null) System.clearProperty(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES);
        else System.setProperty(com.pp.shaderlod.integration.makeupquad.RendererRolloutPolicy.RESEARCH_ROUTES, previousResearch);
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
            return com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions();
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
    private Backend bliss() throws IOException {
        Files.copy(Path.of("dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"),
                temporary.resolve("Bliss.zip"));
        Files.writeString(temporary.resolve("Bliss.zip.txt"),
                "# My native shader quality\r\nTAA=true\r\nTAA_UPSCALING=false\r\n");
        var backend = new Backend(); backend.selected = "Bliss.zip";
        backend.nativeRendererAvailable = true; return backend;
    }
    @Test void blissNativeSelectionsPreserveSourceOptionsAndIndependentControls() throws Exception {
        var backend = bliss(); var before = snapshot();
        ShaderLodConfig.TERRAIN_ENABLED.set(true); ShaderLodConfig.NEAR.set(48.0);
        var generation = com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();
        // Balanced/legacy Aggressive intentionally use shader-work recipes, even with a renderer route.
        for (LodPreset preset : java.util.List.of(LodPreset.PERFORMANCE, LodPreset.QUALITY)) {
            assertTrue(ShaderLodSettings.apply(true, preset, () -> new PackSelectionTransaction(backend), () -> {
                assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                assertEquals("Bliss.zip", backend.selected);
            }, () -> false));
            assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
            assertTrue(ShaderLodConfig.ENABLED.get()); assertEquals(preset, ShaderLodConfig.PRESET.get());
            assertEquals("Bliss.zip", backend.selected);
            var after = snapshot(); assertEquals(before.keySet(), after.keySet());
            before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        }
        assertTrue(ShaderLodSettings.apply(false, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        assertFalse(ShaderLodConfig.ENABLED.get()); assertEquals("Bliss.zip", backend.selected);
        assertTrue(ShaderLodConfig.TERRAIN_ENABLED.get()); assertEquals(48.0, ShaderLodConfig.NEAR.get());
        assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation() > generation);
    }
    @Test void blissRepeatedPerformanceConsumesPendingNativeQualityEdit() throws Exception {
        var backend = bliss(); var count = new AtomicInteger();
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
        assertEquals("Bliss.zip", backend.selected);
        assertTrue(Files.readString(temporary.resolve("Bliss.zip.txt")).contains("TAA=false"));
        assertArrayEquals(archive, Files.readAllBytes(temporary.resolve(backend.selected)));
    }
    @Test void blissFailedNativeReloadRestoresConfigSidecarAndQueue() throws Exception {
        blissFailure(false, true);
    }
    @Test void blissNativeFallbackRestoresConfigSidecarAndQueue() throws Exception {
        blissFailure(true, true);
    }
    @Test void blissConfigOnlyReloadFailureRestoresUnqueuedCanonicalWriterOutput() throws Exception {
        blissFailure(false, false);
    }
    private void blissFailure(boolean fallback, boolean queued) throws Exception {
        var backend = bliss();
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); var before = snapshot();
        var path = temporary.resolve("Bliss.zip.txt");
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        var mode = Files.getPosixFilePermissions(path);
        if (queued) backend.queue.put("TAA", "false");
        var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                    assertEquals("Bliss.zip", backend.selected);
                    if (reloads.incrementAndGet() == 1) {
                        irisWritePending(backend);
                        if (!fallback) throw new IOException("Rejected MakeUp after canonical Iris write");
                    } else {
                        assertEquals(core, ShaderLodConfig.snapshot());
                        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
                        assertTrue(backend.queue.isEmpty());
                        assertArrayEquals(before.get("Bliss.zip.txt"), Files.readAllBytes(path));
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

}
