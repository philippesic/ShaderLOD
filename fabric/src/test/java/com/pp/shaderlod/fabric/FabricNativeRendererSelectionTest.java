package com.pp.shaderlod.fabric;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FabricNativeRendererSelectionTest {
    @TempDir Path directory;
    private final class Backend implements PackSelectionTransaction.Backend {
        String selected;
        final Map<String,String> queue = new HashMap<>();
        Backend(String selected) { this.selected = selected; }
        public Path directory() { return directory; }
        public String selected() { return selected; }
        public Map<String,String> queue() { return queue; }
        public Map<String,String> effectiveOptions() { return Map.of(); }
        public void select(String name) { selected = name; }
        public void save() {}
    }
    @Test void acceptedFamiliesKeepNativeGraphAcrossApplyOffQualityAndFailedApply() throws Exception {
        for (String original : List.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip",
                "dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip")) {
            String selected = Path.of(original).getFileName().toString();
            Path archive = directory.resolve(selected); Files.copy(Path.of(original), archive);
            byte[] sourceBefore = Files.readAllBytes(archive);
            Path sidecar = directory.resolve(selected + ".txt");
            Files.writeString(sidecar, "# personal native options\r\nTAA=false\r\n");
            byte[] optionsBefore = Files.readAllBytes(sidecar);
            FabricLodConfig.load(directory.resolve("settings.json"));
            var backend = new Backend(selected);
            var reloads = new AtomicInteger();
            for (var preset : List.of(LodPreset.QUALITY, LodPreset.PERFORMANCE)) {
                assertTrue(FabricLodSettings.apply(true, preset, () -> new PackSelectionTransaction(backend), () -> {
                    assertTrue(MakeUpQuadLifecycle.suspended()); reloads.incrementAndGet();
                }, () -> false));
                assertFalse(MakeUpQuadLifecycle.suspended()); assertEquals(selected, backend.selected);
            }
            assertTrue(FabricLodSettings.apply(false, LodPreset.PERFORMANCE,
                () -> new PackSelectionTransaction(backend), () -> assertTrue(MakeUpQuadLifecycle.suspended()), () -> false));
            var previous = FabricLodConfig.snapshot(); var attempts = new AtomicInteger();
            assertFalse(FabricLodSettings.apply(true, LodPreset.PERFORMANCE, () -> new PackSelectionTransaction(backend), () -> {
                assertTrue(MakeUpQuadLifecycle.suspended());
                if (attempts.incrementAndGet() == 1) throw new java.io.IOException("shader compile refusal");
                assertEquals(previous, FabricLodConfig.snapshot());
            }, () -> false));
            assertEquals(2, attempts.get()); assertEquals(previous, FabricLodConfig.snapshot());
            assertFalse(MakeUpQuadLifecycle.suspended()); assertEquals(selected, backend.selected);
            assertArrayEquals(sourceBefore, Files.readAllBytes(archive));
            assertArrayEquals(optionsBefore, Files.readAllBytes(sidecar));
        }
    }
}
