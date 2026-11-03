package com.pp.shaderlod.packs;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real normal producer: renderer/proof requests must not buy an unused generated shader graph. */
class NativeCpuGraphRoutingTest {
    @TempDir Path directory;
    @TempDir Path flagsDirectory;
    private Object savedFlags;

    @BeforeEach void isolateFlags() throws Exception {
        var state = OptimizationConfig.class.getDeclaredMethod("state");
        state.setAccessible(true);
        savedFlags = state.invoke(null);
        OptimizationConfig.load(flagsDirectory.resolve("requests.properties"));
        OptimizationConfig.replace(cpuBundle());
    }

    @AfterEach void restoreFlags() throws Exception {
        if(savedFlags == null)return;
        var restore = OptimizationConfig.class.getDeclaredMethod("restoreState", savedFlags.getClass());
        restore.setAccessible(true);
        restore.invoke(null, savedFlags);
    }

    private static OptimizationSnapshot cpuBundle() {
        var result = OptimizationSnapshot.noneRequested();
        for(var id:java.util.List.of(OptimizationId.QUAD_COMMAND_COMPACTION,
                OptimizationId.INDEX_SCALAR_CACHE, OptimizationId.SHADOW_ADMISSION_CACHE,
                OptimizationId.SHADOW_POSITIVE_VERTEX, OptimizationId.SHADOW_VISIBILITY_LISTS,
                OptimizationId.SHADOW_FRUSTUM_COHERENCE, OptimizationId.BINDING_CERTIFICATE_CACHE,
                OptimizationId.BINDING_DIGEST_STREAMING))result=result.with(id,true);
        return result;
    }

    private PackSelectionService.Result apply(String selected, boolean enabled, LodPreset preset) {
        return new PackSelectionService().apply(directory, selected, enabled, preset, Map.of(), true);
    }

    @Test void normalProducerKeepsNativeThenRestoresItAfterShaderProfile() throws Exception {
        Path original = directory.resolve("Bsl.zip");
        Files.copy(Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip"), original);
        byte[] archive = Files.readAllBytes(original);
        String preferences = "# chosen native AA\r\nTAA=true\r\nFXAA=true\r\nTAA_MODE=0\r\n";
        Path sidecar = directory.resolve("Bsl.zip.txt");
        Files.writeString(sidecar, preferences);

        var nativeOnly = apply("Bsl.zip", true, LodPreset.PERFORMANCE);
        assertTrue(nativeOnly.accepted(), nativeOnly.detail());
        assertEquals("Bsl.zip", nativeOnly.packFile());
        assertFalse(nativeOnly.generatedArchive());
        assertArrayEquals(archive, Files.readAllBytes(original));
        assertEquals(preferences, Files.readString(sidecar));
        try(var files=Files.list(directory)) {assertEquals(2,files.count(),"No unused generated archive/state");}

        OptimizationConfig.replace(cpuBundle().with(OptimizationId.AO_BUDGET, true));
        var shader = apply("Bsl.zip", true, LodPreset.PERFORMANCE);
        assertTrue(shader.accepted(), shader.detail());
        assertNotEquals("Bsl.zip", shader.packFile(), "An actual shader request still needs its adapter");
        Path statePath;
        try(var zip=new ZipFile(directory.resolve(shader.packFile()).toFile());
                var input=zip.getInputStream(zip.getEntry("shaderlod-selection.json"))) {
            var marker=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            statePath=directory.resolve(marker.get("stateFile").getAsString());
        }
        var generatedOptions = new Properties();
        try(var input=Files.newInputStream(directory.resolve(shader.packFile()+".txt"))) {generatedOptions.load(input);}
        assertEquals("true", generatedOptions.getProperty("TAA"));
        assertEquals("true", generatedOptions.getProperty("FXAA"));
        assertEquals("0", generatedOptions.getProperty("TAA_MODE"));

        OptimizationConfig.replace(cpuBundle());
        var restored = apply(shader.packFile(), true, LodPreset.PERFORMANCE);
        assertTrue(restored.accepted(), restored.detail());
        assertEquals("Bsl.zip", restored.packFile());
        assertEquals(preferences, Files.readString(sidecar));
        assertArrayEquals(archive, Files.readAllBytes(original));
        assertFalse(JsonParser.parseString(Files.readString(statePath)).getAsJsonObject().get("active").getAsBoolean());
        for(var control:java.util.List.of(apply("Bsl.zip", false, LodPreset.PERFORMANCE),
                apply("Bsl.zip", true, LodPreset.QUALITY))) {
            assertTrue(control.accepted(),control.detail());
            assertEquals("Bsl.zip",control.packFile());
        }
        assertEquals(preferences, Files.readString(sidecar));
        assertArrayEquals(archive, Files.readAllBytes(original));
    }
}
