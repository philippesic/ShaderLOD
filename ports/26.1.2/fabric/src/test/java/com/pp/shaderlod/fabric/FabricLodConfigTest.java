package com.pp.shaderlod.fabric;

import com.pp.shaderlod.LodPreset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FabricLodConfigTest {
    @TempDir Path directory;
    @Test void offPreservesSelectedPresetAndDistancesAcrossReload() throws Exception {
        var path = directory.resolve("shaderlod-client.json");
        FabricLodConfig.load(path);
        FabricLodConfig.setPreset(LodPreset.QUALITY);
        FabricLodConfig.setEnabled(false);
        var expected = FabricLodConfig.snapshot();
        FabricLodConfig.setPreset(LodPreset.PERFORMANCE);
        Files.writeString(path, "{\"enabled\":false,\"preset\":\"QUALITY\",\"near\":32,\"far\":96,\"transition\":8,\"debug\":false}");
        FabricLodConfig.load(path);
        assertEquals(expected, FabricLodConfig.snapshot());
        assertEquals(0, expected.core().settings().tier(10000));
    }
    @Test void malformedPersonalConfigIsRetained() throws Exception {
        var path = directory.resolve("shaderlod-client.json");
        String malformed = "{\"enabled\":true,\"preset\":\"PERFORMANCE\",\"near\":96,\"far\":32,\"transition\":8,\"debug\":false}";
        Files.writeString(path, malformed);
        FabricLodConfig.load(path);
        assertEquals(FabricLodConfig.defaults(), FabricLodConfig.snapshot());
        assertEquals(malformed, Files.readString(path));
        FabricLodConfig.setEnabled(false);
        assertEquals(malformed, Files.readString(directory.resolve("shaderlod-client.json.bak")));
    }
    @Test void stablePresetOrdinalsMatchTheSharedUniformContract() {
        assertEquals(0, LodPreset.BALANCED.ordinal());
        assertEquals(1, LodPreset.PERFORMANCE.ordinal());
        assertEquals(2, LodPreset.AGGRESSIVE.ordinal());
        assertEquals(3, LodPreset.QUALITY.ordinal());
    }
}
