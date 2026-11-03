package com.pp.shaderlod.packs;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real pack-service/file integration; only Iris selection and option access are replaced. */
class PackSelectionTransactionTest {
    @TempDir Path directory;
    private final class Backend implements PackSelectionTransaction.Backend {
        String selected = "Photon.zip";
        final Map<String, String> queue = new HashMap<>();
        final Map<String, String> effective = new HashMap<>();
        boolean rejectSave, rendererAvailable, sourceReload;
        int availabilityReads;
        public boolean nativeRendererAvailable() { availabilityReads++; return rendererAvailable; }
        public boolean sourceReloadRequired(boolean enabled,com.pp.shaderlod.LodPreset preset){return sourceReload;}
        public Path directory() { return directory; }
        public String selected() { return selected; }
        public Map<String, String> queue() { return queue; }
        public Map<String, String> effectiveOptions() { return effective; }
        public void select(String name) { selected = name; }
        public void save() throws IOException { if (rejectSave) throw new IOException("Injected Iris save rejection"); }
    }
    private Backend photon() throws IOException {
        Path fixture = Path.of("dependencies/upstream/photon_v1.3b.zip");
        assumeTrue(Files.isRegularFile(fixture), "Pinned local Photon required");
        Files.copy(fixture, directory.resolve("Photon.zip"));
        return new Backend();
    }
    private Properties options(String name) throws IOException {
        var values = new Properties();
        try (var input = Files.newInputStream(directory.resolve(name + ".txt"))) { values.load(input); }
        return values;
    }
    private Map<String, byte[]> sidecars() throws IOException {
        var result = new HashMap<String, byte[]>();
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) if (!path.toString().endsWith(".zip"))
                result.put(path.getFileName().toString(), Files.readAllBytes(path));
        }
        return result;
    }
    private void assertSidecars(Map<String, byte[]> expected) throws IOException {
        var actual = sidecars(); assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((name, value) -> assertArrayEquals(value, actual.get(name), name));
    }
    @Test void ownedSourceMismatchReloadsEvenWhenOriginalArchiveAndOptionsDoNotChange() throws Exception {
        Path fixture=Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");
        assertTrue(Files.isRegularFile(fixture), "Pinned BSL input for the affected route must exist");
        Files.copy(fixture,directory.resolve("Photon.zip"));
        var backend=new Backend();
        backend.effective.putAll(com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions());
        backend.sourceReload=true;
        var before=sidecars();var transaction=new PackSelectionTransaction(backend);
        assertTrue(transaction.prepare(false));
        assertEquals("Photon.zip",backend.selected);assertSidecars(before);
        transaction.restore();assertSidecars(before);
        backend.sourceReload=false;assertFalse(new PackSelectionTransaction(backend).prepare(false));
    }
    @Test void unchangedOriginalRetainsUnsavedQueueAndDoesNotCreateFiles() throws Exception {
        var backend = photon(); backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend);
        assertFalse(transaction.prepare(false));
        assertEquals(Map.of("INFO", "3"), backend.queue);
        assertEquals("Photon.zip", backend.selected);
        assertTrue(sidecars().isEmpty());
    }
    @Test void rejectedPresetOnlyReloadRestoresQueueConsumedByIris() throws Exception {
        var backend = photon(); backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend);
        assertFalse(transaction.prepare(false));
        backend.queue.clear(); // Iris consumed the preserved queue while reloading a new mod preset.
        transaction.restore();
        assertEquals(Map.of("INFO", "3"), backend.queue);
        assertEquals("Photon.zip", backend.selected);
        assertTrue(sidecars().isEmpty());
    }
    @Test void originalEffectiveValuesAndQueuedOverridesReachService() throws Exception {
        var backend = photon();
        backend.effective.putAll(Map.of("TAA", "true", "TAAU", "true", "TAAU_RENDER_SCALE", "0.50", "INFO", "2"));
        backend.queue.put("INFO", "3");
        assertTrue(new PackSelectionTransaction(backend).prepare(true));
        assertEquals("0.50", options(backend.selected).getProperty("TAAU_RENDER_SCALE"));
        assertEquals("3", options(backend.selected).getProperty("INFO"));
        assertEquals("3", options("Photon.zip").getProperty("INFO"));
        assertTrue(backend.queue.isEmpty());
    }
    @Test void generatedPendingEditsArePersistedBeforeOffMerge() throws Exception {
        var backend = photon();
        assertTrue(new PackSelectionTransaction(backend).prepare(true));
        backend.queue.put("INFO", "3");
        backend.queue.put("SHADOW_COLOR", "false");
        assertTrue(new PackSelectionTransaction(backend).prepare(false));
        assertEquals("Photon.zip", backend.selected);
        assertEquals("3", options("Photon.zip").getProperty("INFO"));
        assertEquals("false", options("Photon.zip").getProperty("SHADOW_COLOR"));
        assertTrue(backend.queue.isEmpty());
    }
    @Test void generatedUnchangedSelectionWithQueuedEditsStillNeedsReload() throws Exception {
        var backend = photon();
        new PackSelectionTransaction(backend).prepare(true);
        String selected = backend.selected;
        backend.queue.put("INFO", "3");
        assertTrue(new PackSelectionTransaction(backend).prepare(true));
        assertEquals(selected, backend.selected);
        assertEquals("3", options(selected).getProperty("INFO"));
        assertTrue(backend.queue.isEmpty());
    }
    @Test void rejectedReloadRestoresOwnedFilesSelectionQueueAndKeepsUnrelatedFiles() throws Exception {
        var backend = photon();
        Files.writeString(directory.resolve("Photon.zip.txt"), "# personal quality\nINFO=2\n");
        byte[] originalZip = Files.readAllBytes(directory.resolve("Photon.zip"));
        var prior = sidecars();
        backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend);
        assertTrue(transaction.prepare(true));
        Path createdArchive = directory.resolve(backend.selected);
        // A reload rejects the prepared shader; rollback must not sweep files from other actions.
        Path unrelated = directory.resolve("Other.zip.txt");
        Files.writeString(unrelated, "UNRELATED=1\n");
        transaction.restore();
        assertEquals("Photon.zip", backend.selected);
        assertEquals(Map.of("INFO", "3"), backend.queue);
        assertFalse(Files.exists(createdArchive));
        assertEquals("UNRELATED=1\n", Files.readString(unrelated));
        Files.delete(unrelated); assertSidecars(prior);
        assertArrayEquals(originalZip, Files.readAllBytes(directory.resolve("Photon.zip")));
    }
    @Test void rejectedOffReloadRestoresGeneratedPendingEditsAndStateExactly() throws Exception {
        var backend = photon();
        new PackSelectionTransaction(backend).prepare(true);
        String generated = backend.selected;
        var prior = sidecars();
        backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend);
        assertTrue(transaction.prepare(false));
        transaction.restore();
        assertEquals(generated, backend.selected);
        assertEquals(Map.of("INFO", "3"), backend.queue);
        assertSidecars(prior);
        assertTrue(Files.exists(directory.resolve(generated)));
    }
    @Test void failedIrisSaveCanRollBackAlreadyPreparedSelection() throws Exception {
        var backend = photon(); backend.queue.put("INFO", "3"); backend.rejectSave = true;
        var transaction = new PackSelectionTransaction(backend);
        assertThrows(IOException.class, () -> transaction.prepare(true));
        backend.rejectSave = false;
        transaction.restore();
        assertEquals("Photon.zip", backend.selected);
        assertEquals(Map.of("INFO", "3"), backend.queue);
        assertTrue(sidecars().isEmpty());
    }
    @Test void rollbackPreservesConcurrentSidecarAndQueueEdits() throws Exception {
        var backend = photon(); backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend);
        transaction.prepare(true);
        Path originalOptions = directory.resolve("Photon.zip.txt");
        Files.writeString(originalOptions, "INFO=1\n");
        backend.queue.put("INFO", "1"); backend.queue.put("OTHER", "2");
        assertThrows(IOException.class, transaction::restore);
        assertEquals("INFO=1\n", Files.readString(originalOptions));
        assertEquals(Map.of("INFO", "1", "OTHER", "2"), backend.queue);
        assertEquals("Photon.zip", backend.selected);
    }
    @Test void rollbackRetainsGeneratedArchiveEditedAfterApply() throws Exception {
        var backend = photon();
        var transaction = new PackSelectionTransaction(backend);
        transaction.prepare(true);
        Path archive = directory.resolve(backend.selected);
        Files.writeString(archive, "An independent edit");
        assertThrows(IOException.class, transaction::restore);
        assertEquals("An independent edit", Files.readString(archive));
        assertEquals("Photon.zip", backend.selected);
    }
    @Test void bslOriginalRendererRetainsGraphAndQueuedNativeWriterOwnership() throws Exception {
        Path fixture = Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");
        assumeTrue(Files.isRegularFile(fixture), "Pinned BSL fixture required");
        var backend = new Backend(); backend.selected = "Bsl.zip"; backend.rendererAvailable = true;
        Files.copy(fixture, directory.resolve(backend.selected));
        byte[] originalZip = Files.readAllBytes(directory.resolve(backend.selected));
        var on = new PackSelectionTransaction(backend);
        assertEquals(1, backend.availabilityReads, "Capture availability before the suspended Apply lifecycle");
        backend.rendererAvailable = false; // Runtime refuses while Apply is suspended.
        assertFalse(on.prepare(true), "No derivative or option edit needs a graph reload");
        assertEquals(1, backend.availabilityReads, "Selection never re-queries the suspended runtime");
        assertTrue(on.requiresOwnedNativeRecovery(), "Config-only Iris writes have native ownership");
        assertTrue(sidecars().isEmpty());
        backend.queue.put("ADVANCED_MATERIALS", "true");
        var off = new PackSelectionTransaction(backend);
        assertFalse(off.prepare(false), "Unchanged Off retains queued preferences; a config-driven reload still has native writer ownership");
        assertTrue(off.requiresOwnedNativeRecovery());
        assertEquals("Bsl.zip", backend.selected);
        assertEquals(Map.of("ADVANCED_MATERIALS", "true"), backend.queue);
        assertTrue(sidecars().isEmpty(), "No legacy lossy adapter or private option is generated");
        assertArrayEquals(originalZip, Files.readAllBytes(directory.resolve(backend.selected)));
    }
    @Test void unknownOriginalOffRetainsQueueAndOnReportsUnsupported() throws Exception {
        var backend = new Backend(); backend.selected = "Unknown.zip";
        try (var zip = new ZipOutputStream(Files.newOutputStream(directory.resolve(backend.selected)))) {
            zip.putNextEntry(new ZipEntry("shaders/example.glsl")); zip.write("void main() {}".getBytes()); zip.closeEntry();
        }
        backend.queue.put("QUALITY", "high");
        assertFalse(new PackSelectionTransaction(backend).prepare(false));
        var error = assertThrows(IOException.class, () -> new PackSelectionTransaction(backend).prepare(true));
        assertThrows(IOException.class, () -> new PackSelectionTransaction(backend).prepare(true, com.pp.shaderlod.LodPreset.QUALITY));
        assertEquals("LOD is not available for this shader version yet.", PackSelectionTransaction.failureMessage(error));
        assertEquals("Unknown.zip", backend.selected);
        assertEquals(Map.of("QUALITY", "high"), backend.queue);
        assertTrue(sidecars().isEmpty());
    }
}
