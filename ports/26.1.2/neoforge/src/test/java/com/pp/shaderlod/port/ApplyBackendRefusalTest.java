package com.pp.shaderlod;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.RendererBackend;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.integration.KnownRuntimeBoundary;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the production Apply implementation with bounded backend observation/transaction ports. */
class ApplyBackendRefusalTest {
    @TempDir Path directory;
    private Map<String,List<Byte>> files() throws Exception {
        var result = new TreeMap<String,List<Byte>>();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                var bytes = new ArrayList<Byte>(); for (byte b : Files.readAllBytes(path)) bytes.add(b);
                result.put(directory.relativize(path).toString(), bytes);
            }
        }
        return result;
    }
    @Test void unsupportedOrUnavailableObservationRefusesBeforeAnyTransactionOrMutation() throws Exception {
        Files.writeString(directory.resolve("settings.toml"), "enabled=true\npreset=PERFORMANCE\n");
        Files.write(directory.resolve("original.zip"), new byte[]{1,2,3,4});
        Files.writeString(directory.resolve("original.zip.txt"), "TAA=false\n");
        var before = files();
        for (String name : Arrays.asList("Vulkan", "Metal", "Unknown", "", null)) {
            refused(() -> name, before);
        }
        refused(() -> { throw new IllegalStateException("No render-thread device"); }, before);
        refused(() -> { throw new NoClassDefFoundError("Missing graphics context"); }, before);
    }
    private void refused(Supplier<String> observation, Map<String,List<Byte>> before) throws Exception {
        var queue = new LinkedHashMap<>(Map.of("TAA", "false"));
        var reads = new AtomicInteger(); var selects = new AtomicInteger(); var saves = new AtomicInteger();
        var factories = new AtomicInteger(); var reloads = new AtomicInteger(); var fallbackReads = new AtomicInteger();
        PackSelectionTransaction.Backend backend = new PackSelectionTransaction.Backend() {
            public Path directory() { reads.incrementAndGet(); return directory; }
            public String selected() { reads.incrementAndGet(); return "original.zip"; }
            public Map<String,String> queue() { reads.incrementAndGet(); return queue; }
            public Map<String,String> effectiveOptions() { reads.incrementAndGet(); return Map.of(); }
            // A different Minecraft tuple must never bypass the separate backend preflight.
            public KnownRuntimeBoundary.Facts runtimeFacts() {
                reads.incrementAndGet(); return new KnownRuntimeBoundary.Facts("fabric", "26.1.2", "1.11.4", "Mac OS X", "27.0", "Apple", "Apple M3 Max", "4.1 Metal - 91.7");
            }
            public void select(String name) { selects.incrementAndGet(); queue.clear(); }
            public void save() { saves.incrementAndGet(); }
        };
        assertFalse(ShaderLodSettings.apply(false, LodPreset.QUALITY,
                () -> RendererBackend.requireApplySupported(observation),
                () -> { factories.incrementAndGet(); return new PackSelectionTransaction(backend); },
                reloads::incrementAndGet, () -> { fallbackReads.incrementAndGet(); return false; }));
        assertTrue(ShaderLodSettings.failureMessage().contains("Apply requires"));
        assertEquals(0, factories.get()); assertEquals(0, reads.get());
        assertEquals(0, selects.get()); assertEquals(0, saves.get());
        assertEquals(0, reloads.get()); assertEquals(0, fallbackReads.get());
        assertEquals(Map.of("TAA", "false"), queue);
        assertEquals(before, files()); // Includes absence of generated/archive/backup/tmp/config writes.
    }
    @Test void oldAstraCrashFactsDoNotCertifyABackend() {
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL, KnownRuntimeBoundary.Facts.unknown()).isPresent());
        assertTrue(KnownRuntimeBoundary.refusal(KnownRuntimeBoundary.ASTRALEX_ORIGINAL,
                new KnownRuntimeBoundary.Facts("fabric", "26.1.2", "1.11.4", null,null,null,null,null)).isEmpty());
    }
}
