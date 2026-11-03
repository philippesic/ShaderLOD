package com.pp.shaderlod.fabric;

import com.pp.shaderlod.LodPreset;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FabricLodSettingsTest {
    @TempDir Path directory;
    @Test void applyReloadsOnceAndUnchangedApplyDoesNotReload() {
        FabricLodConfig.load(directory.resolve("settings.json"));
        var reloads = new AtomicInteger();
        assertTrue(FabricLodSettings.apply(false, LodPreset.QUALITY, reloads::incrementAndGet, () -> false));
        assertEquals(1, reloads.get());
        assertTrue(FabricLodSettings.apply(false, LodPreset.QUALITY, reloads::incrementAndGet, () -> false));
        assertEquals(1, reloads.get());
    }
    @Test void thrownReloadRestoresPriorConfigBeforeRecoveryReload() {
        var path = directory.resolve("settings.json");
        FabricLodConfig.load(path);
        var before = FabricLodConfig.snapshot();
        var reloads = new AtomicInteger();
        assertFalse(FabricLodSettings.apply(false, LodPreset.QUALITY, () -> {
            if (reloads.incrementAndGet() == 1) throw new IOException("Simulated shader compile failure");
            assertEquals(before, FabricLodConfig.snapshot());
        }, () -> false));
        assertEquals(2, reloads.get());
        FabricLodConfig.load(path);
        assertEquals(before, FabricLodConfig.snapshot());
    }
    @Test void fallbackAlsoRollsBack() {
        FabricLodConfig.load(directory.resolve("settings.json"));
        var before = FabricLodConfig.snapshot();
        var reloads = new AtomicInteger();
        assertFalse(FabricLodSettings.apply(false, LodPreset.QUALITY, reloads::incrementAndGet,
                () -> reloads.get() == 1));
        assertEquals(before, FabricLodConfig.snapshot());
        assertEquals(2, reloads.get());
    }
}
