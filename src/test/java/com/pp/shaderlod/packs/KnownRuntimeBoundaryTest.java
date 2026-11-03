package com.pp.shaderlod.packs;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.pp.shaderlod.integration.KnownRuntimeBoundary;
import com.pp.shaderlod.integration.KnownRuntimeBoundary.Facts;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnownRuntimeBoundaryTest {
    @TempDir Path directory;
    private static final Facts FAILED = new Facts("neoforge", "1.21.1", "1.8.12-snapshot+mc1.21.1-local",
            "Mac OS X", "27.0", "Apple", "Apple M3 Max", "4.1 Metal - 91.7");
    private final class Backend implements PackSelectionTransaction.Backend {
        String selected = "Renamed.zip";
        Facts facts = FAILED;
        int selects, saves, probes, effectiveReads;
        final Map<String, String> queue = new HashMap<>(Map.of("MY_QUALITY", "chosen"));
        public Path directory() { return directory; }
        public String selected() { return selected; }
        public Map<String, String> queue() { return queue; }
        public Map<String, String> effectiveOptions() { effectiveReads++; return Map.of(); }
        public Facts runtimeFacts() { probes++; return facts; }
        public void select(String name) { selects++; selected = name; }
        public void save() { saves++; }
    }
    @Test void exactTupleMatchesOnlyPinnedSourceGraphs() {
        String currentAdapter = SupportedPackCatalog.inventory().stream()
                .filter(pack -> pack.id().equals("astralex")).findFirst().orElseThrow().adapterSourceDigest();
        for (String pin : java.util.List.of(KnownRuntimeBoundary.ASTRALEX_ORIGINAL,
                KnownRuntimeBoundary.ASTRALEX_PRIVATE, currentAdapter))
            assertTrue(KnownRuntimeBoundary.refusal(pin, FAILED).orElseThrow().contains("crash"));
        assertTrue(KnownRuntimeBoundary.refusal("0".repeat(64), FAILED).isEmpty());
        assertTrue(KnownRuntimeBoundary.refusal("0".repeat(64), Facts.unknown()).isEmpty());
    }
    @Test void differentKnownTupleFieldsDoNotImplyTheObservedFailure() {
        String[] original = {FAILED.loader(), FAILED.minecraft(), FAILED.iris(), FAILED.os(), FAILED.osVersion(),
                FAILED.vendor(), FAILED.renderer(), FAILED.glVersion()};
        for (int field = 0; field < original.length; field++) {
            String[] changed = original.clone(); changed[field] = "different";
            assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL,
                    new Facts(changed[0], changed[1], changed[2], changed[3], changed[4], changed[5], changed[6], changed[7])).isEmpty(),
                    "Field " + field);
        }
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL,
                new Facts("forge", null, null, null, null, null, null, null)).isEmpty());
    }
    @Test void missingRuntimeFactsGiveQualifiedRefusalForPinnedSourcesOnly() {
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL, Facts.unknown())
                .orElseThrow().contains("unavailable"));
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_PRIVATE,
                new Facts(FAILED.loader(), FAILED.minecraft(), FAILED.iris(), FAILED.os(), FAILED.osVersion(),
                        FAILED.vendor(), null, FAILED.glVersion())).orElseThrow().contains("unavailable"));
    }
    private Backend fixture(String path) throws IOException {
        Path fixture = Path.of(path);
        assumeTrue(Files.isRegularFile(fixture), "Pinned local source required: " + path);
        var backend = new Backend(); Files.copy(fixture, directory.resolve(backend.selected));
        Files.writeString(directory.resolve(backend.selected + ".txt"), "# selected quality\nMY_QUALITY=high\n");
        return backend;
    }
    private Map<String, byte[]> snapshot() throws IOException {
        var files = new HashMap<String, byte[]>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) files.put(path.getFileName().toString(), Files.readAllBytes(path));
        }
        return files;
    }
    private void assertRefusedWithoutChanges(Backend backend, boolean enabled) throws IOException {
        var before = snapshot(); String selected = backend.selected; var queue = Map.copyOf(backend.queue);
        var transaction = new PackSelectionTransaction(backend);
        var failure = assertThrows(IOException.class, () -> transaction.prepare(enabled));
        assertTrue(KnownRuntimeBoundary.isRefusal(failure));
        var qualityFailure = assertThrows(IOException.class, () ->
                new PackSelectionTransaction(backend).prepare(enabled, com.pp.shaderlod.LodPreset.QUALITY));
        assertTrue(KnownRuntimeBoundary.isRefusal(qualityFailure));
        assertTrue(PackSelectionTransaction.failureMessage(failure).contains("Previous selection retained"));
        transaction.restore();
        var after = snapshot(); assertEquals(before.keySet(), after.keySet());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        assertEquals(selected, backend.selected); assertEquals(queue, backend.queue);
        assertEquals(0, backend.selects); assertEquals(0, backend.saves);
        assertEquals(0, backend.effectiveReads); // Guard precedes service preparation/option reads.
    }
    @Test void renamedOriginalRefusesOnAndOffBeforeAnyStateOrQueueChange() throws Exception {
        var backend = fixture("dependencies/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip");
        assertRefusedWithoutChanges(backend, true);
        assertRefusedWithoutChanges(backend, false); // Loader may reload for a changed mod preset.
    }
    @Test void privatePinnedSourceRefusesWithoutTrustingArchiveName() throws Exception {
        var backend = fixture("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-AstraLex-93.0-r1.zip");
        assertRefusedWithoutChanges(backend, true);
    }
    @Test void managedSelectionRefusesPendingEditsAndOffBeforeSidecarMutation() throws Exception {
        var backend = fixture("dependencies/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip");
        var result = new PackSelectionService().apply(directory, backend.selected, true);
        assertTrue(result.accepted(), result.detail()); backend.selected = result.packFile();
        assertRefusedWithoutChanges(backend, true);
        assertRefusedWithoutChanges(backend, false);
    }
    @Test void unavailableFactsRetainPinnedSelectionWithQualifiedReason() throws Exception {
        var backend = fixture("dependencies/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip");
        backend.facts = Facts.unknown(); assertRefusedWithoutChanges(backend, true);
    }
    @Test void otherHardwareCanStillGenerateWithoutClaimingRuntimeValidation() throws Exception {
        var backend = fixture("dependencies/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip");
        backend.facts = new Facts("neoforge", "1.21.1", FAILED.iris(), "Linux", "6", "NVIDIA", "RTX", "4.6");
        assertTrue(new PackSelectionTransaction(backend).prepare(true));
        assertNotEquals("Renamed.zip", backend.selected);
        assertTrue(Files.isRegularFile(directory.resolve(backend.selected)));
    }
    @Test void unrelatedPackNeverRequestsGlRuntimeFacts() throws Exception {
        var backend = fixture("dependencies/upstream/photon_v1.3b.zip");
        assertTrue(new PackSelectionTransaction(backend).prepare(true));
        assertEquals(0, backend.probes);
    }

    private static Facts solasFacts(Boolean clearAvailable) {
        return new Facts(FAILED.loader(), FAILED.minecraft(), FAILED.iris(), FAILED.os(), FAILED.osVersion(),
                FAILED.vendor(), FAILED.renderer(), FAILED.glVersion(), clearAvailable);
    }
    @Test void solasPinsRefuseOnlyTheObservedMissingFunctionTuple() {
        String currentAdapter = SupportedPackCatalog.inventory().stream()
                .filter(pack -> pack.id().equals("solas-shader")).findFirst().orElseThrow().adapterSourceDigest();
        for (String pin : java.util.List.of(KnownRuntimeBoundary.SOLAS_ORIGINAL,
                KnownRuntimeBoundary.SOLAS_PRIVATE, currentAdapter)) {
            assertTrue(KnownRuntimeBoundary.pinnedSource(pin));
            assertTrue(KnownRuntimeBoundary.refusal(pin, solasFacts(false)).orElseThrow().contains("glClearTexImage"));
            assertTrue(KnownRuntimeBoundary.refusal(pin, solasFacts(true)).isEmpty());
            assertTrue(KnownRuntimeBoundary.refusal(pin, solasFacts(null)).orElseThrow().contains("unavailable"));
            assertTrue(KnownRuntimeBoundary.refusal(pin, Facts.unknown()).orElseThrow().contains("unavailable"));
        }
        // The added capability does not weaken the independent Astra linker refusal.
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL, solasFacts(true)).isPresent());
    }
    @Test void solasDifferentLoaderGameIrisOsAndDriverRemainEligible() {
        String[] original = {FAILED.loader(), FAILED.minecraft(), FAILED.iris(), FAILED.os(), FAILED.osVersion(),
                FAILED.vendor(), FAILED.renderer(), FAILED.glVersion()};
        for (int field = 0; field < original.length; field++) {
            String[] changed = original.clone(); changed[field] = "different";
            assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.SOLAS_ORIGINAL,
                    new Facts(changed[0], changed[1], changed[2], changed[3], changed[4], changed[5], changed[6], changed[7], false)).isEmpty(),
                    "Field " + field);
        }
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.SOLAS_ORIGINAL,
                new Facts("fabric", null, null, null, null, null, null, null, false)).isEmpty());
    }
    @Test void renamedSolasOriginalAndPrivateRefuseOnOffWithoutFileOrQueueChanges() throws Exception {
        var original = fixture("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip");
        original.facts = solasFacts(false);
        assertRefusedWithoutChanges(original, true);
        assertRefusedWithoutChanges(original, false);
        Files.delete(directory.resolve(original.selected));
        var adapted = fixture("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-Solas-3.7b-r3.zip");
        adapted.facts = solasFacts(false);
        assertRefusedWithoutChanges(adapted, true);
        assertRefusedWithoutChanges(adapted, false);
    }
    @Test void managedSolasRefusesPendingEditsAndOffBeforeMutatingState() throws Exception {
        var backend = fixture("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip");
        var result = new PackSelectionService().apply(directory, backend.selected, true);
        assertTrue(result.accepted(), result.detail()); backend.selected = result.packFile();
        backend.facts = solasFacts(false);
        assertRefusedWithoutChanges(backend, true);
        assertRefusedWithoutChanges(backend, false);
    }
    @Test void missingSolasCapabilityRetainsSelectionWithQualifiedReason() throws Exception {
        var backend = fixture("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip");
        backend.facts = solasFacts(null);
        assertRefusedWithoutChanges(backend, true);
    }
    @Test void availableSolasClearFunctionCanGenerateAndRestoreNativeOptions() throws Exception {
        var backend = fixture("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip");
        backend.facts = solasFacts(true);
        backend.queue.clear();
        byte[] original = Files.readAllBytes(directory.resolve(backend.selected));
        byte[] options = Files.readAllBytes(directory.resolve(backend.selected + ".txt"));
        assertTrue(new PackSelectionTransaction(backend).prepare(true));
        assertNotEquals("Renamed.zip", backend.selected);
        assertTrue(new PackSelectionTransaction(backend).prepare(false));
        assertEquals("Renamed.zip", backend.selected);
        assertArrayEquals(original, Files.readAllBytes(directory.resolve(backend.selected)));
        assertArrayEquals(options, Files.readAllBytes(directory.resolve(backend.selected + ".txt")));
    }
}
