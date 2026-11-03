package com.pp.shaderlod.fabric;

import static org.junit.jupiter.api.Assertions.*;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricQualitySelectionTest {
    @TempDir Path directory;
    private FabricLodSettings.Preflight originalPreflight;
    @org.junit.jupiter.api.BeforeEach void preflight() { originalPreflight = FabricLodSettings.applyPreflight; FabricLodSettings.applyPreflight = () -> {}; }
    @org.junit.jupiter.api.AfterEach void restorePreflight() { FabricLodSettings.applyPreflight = originalPreflight; }
    private final class Backend implements PackSelectionTransaction.Backend {
        String selected = "Photon.zip";
        Map<String, String> queue = new HashMap<>();
        public Path directory() { return directory; }
        public String selected() { return selected; }
        public Map<String, String> queue() { return queue; }
        public Map<String, String> effectiveOptions() { return Map.of(); }
        public void select(String name) { selected = name; }
        public void save() {}
    }
    private Backend fixture() throws IOException {
        Files.copy(Path.of("dependencies/upstream/photon_v1.3b.zip"), directory.resolve("Photon.zip"));
        Files.writeString(directory.resolve("Photon.zip.txt"), "TAA=true\nTAAU=false\nINFO=2\n");
        FabricLodConfig.load(directory.resolve("settings.json"));
        return new Backend();
    }
    private Map<String, byte[]> snapshot() throws IOException {
        var result = new HashMap<String, byte[]>();
        try (var files = Files.list(directory)) {
            for (var file : files.toList()) if (!file.getFileName().toString().equals("settings.json"))
                result.put(file.getFileName().toString(), Files.readAllBytes(file));
        }
        return result;
    }
    @Test void loaderApplyForwardsQualityWhilePreservingEnabledAndDistances() throws Exception {
        var backend = fixture(); var reloads = new AtomicInteger();
        var before = FabricLodConfig.snapshot();
        assertTrue(FabricLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        String generated = backend.selected; assertNotEquals("Photon.zip", generated);
        assertTrue(FabricLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals("Photon.zip", backend.selected);
        var quality = FabricLodConfig.snapshot(); assertTrue(quality.enabled()); assertEquals(LodPreset.QUALITY, quality.preset());
        assertEquals(before.near(), quality.near()); assertEquals(before.far(), quality.far());
        assertEquals(before.transition(), quality.transition()); assertEquals(before.debug(), quality.debug());
        int settled = reloads.get();
        assertTrue(FabricLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals(settled, reloads.get());
        assertTrue(FabricLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals(generated, backend.selected);
        assertTrue(FabricLodSettings.apply(false, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), reloads::incrementAndGet, () -> false));
        assertEquals("Photon.zip", backend.selected); assertFalse(FabricLodConfig.snapshot().enabled());
    }
    @Test void failedQualityReloadRestoresCompletePriorConfigGraphFilesAndQueue() throws Exception {
        var backend = fixture();
        assertTrue(FabricLodSettings.apply(true, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        var config = FabricLodConfig.snapshot(); String generated = backend.selected;
        var before = snapshot(); backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        assertFalse(FabricLodSettings.apply(true, LodPreset.QUALITY, () -> new PackSelectionTransaction(backend), () -> {
            if (reloads.incrementAndGet() == 1) throw new IOException("Injected shader rejection");
            assertEquals(config, FabricLodConfig.snapshot()); assertEquals(generated, backend.selected);
        }, () -> false));
        assertEquals(2, reloads.get()); assertEquals(config, FabricLodConfig.snapshot());
        assertEquals(Map.of("INFO", "3"), backend.queue); assertEquals(generated, backend.selected);
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
    }

    @Test void repeatedEnabledQualityConsumesQueuedNativeOptionsThroughReload() throws Exception {
        var backend = fixture();
        assertTrue(FabricLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        var config = FabricLodConfig.snapshot();
        backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        assertTrue(FabricLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {
                    reloads.incrementAndGet();
                    assertEquals("Photon.zip", backend.selected);
                    assertEquals(Map.of("INFO", "3"), backend.queue);
                    assertEquals(config, FabricLodConfig.snapshot());
                    irisWritePending(backend);
                }, () -> false));
        assertEquals(1, reloads.get()); assertTrue(backend.queue.isEmpty());
        assertEquals("Photon.zip", backend.selected); assertEquals(config, FabricLodConfig.snapshot());
    }
    private void irisWritePending(Backend backend) throws Exception {
        var properties = new java.util.Properties();
        Path file = directory.resolve("Photon.zip.txt");
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
        assertTrue(FabricLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        var core = FabricLodConfig.snapshot();
        Path nativeFile = directory.resolve("Photon.zip.txt");
        Files.setPosixFilePermissions(nativeFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        var mode = Files.getPosixFilePermissions(nativeFile);
        byte[] original = Files.readAllBytes(nativeFile);
        var before = snapshot(); backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        assertFalse(FabricLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {
                    if (reloads.incrementAndGet() == 1) {
                        irisWritePending(backend);
                        if (!useFallback) throw new IOException("Rejected after Iris option-file write");
                    } else {
                        assertEquals("Photon.zip", backend.selected);
                        assertEquals(core, FabricLodConfig.snapshot());
                        assertTrue(backend.queue.isEmpty(), "Rejected native options must not be retried during recovery");
                        assertArrayEquals(original, Files.readAllBytes(nativeFile));
                        assertEquals(mode, Files.getPosixFilePermissions(nativeFile));
                        irisWritePending(backend); // Real recovery also rewrites valid options canonically.
                    }
                }, () -> useFallback && reloads.get() == 1));
        assertEquals(core, FabricLodConfig.snapshot());
        assertEquals(2, reloads.get()); assertEquals(Map.of("INFO", "3"), backend.queue);
        assertEquals("Photon.zip", backend.selected);
        assertArrayEquals(original, Files.readAllBytes(nativeFile));
        assertEquals(mode, Files.getPosixFilePermissions(nativeFile));
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
    }
    @Test void independentlyEditedNativeFileRefusesOverwriteAndSkipsRecoveryReload() throws Exception {
        var backend = fixture();
        assertTrue(FabricLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {}, () -> false));
        backend.queue.put("INFO", "3"); var reloads = new AtomicInteger();
        String external = "# independent edit\nTAA=true\nTAAU=false\nINFO=1\n";
        assertFalse(FabricLodSettings.apply(true, LodPreset.QUALITY,
                () -> new PackSelectionTransaction(backend), () -> {
                    reloads.incrementAndGet(); irisWritePending(backend);
                    Files.writeString(directory.resolve("Photon.zip.txt"), external);
                    throw new IOException("Failed after a concurrent external edit");
                }, () -> false));
        assertEquals(1, reloads.get()); assertEquals(external, Files.readString(directory.resolve("Photon.zip.txt")));
        assertEquals("Photon.zip", backend.selected); assertEquals(Map.of("INFO", "3"), backend.queue);
    }
    @Test void actualPublicApplyForwardsQualityAndConsumesNativeQueue() throws Exception {
        var backend = fixture();
        var oldFactory = FabricLodSettings.selectionFactory; var oldReload = FabricLodSettings.applyReload; var oldFallback = FabricLodSettings.applyFallback;
        var reloads = new AtomicInteger();
        try {
            FabricLodSettings.applyFallback = () -> false;
            FabricLodSettings.selectionFactory = () -> new PackSelectionTransaction(backend);
            FabricLodSettings.applyReload = () -> { reloads.incrementAndGet(); irisWritePending(backend); };
            assertTrue(FabricLodSettings.apply(true, LodPreset.QUALITY));
            assertEquals("Photon.zip", backend.selected);
            backend.queue.put("INFO", "3"); int settled = reloads.get();
            assertTrue(FabricLodSettings.apply(true, LodPreset.QUALITY));
            assertEquals(settled + 1, reloads.get()); assertTrue(backend.queue.isEmpty());
            assertEquals("Photon.zip", backend.selected);
        } finally { FabricLodSettings.selectionFactory = oldFactory; FabricLodSettings.applyReload = oldReload; FabricLodSettings.applyFallback = oldFallback; }
    }
}
