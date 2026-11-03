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
    private Object savedRequests;
    private java.lang.reflect.Method restoreRequests;
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

    @BeforeEach void load() throws Exception {
        var capture=com.pp.shaderlod.optimization.OptimizationConfig.class.getDeclaredMethod("state");capture.setAccessible(true);savedRequests=capture.invoke(null);
        restoreRequests=com.pp.shaderlod.optimization.OptimizationConfig.class.getDeclaredMethod("restoreState",capture.getReturnType());restoreRequests.setAccessible(true);
        com.pp.shaderlod.optimization.OptimizationConfig.load(temporary.resolve("requests.properties"));
        com.pp.shaderlod.optimization.OptimizationConfig.replace(com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested());
        FMLPaths.loadAbsolutePaths(temporary);
        FMLConfig.load();
        loaded = new Loaded(temporary.resolve("shaderlod-effects-client.toml"));
        ShaderLodConfig.SPEC.correct(loaded.config());
        ShaderLodConfig.SPEC.acceptConfig(loaded.delegate);
    }

    @AfterEach void unload() throws Exception {
        try{ShaderLodConfig.SPEC.acceptConfig(null);}finally{if(restoreRequests!=null)restoreRequests.invoke(null,savedRequests);}
    }

    private final class Backend implements PackSelectionTransaction.Backend {
        String selected = "Photon.zip";
        boolean rendererAvailable;
        public boolean nativeRendererAvailable(){return rendererAvailable;}
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
    @Test void qualityToPerformanceUsesOneOwnedForwardTransactionForBothLoads()throws Exception {
        var backend=fixture();ShaderLodConfig.ENABLED.set(true);ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var selections=new AtomicInteger();var loads=new AtomicInteger();var forwards=new AtomicInteger();
        assertTrue(ShaderLodSettings.apply(true,LodPreset.PERFORMANCE,()->{selections.incrementAndGet();return new PackSelectionTransaction(backend);},
                ()->{loads.incrementAndGet();assertEquals(LodPreset.PERFORMANCE,ShaderLodConfig.PRESET.get());assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());},
                ()->false,(selection,reload)->{forwards.incrementAndGet();selection.reload(reload::run);selection.reload(reload::run);}));
        assertEquals(1,selections.get());assertEquals(1,forwards.get());assertEquals(2,loads.get());
        assertNotEquals("Photon.zip",backend.selected);assertEquals(LodPreset.PERFORMANCE,ShaderLodConfig.PRESET.get());
    }
    @Test void failedActivationLoadRestoresQualityThroughTheSameSelectionOwner()throws Exception {
        var backend=fixture();ShaderLodConfig.ENABLED.set(true);ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var before=snapshot();var selections=new AtomicInteger();var loads=new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true,LodPreset.PERFORMANCE,()->{selections.incrementAndGet();return new PackSelectionTransaction(backend);},
                ()->{int n=loads.incrementAndGet();if(n==2)throw new IOException("activation load rejected");if(n==3){assertEquals("Photon.zip",backend.selected);assertEquals(LodPreset.QUALITY,ShaderLodConfig.PRESET.get());}},
                ()->false,(selection,reload)->{selection.reload(reload::run);selection.reload(reload::run);}));
        assertEquals(1,selections.get());assertEquals(3,loads.get());assertEquals("Photon.zip",backend.selected);
        assertTrue(ShaderLodConfig.ENABLED.get());assertEquals(LodPreset.QUALITY,ShaderLodConfig.PRESET.get());
        var after=snapshot();assertEquals(before.keySet(),after.keySet());before.forEach((name,bytes)->assertArrayEquals(bytes,after.get(name),name));
        assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
    }
    @Test void loaderApplyForwardsQualityAndKeepsCoreEnabledAndIndependentControls() throws Exception {
        var backend = fixture(); var reloads = new AtomicInteger();
        ShaderLodConfig.TERRAIN_ENABLED.set(true);
        ShaderLodConfig.OPTIMIZE_SHADOW_CULLING.set(true);
        ShaderLodConfig.NEAR.set(48.0);
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        String generated = backend.selected; assertNotEquals("Photon.zip", generated);
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals("Photon.zip", backend.selected);
        assertTrue(ShaderLodConfig.ENABLED.get()); assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
        assertTrue(ShaderLodConfig.TERRAIN_ENABLED.get()); assertTrue(ShaderLodConfig.OPTIMIZE_SHADOW_CULLING.get());
        assertEquals(48.0, ShaderLodConfig.NEAR.get());
        int settled = reloads.get();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals(settled, reloads.get());
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals(generated, backend.selected);
        assertTrue(ShaderLodSettings.apply(false, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals("Photon.zip", backend.selected); assertFalse(ShaderLodConfig.ENABLED.get());
        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
    }
    @Test void rejectedQualityReloadRestoresPriorCoreGraphFilesAndQueue() throws Exception {
        var backend = fixture();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        String generated = backend.selected; var before = snapshot(); backend.queue.put("INFO", "3");
        var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.QUALITY, () -> new PackSelectionTransaction(backend), () -> {
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
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        var config = ShaderLodConfig.snapshot();
        backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY,
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
        Path file = temporary.resolve(backend.selected + ".txt");
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
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        var core = ShaderLodConfig.snapshot(); var preset = ShaderLodConfig.PRESET.get();
        Path nativeFile = temporary.resolve("Photon.zip.txt");
        Files.setPosixFilePermissions(nativeFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        var mode = Files.getPosixFilePermissions(nativeFile);
        byte[] original = Files.readAllBytes(nativeFile);
        var before = snapshot(); backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.QUALITY,
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
        assertTrue(ShaderLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        String external = "# independent edit\nTAA=true\nTAAU=false\nINFO=1\n";
        assertFalse(ShaderLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {
                    reloads.incrementAndGet(); irisWritePending(backend);
                    Files.writeString(temporary.resolve("Photon.zip.txt"), external);
                    throw new IOException("Failed after a concurrent external edit");
                }, () -> false));
        assertEquals(1, reloads.get()); assertEquals(external, Files.readString(temporary.resolve("Photon.zip.txt")));
        assertEquals("Photon.zip", backend.selected); assertEquals(Map.of("INFO", "3"), backend.queue);
    }
    private Backend makeUp() throws IOException {
        Files.copy(Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),
                temporary.resolve("MakeUp.zip"));
        Files.writeString(temporary.resolve("MakeUp.zip.txt"),
                "# My native shader quality\r\nAA_TYPE=2\r\nREFLECTION_SLIDER=2\r\n");
        var backend = new Backend(); backend.selected = "MakeUp.zip"; backend.rendererAvailable=true; return backend;
    }
    @Test void makeUpNativeSelectionsPreserveSourceOptionsAndIndependentControls() throws Exception {
        var backend = makeUp(); var before = snapshot();
        ShaderLodConfig.TERRAIN_ENABLED.set(true); ShaderLodConfig.NEAR.set(48.0);
        var generation = com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();
        for (LodPreset preset : java.util.List.of(LodPreset.PERFORMANCE,LodPreset.QUALITY)) {
            assertTrue(ShaderLodSettings.apply(true, preset, () -> new PackSelectionTransaction(backend), () -> {
                assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                assertEquals("MakeUp.zip", backend.selected);
            }, () -> false));
            assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
            assertTrue(ShaderLodConfig.ENABLED.get()); assertEquals(preset, ShaderLodConfig.PRESET.get());
            assertEquals("MakeUp.zip", backend.selected);
            var after = snapshot(); assertEquals(before.keySet(), after.keySet());
            before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        }
        assertTrue(ShaderLodSettings.apply(false, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        assertFalse(ShaderLodConfig.ENABLED.get()); assertEquals("MakeUp.zip", backend.selected);
        assertTrue(ShaderLodConfig.TERRAIN_ENABLED.get()); assertEquals(48.0, ShaderLodConfig.NEAR.get());
        assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation() > generation);
    }
    @Test void makeUpUnavailableRendererAndNonPerformancePresetUseGeneratedGraph()throws Exception {
        var backend=makeUp();backend.rendererAvailable=false;
        assertTrue(ShaderLodSettings.apply(true,LodPreset.PERFORMANCE,()->new PackSelectionTransaction(backend),()->{},()->false));
        assertNotEquals("MakeUp.zip",backend.selected);
        assertTrue(ShaderLodSettings.apply(false,LodPreset.QUALITY,()->new PackSelectionTransaction(backend),()->{},()->false));
        assertEquals("MakeUp.zip",backend.selected);
        backend.rendererAvailable=true;
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,()->new PackSelectionTransaction(backend),()->{},()->false));
        assertNotEquals("MakeUp.zip",backend.selected);
        com.pp.shaderlod.optimization.OptimizationConfig.replace(com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested()
                .with(com.pp.shaderlod.optimization.OptimizationId.AO_BUDGET,true));
        assertTrue(ShaderLodSettings.apply(true,LodPreset.PERFORMANCE,()->new PackSelectionTransaction(backend),()->{},()->false));
        assertNotEquals("MakeUp.zip",backend.selected); // An admitted renderer never bypasses a loaded GLSL request.
    }
    @Test void makeUpRepeatedPerformanceConsumesPendingNativeQualityEdit() throws Exception {
        var backend = makeUp(); var count = new AtomicInteger();
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        byte[] archive = Files.readAllBytes(temporary.resolve(backend.selected));
        backend.queue.put("AA_TYPE", "0");
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    count.incrementAndGet();
                    assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                    irisWritePending(backend);
                }, () -> false));
        assertEquals(1, count.get()); assertTrue(backend.queue.isEmpty());
        assertEquals("MakeUp.zip", backend.selected);
        assertTrue(Files.readString(temporary.resolve("MakeUp.zip.txt")).contains("AA_TYPE=0"));
        assertArrayEquals(archive, Files.readAllBytes(temporary.resolve(backend.selected)));
    }
    @Test void makeUpFailedNativeReloadRestoresConfigSidecarAndQueue() throws Exception {
        makeUpFailure(false, true);
    }
    @Test void makeUpNativeFallbackRestoresConfigSidecarAndQueue() throws Exception {
        makeUpFailure(true, true);
    }
    @Test void makeUpConfigOnlyReloadFailureRestoresUnqueuedCanonicalWriterOutput() throws Exception {
        makeUpFailure(false, false);
    }
    private void makeUpFailure(boolean fallback, boolean queued) throws Exception {
        var backend = makeUp();
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); var before = snapshot();
        var path = temporary.resolve("MakeUp.zip.txt");
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        var mode = Files.getPosixFilePermissions(path);
        if (queued) backend.queue.put("AA_TYPE", "0");
        var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                    assertEquals("MakeUp.zip", backend.selected);
                    if (reloads.incrementAndGet() == 1) {
                        irisWritePending(backend);
                        if (!fallback) throw new IOException("Rejected MakeUp after canonical Iris write");
                    } else {
                        assertEquals(core, ShaderLodConfig.snapshot());
                        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
                        assertTrue(backend.queue.isEmpty());
                        assertArrayEquals(before.get("MakeUp.zip.txt"), Files.readAllBytes(path));
                        irisWritePending(backend);
                    }
                }, () -> fallback && reloads.get() == 1));
        assertEquals(2, reloads.get()); assertEquals(core, ShaderLodConfig.snapshot());
        assertEquals(LodPreset.QUALITY, ShaderLodConfig.PRESET.get());
        assertEquals(queued ? Map.of("AA_TYPE", "0") : Map.of(), backend.queue);
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        assertEquals(mode, Files.getPosixFilePermissions(path));
        assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
    }

    private Backend legacyMakeUp() throws IOException {
        // Assemble through today's public service; stale historical recipe bytes are not current admission.
        var backend=makeUp();
        var generated=new com.pp.shaderlod.packs.PackSelectionService().apply(temporary,backend.selected,true,LodPreset.PERFORMANCE,Map.of(),false);
        assertTrue(generated.accepted(),generated.detail());assertTrue(generated.generatedArchive());
        backend.selected=generated.packFile();return backend;
    }
    @Test void legacyMakeUpPerformanceReturnsOriginalAndPreservesPendingQualityEdits() throws Exception {
        var backend = legacyMakeUp();
        byte[] original = Files.readAllBytes(temporary.resolve("MakeUp.zip"));
        backend.queue.put("AA_TYPE", "0");
        assertTrue(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> irisWritePending(backend), () -> false));
        assertEquals("MakeUp.zip", backend.selected); assertTrue(backend.queue.isEmpty());
        String options = Files.readString(temporary.resolve("MakeUp.zip.txt"));
        assertTrue(options.contains("AA_TYPE=0")); assertFalse(options.contains("SHADERLOD_REFINEMENT_ADAPTER"));
        assertArrayEquals(original, Files.readAllBytes(temporary.resolve("MakeUp.zip")));
    }
    @Test void rejectedLegacyMakeUpMigrationRestoresBothCanonicalWritesAndPreviousSelection() throws Exception {
        var backend = legacyMakeUp(); String previous = backend.selected;
        Path originalPath = temporary.resolve("MakeUp.zip.txt"), recoveryPath = temporary.resolve(previous + ".txt");
        Files.setPosixFilePermissions(originalPath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        Files.setPosixFilePermissions(recoveryPath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var originalMode = Files.getPosixFilePermissions(originalPath);
        var recoveryMode = Files.getPosixFilePermissions(recoveryPath);
        ShaderLodConfig.ENABLED.set(false); ShaderLodConfig.PRESET.set(LodPreset.QUALITY);
        var core = ShaderLodConfig.snapshot(); var before = snapshot();
        backend.queue.put("AA_TYPE", "0"); var reloads = new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {
                    assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                    if (reloads.incrementAndGet() == 1) {
                        assertEquals("MakeUp.zip", backend.selected);
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
        assertEquals(Map.of("AA_TYPE", "0"), backend.queue); assertEquals(core, ShaderLodConfig.snapshot());
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
    }

}
