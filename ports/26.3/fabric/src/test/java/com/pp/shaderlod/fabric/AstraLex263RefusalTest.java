package com.pp.shaderlod.fabric;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.KnownRuntimeBoundary;
import com.pp.shaderlod.integration.KnownRuntimeBoundary.Facts;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AstraLex263RefusalTest {
    @TempDir Path directory;
    private static final Path ORIGINAL = Path.of("dependencies/upstream/pack-expansion/AstraLex-93.0-original-ascii.zip");
    private static final Facts FAILED = new Facts("fabric", "26.3", "1.11.7+mc26.3",
            "Mac OS X", "27.0", "Apple", "Apple M3 Max", "4.1 Metal - 91.7");

    @Test void independentlyReadOriginalSourceMatchesPinAndOnlyThatGraphIsBlocked() throws Exception {
        var source = PackSourceReader.read(ORIGINAL);
        assertEquals("70b6d8127a6733f0a19bd1d351b92709d6ee7496967ff49c4793d2c28c7a6c58", source.sourceSha256());
        assertEquals(KnownRuntimeBoundary.ASTRALEX_ORIGINAL, SupportedPackCatalog.shaderSourceDigest(source.memberSha256()));
        var detail = KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL, FAILED).orElseThrow();
        assertTrue(detail.contains("Fabric 26.3"));
        assertTrue(detail.contains("original shader"));
        assertFalse(detail.contains("LOD copy"));
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_PRIVATE, FAILED).isEmpty());
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.SOLAS_ORIGINAL, FAILED).isEmpty());
        assertTrue(KnownRuntimeBoundary.refusal("0".repeat(64), FAILED).isEmpty());
        // Linker failure is independent of the texture-clear function.
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL,
                new Facts(FAILED.loader(), FAILED.minecraft(), FAILED.iris(), FAILED.os(), FAILED.osVersion(),
                        FAILED.vendor(), FAILED.renderer(), FAILED.glVersion(), true)).isPresent());
    }

    @Test void everyIdentifiedDifferentTupleFactExcludesThisCrash() {
        String[] fields = fields(FAILED);
        for (int i = 0; i < fields.length; i++) {
            var neighbor = fields.clone(); neighbor[i] = "different";
            assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL, facts(neighbor)).isEmpty(), "Field " + i);
        }
        for (String[] neighbor : List.of(
                new String[]{"neoforge", "26.3", "1.11.7+mc26.3", "Mac OS X", "27.0", "Apple", "Apple M3 Max", "4.1 Metal - 91.7"},
                new String[]{"fabric", "26.2", "1.11.4+mc26.2", "Mac OS X", "27.0", "Apple", "Apple M3 Max", "4.1 Metal - 91.7"},
                new String[]{"fabric", "26.3", "1.11.8+mc26.3", "Mac OS X", "27.0", "Apple", "Apple M3 Max", "4.1 Metal - 91.7"}))
            assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL, facts(neighbor)).isEmpty());
    }

    @Test void missingOrBlankMatchingFactsRemainQualifiedInsteadOfConfirmedFailure() {
        String[] fields = fields(FAILED);
        for (int i = 0; i < fields.length; i++) {
            for (String missing : new String[]{null, " "}) {
                var partial = fields.clone(); partial[i] = missing;
                var detail = KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL, facts(partial)).orElseThrow();
                assertTrue(detail.contains("unavailable"), "Field " + i);
                assertFalse(detail.contains("was blocked"));
            }
        }
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL,
                new Facts("fabric", null, null, null, null, null, null, null)).orElseThrow().contains("unavailable"));
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_PRIVATE,
                new Facts("fabric", null, null, null, null, null, null, null)).isEmpty());
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL,
                new Facts("forge", null, null, null, null, null, null, null)).isEmpty());
    }

    @Test void publicOnAndOffAllPresetsRefuseBeforeWritesGenerationReloadAndRecovery() throws Exception {
        var backend = fixture();
        for (boolean enabled : new boolean[]{true, false})
            for (LodPreset preset : LodPreset.values()) checkPublicRefusal(backend, enabled, preset, "was blocked");
    }

    @Test void publicUnknownFactsRefuseWithQualifiedMessageAndKeepPendingNativeEdits() throws Exception {
        var backend = fixture(); backend.facts = Facts.unknown();
        checkPublicRefusal(backend, true, LodPreset.QUALITY, "unavailable");
    }

    @Test void selectedOriginalWithPriorManagedStateStillRefusesBeforeEditingThatState() throws Exception {
        var backend = fixture();
        var generated = new PackSelectionService().apply(directory, backend.selected, true);
        assertTrue(generated.accepted(), generated.detail());
        // Retain the original selection; an already existing managed state must stay byte-identical.
        checkPublicRefusal(backend, true, LodPreset.PERFORMANCE, "was blocked");
        checkPublicRefusal(backend, false, LodPreset.QUALITY, "was blocked");
    }

    @Test void knownDifferentDriverStillAllowsGenerationWithoutClaimingRendering() throws Exception {
        var backend = fixture();
        backend.facts = new Facts("fabric", "26.3", FAILED.iris(), "Linux", "6", "NVIDIA", "RTX", "4.6");
        assertTrue(new PackSelectionTransaction(backend).prepare(true));
        assertNotEquals("Renamed-original.zip", backend.selected);
        assertTrue(Files.isRegularFile(directory.resolve(backend.selected)));
    }

    private final class Backend implements PackSelectionTransaction.Backend {
        String selected = "Renamed-original.zip";
        Facts facts = FAILED;
        final Map<String, String> queue = new HashMap<>(Map.of("TAA", "false", "MY_QUALITY", "chosen"));
        int selects, saves, effectiveReads, probes;
        public Path directory() { return directory; }
        public String selected() { return selected; }
        public Map<String, String> queue() { return queue; }
        public Map<String, String> effectiveOptions() { effectiveReads++; return Map.of(); }
        public Facts runtimeFacts() { probes++; return facts; }
        public void select(String name) { selects++; selected = name; }
        public void save() { saves++; }
    }
    private Backend fixture() throws Exception {
        var backend = new Backend(); Files.copy(ORIGINAL, directory.resolve(backend.selected));
        Files.writeString(directory.resolve(backend.selected + ".txt"), "# native quality\nMY_QUALITY=high\n");
        FabricLodConfig.load(directory.resolve("shaderlod-client.json"));
        return backend;
    }
    private void checkPublicRefusal(Backend backend, boolean enabled, LodPreset preset, String reason) throws Exception {
        var before = snapshot(); var config = FabricLodConfig.snapshot();
        var queue = Map.copyOf(backend.queue); String selected = backend.selected;
        var oldPreflight = FabricLodSettings.applyPreflight; var oldFactory = FabricLodSettings.selectionFactory;
        var oldReload = FabricLodSettings.applyReload; var oldFallback = FabricLodSettings.applyFallback;
        var creates = new AtomicInteger(); var reloads = new AtomicInteger(); var fallbacks = new AtomicInteger();
        int previousProbes = backend.probes;
        try {
            FabricLodSettings.applyPreflight = () -> {}; // Explicit fixture: no real GL context is manufactured.
            FabricLodSettings.selectionFactory = () -> { creates.incrementAndGet(); return new PackSelectionTransaction(backend); };
            FabricLodSettings.applyReload = reloads::incrementAndGet;
            FabricLodSettings.applyFallback = () -> { fallbacks.incrementAndGet(); return false; };
            assertFalse(FabricLodSettings.apply(enabled, preset));
            assertTrue(FabricLodSettings.failureMessage().contains(reason), FabricLodSettings.failureMessage());
            assertEquals(1, creates.get(), "Read-only transaction construction precedes source-identity refusal");
            assertEquals(previousProbes + 1, backend.probes);
            assertEquals(0, reloads.get(), "Neither requested nor rollback reload may run");
            assertEquals(0, fallbacks.get()); assertEquals(0, backend.selects); assertEquals(0, backend.saves);
            assertEquals(0, backend.effectiveReads); assertEquals(selected, backend.selected);
            assertEquals(queue, backend.queue); assertEquals(config, FabricLodConfig.snapshot());
            var after = snapshot(); assertEquals(before.keySet(), after.keySet());
            before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
        } finally {
            FabricLodSettings.applyPreflight = oldPreflight; FabricLodSettings.selectionFactory = oldFactory;
            FabricLodSettings.applyReload = oldReload; FabricLodSettings.applyFallback = oldFallback;
        }
    }
    private Map<String, byte[]> snapshot() throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) files.put(path.getFileName().toString(), Files.readAllBytes(path));
        }
        return files;
    }
    private static String[] fields(Facts f) { return new String[]{f.loader(), f.minecraft(), f.iris(), f.os(), f.osVersion(), f.vendor(), f.renderer(), f.glVersion()}; }
    private static Facts facts(String[] f) { return new Facts(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7]); }
}
