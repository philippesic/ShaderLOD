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

class BslQuadSettingsSelectionTest {
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
        boolean nativeRendererAvailable;
        // Synthetic selection availability only; this fixture does not establish a live GL certificate.
        public boolean nativeRendererAvailable() { return nativeRendererAvailable; }
        final Map<String, String> queue = new HashMap<>();
        public Path directory() { return temporary; }
        public String selected() { return selected; }
        public Map<String, String> queue() { return queue; }
        public Map<String, String> declaredDefaultOptions() { return bslDeclaredDefaults(); }
        public Map<String, String> effectiveOptions() {
            var values = new HashMap<>(declaredDefaultOptions());
            Path sidecar = temporary.resolve(selected + ".txt");
            if (Files.exists(sidecar)) {
                var properties = new java.util.Properties();
                try (var input = Files.newInputStream(sidecar)) { properties.load(input); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                properties.forEach((key, value) -> values.put((String) key, (String) value));
            }
            return values;
        }
        public void select(String name) { selected = name; }
        public void save() {}
    }
    private Map<String, String> bslDeclaredDefaults() {
        var sources = new HashMap<String, net.irisshaders.iris.shaderpack.option.OptionAnnotatedSource>();
        var refs = new java.util.HashSet<String>();
        try (var archive = new java.util.zip.ZipFile("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip")) {
            for (var entry : archive.stream().toList()) {
                String name = entry.getName();
                if (!name.startsWith("shaders/") || !(name.endsWith(".glsl") || name.endsWith(".vsh")
                        || name.endsWith(".fsh") || name.endsWith(".csh"))) continue;
                var source = new net.irisshaders.iris.shaderpack.option.OptionAnnotatedSource(
                        new String(archive.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                sources.put(name, source); refs.addAll(source.getBooleanDefineReferences().keySet());
            }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        var builder = net.irisshaders.iris.shaderpack.option.OptionSet.builder();
        sources.forEach((name, source) -> builder.addAll(source.getOptionSet(
                net.irisshaders.iris.shaderpack.include.AbsolutePackPath.fromAbsolutePath("/" + name.substring(8)), refs)));
        var options = builder.build(); var defaults = new HashMap<String, String>();
        options.getBooleanOptions().forEach((key, option) ->
                defaults.put(key, Boolean.toString(option.getOption().getDefaultValue())));
        options.getStringOptions().forEach((key, option) -> defaults.put(key, option.getOption().getDefaultValue()));
        return defaults;
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
    private Backend bsl() throws IOException {
        Files.copy(Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip"),
                temporary.resolve("Bsl.zip"));
        Files.writeString(temporary.resolve("Bsl.zip.txt"),
                "# My native shader quality\r\nTAA=true\r\nADVANCED_MATERIALS=false\r\nREFLECTION=2\r\n");
        var backend = new Backend(); backend.selected = "Bsl.zip";
        backend.nativeRendererAvailable = true; return backend;
    }
    @Test void bslNativeSelectionsPreserveSourceOptionsAndIndependentControls() throws Exception {
        var backend = bsl(); var before = snapshot();
        ShaderLodConfig.TERRAIN_ENABLED.set(true); ShaderLodConfig.NEAR.set(48.0);
        var generation = com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();
        // Balanced/legacy Aggressive intentionally use shader-work recipes, even with a renderer route.
        for (LodPreset preset : java.util.List.of(LodPreset.PERFORMANCE, LodPreset.QUALITY)) {
            assertTrue(ShaderLodSettings.apply(true, preset, () -> new PackSelectionTransaction(backend), () -> {
                assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                assertEquals("Bsl.zip", backend.selected);
            }, () -> false));
            assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
            assertTrue(ShaderLodConfig.ENABLED.get()); assertEquals(preset, ShaderLodConfig.PRESET.get());
            assertEquals("Bsl.zip", backend.selected);
            var after = snapshot(); assertEquals(before.keySet(), after.keySet());
            before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        }
        assertTrue(ShaderLodSettings.apply(false, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        assertFalse(ShaderLodConfig.ENABLED.get()); assertEquals("Bsl.zip", backend.selected);
        assertTrue(ShaderLodConfig.TERRAIN_ENABLED.get()); assertEquals(48.0, ShaderLodConfig.NEAR.get());
        assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation() > generation);
    }
    @Test void unavailableBslRendererUsesRecipeAndOffRestoresNativeSelection() throws Exception {
        var backend = bsl(); backend.nativeRendererAvailable = false;
        byte[] original = Files.readAllBytes(temporary.resolve("Bsl.zip"));
        byte[] chosen = Files.readAllBytes(temporary.resolve("Bsl.zip.txt"));
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        assertNotEquals("Bsl.zip", backend.selected);
        assertTrue(Files.readString(temporary.resolve(backend.selected + ".txt"))
                .contains("SHADERLOD_REFINEMENT_ADAPTER=1"));
        assertTrue(ShaderLodConfig.ENABLED.get());
        assertTrue(ShaderLodSettings.apply(false, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        assertEquals("Bsl.zip", backend.selected); assertFalse(ShaderLodConfig.ENABLED.get());
        assertArrayEquals(original, Files.readAllBytes(temporary.resolve("Bsl.zip")));
        assertArrayEquals(chosen, Files.readAllBytes(temporary.resolve("Bsl.zip.txt")));
    }
    @Test void unavailableBslRendererKeepsExplicitQueuedDefaultAndUserEdit() throws Exception {
        var backend = bsl(); backend.nativeRendererAvailable = false;
        String clouds = backend.declaredDefaultOptions().get("CLOUDS"); assertNotNull(clouds);
        backend.queue.put("CLOUDS", clouds); backend.queue.put("REFLECTION", "1");
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        assertTrue(backend.queue.isEmpty());
        assertTrue(ShaderLodSettings.apply(false, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        var restored = new java.util.Properties();
        try (var input = Files.newInputStream(temporary.resolve("Bsl.zip.txt"))) { restored.load(input); }
        assertEquals(clouds, restored.getProperty("CLOUDS")); assertEquals("1", restored.getProperty("REFLECTION"));
    }
    @Test void bslRepeatedPerformanceConsumesPendingNativeQualityEdit() throws Exception {
        var backend = bsl(); var count = new AtomicInteger();
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
        assertEquals("Bsl.zip", backend.selected);
        assertTrue(Files.readString(temporary.resolve("Bsl.zip.txt")).contains("TAA=false"));
        assertArrayEquals(archive, Files.readAllBytes(temporary.resolve(backend.selected)));
    }
    @Test void bslFailedNativeReloadRestoresConfigSidecarAndQueue() throws Exception {
        bslFailure(false, true);
    }
    @Test void bslNativeFallbackRestoresConfigSidecarAndQueue() throws Exception {
        bslFailure(true, true);
    }
    @Test void bslConfigOnlyReloadFailureRestoresUnqueuedCanonicalWriterOutput() throws Exception {
        bslFailure(false, false);
    }
    private void bslFailure(boolean fallback, boolean queued) throws Exception {
        var backend = bsl();
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); var before = snapshot();
        var path = temporary.resolve("Bsl.zip.txt");
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        var mode = Files.getPosixFilePermissions(path);
        if (queued) backend.queue.put("TAA", "false");
        var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                    assertEquals("Bsl.zip", backend.selected);
                    if (reloads.incrementAndGet() == 1) {
                        irisWritePending(backend);
                        if (!fallback) throw new IOException("Rejected MakeUp after canonical Iris write");
                    } else {
                        assertEquals(core, ShaderLodConfig.snapshot());
                        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
                        assertTrue(backend.queue.isEmpty());
                        assertArrayEquals(before.get("Bsl.zip.txt"), Files.readAllBytes(path));
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

    private Backend staleLegacyBsl() throws IOException {
        Path fixture = Path.of("dev/logs/cross-shader-performance-r3/bsl-quad-transfer-r1/integration-r2/legacy-fixture/bsl");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(fixture), "Prepared legacy Bsl fixture required");
        try (var paths = Files.list(fixture)) {
            for (var path : paths.toList()) if (!path.getFileName().toString().equals("selected.txt"))
                Files.copy(path, temporary.resolve(path.getFileName()));
        }
        var backend = new Backend(); backend.selected = Files.readString(fixture.resolve("selected.txt"));
        backend.nativeRendererAvailable = true; return backend;
    }
    private Backend legacyBsl() throws IOException {
        // Generate through today's pinned service, not an invented marker or a stale recipe receipt.
        var backend = bsl();
        var result = new com.pp.shaderlod.packs.PackSelectionService().apply(temporary,
                backend.selected, true, LodPreset.PERFORMANCE, Map.of(), false);
        assertTrue(result.accepted(), result.detail()); assertTrue(result.generatedArchive());
        backend.selected = result.packFile(); return backend;
    }
    @Test void staleLegacyBslRecipeMismatchRefusesWithoutWrites() throws Exception {
        var backend = staleLegacyBsl(); var before = snapshot(); var selected = backend.selected;
        backend.queue.put("TAA", "false"); var queue = Map.copyOf(backend.queue);
        var failure = assertThrows(IOException.class,
                () -> new PackSelectionTransaction(backend).prepare(true, LodPreset.PERFORMANCE));
        assertTrue(com.pp.shaderlod.integration.KnownRuntimeBoundary.isRefusal(failure));
        var core = ShaderLodConfig.snapshot(); var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals(0, reloads.get(), "A stale generated graph must not be reloaded for recovery");
        assertEquals(core, ShaderLodConfig.snapshot());
        assertEquals(selected, backend.selected); assertEquals(queue, backend.queue);
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((key, bytes) -> assertArrayEquals(bytes, after.get(key), key));
    }
    @Test void legacyBslPerformanceReturnsOriginalAndPreservesPendingQualityEdits() throws Exception {
        var backend = legacyBsl();
        byte[] original = Files.readAllBytes(temporary.resolve("Bsl.zip"));
        backend.queue.put("TAA", "false");
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> irisWritePending(backend), () -> false));
        assertEquals("Bsl.zip", backend.selected); assertTrue(backend.queue.isEmpty());
        String options = Files.readString(temporary.resolve("Bsl.zip.txt"));
        assertTrue(options.contains("TAA=false")); assertFalse(options.contains("SHADERLOD_REFINEMENT_ADAPTER"));
        assertArrayEquals(original, Files.readAllBytes(temporary.resolve("Bsl.zip")));
    }
    @Test void rejectedLegacyBslMigrationRestoresBothCanonicalWritesAndPreviousSelection() throws Exception {
        var backend = legacyBsl(); String previous = backend.selected;
        Path originalPath = temporary.resolve("Bsl.zip.txt"), recoveryPath = temporary.resolve(previous + ".txt");
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
                        assertEquals("Bsl.zip", backend.selected);
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
        var backend = bsl(); var path = temporary.resolve("Bsl.zip.txt"); Files.delete(path);
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); backend.queue.put("TAA", "false");
        var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    if (reloads.incrementAndGet() == 1) {
                        irisWritePending(backend); assertTrue(Files.exists(path));
                        throw new IOException("Rejected queued Bsl TAA after first native sidecar creation");
                    }
                    assertTrue(backend.queue.isEmpty()); assertFalse(Files.exists(path));
                    irisWritePending(backend); // Iris deletes the empty canonical options map.
                }, () -> false));
        assertEquals(2, reloads.get()); assertFalse(Files.exists(path));
        assertEquals(Map.of("TAA", "false"), backend.queue); assertEquals(core, ShaderLodConfig.snapshot());
    }

}
