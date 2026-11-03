package com.pp.shaderlod.packs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupportedPackCatalogTest {
    @Test void inventorySeparatesSourceIntegrationFromRuntimeSupport() {
        var packs = SupportedPackCatalog.inventory();
        assertEquals(9, packs.size());
        assertEquals(9, packs.stream().map(SupportedPackCatalog.Pack::id).distinct().count());
        assertEquals(9, packs.stream().filter(SupportedPackCatalog.Pack::integrated).count());
        assertTrue(packs.stream().noneMatch(SupportedPackCatalog.Pack::supported));
        assertTrue(packs.stream().noneMatch(SupportedPackCatalog.Pack::runtimeValidated));
        assertThrows(UnsupportedOperationException.class, () -> packs.clear());
    }

    @Test void filenamesAndClaimsDoNotRecognizeUnknownSources() {
        assertTrue(SupportedPackCatalog.recognize(Map.of()).isEmpty());
        assertTrue(SupportedPackCatalog.recognize(Map.of("ComplementaryReimagined_r5.9.3.zip", "claim")).isEmpty());
        assertTrue(SupportedPackCatalog.recognize(Map.of("shaders/lib/common.glsl", "0".repeat(64))).isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> SupportedPackCatalog.recognize(Map.of("../shaders/a.glsl", "0".repeat(64))));
        assertThrows(IllegalArgumentException.class,
            () -> SupportedPackCatalog.recognize(Map.of("shaders/a.glsl", "invalid")));
    }

    @Test void sourceIdentityIgnoresPackagingButRejectsExecutableChanges() {
        Map<String,String> files = new HashMap<>();
        files.put("shaders/a.glsl", "a".repeat(64));
        String before = SupportedPackCatalog.shaderSourceDigest(files);
        files.put("README.md", "changed attribution");
        files.put("shaders/lang/en_us.lang", "changed translation");
        assertEquals(before, SupportedPackCatalog.shaderSourceDigest(files));
        files.put("shaders/a.glsl", "b".repeat(64));
        assertNotEquals(before, SupportedPackCatalog.shaderSourceDigest(files));
        String sourceBeforeProperties = SupportedPackCatalog.shaderSourceDigest(files);
        files.put("shaders/block.properties", "c".repeat(64));
        assertNotEquals(sourceBeforeProperties, SupportedPackCatalog.shaderSourceDigest(files));
        for (String extension : new String[]{".gsh", ".csh", ".tcs", ".tes", ".comp", ".geom"}) {
            String stageBefore = SupportedPackCatalog.shaderSourceDigest(files);
            files.put("shaders/newStage" + extension, "d".repeat(64));
            assertNotEquals(stageBefore, SupportedPackCatalog.shaderSourceDigest(files));
        }
        files.put("shaders/shaders.properties", "c".repeat(64));
        assertNotEquals(before, SupportedPackCatalog.shaderSourceDigest(files));
    }

    @Test void availablePinnedOriginalAndAssembledSourcesRecognize() throws Exception {
        Map<String,String> paths = Map.ofEntries(
            Map.entry("dependencies/upstream/photon_v1.3b.zip", "photon"),
            Map.entry("build/live-pixel-budget-portable-enabled.zip", "photon"),
            Map.entry("dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip", "complementary-reimagined"),
            Map.entry("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-Aurora-reimagined-r2.zip", "complementary-reimagined"),
            Map.entry("dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip", "complementary-unbound"),
            Map.entry("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-Aurora-unbound-r2.zip", "complementary-unbound"),
            Map.entry("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip", "makeup-ultra-fast-shaders"),
            Map.entry("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-makeup-ultra-fast-shaders-r2.zip", "makeup-ultra-fast-shaders"),
            Map.entry("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip", "bsl-shaders"),
            Map.entry("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-BSL-10.1.8-r2.zip", "bsl-shaders"),
            Map.entry("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip", "solas-shader"),
            Map.entry("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-Solas-3.7b-r3.zip", "solas-shader"),
            Map.entry("dependencies/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip", "astralex"),
            Map.entry("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-AstraLex-93.0-r1.zip", "astralex"),
            Map.entry("dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip", "bliss-shader"),
            Map.entry("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-Bliss-2.1.2-r1.zip", "bliss-shader"),
            Map.entry("dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip", "sildurs-vibrant-shaders"),
            Map.entry("dependencies/upstream/pack-expansion/generated/ShaderLOD-Private-Sildur-Vibrant-2.02-Extreme-r1.zip", "sildurs-vibrant-shaders"));
        for (var entry : paths.entrySet()) {
            Path path = Path.of(entry.getKey());
            org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(path), "Local pinned fixture required: " + path);
            Map<String,String> hashes = new HashMap<>();
            try (ZipFile archive = new ZipFile(path.toFile())) {
                var entries = archive.entries();
                while (entries.hasMoreElements()) {
                    var member = entries.nextElement();
                    if (member.isDirectory()) continue;
                    hashes.put(member.getName(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(archive.getInputStream(member).readAllBytes())));
                }
            }
            var result = SupportedPackCatalog.recognize(hashes).orElseThrow();
            assertEquals(entry.getValue(), result.pack().id());
            assertEquals(entry.getKey().contains("generated") || entry.getKey().startsWith("build/") ? SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER
                : SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL, result.stage());
            var modifiedCompute = new HashMap<>(hashes);
            modifiedCompute.put("shaders/unrecognized_new_version.csh", "0".repeat(64));
            assertTrue(SupportedPackCatalog.recognize(modifiedCompute).isEmpty());
            var modifiedControls = new HashMap<>(hashes);
            modifiedControls.put("shaders/block.properties", "0".repeat(64));
            assertTrue(SupportedPackCatalog.recognize(modifiedControls).isEmpty());
        }
    }
}
