package com.pp.shaderlod.packs;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackSelectionServiceRoundtripTest {
    @TempDir Path directory;
    private static final Path PHOTON = Path.of("dev/upstream/photon_v1.3b.zip");
    private static final Map<String, String> ORIGINALS = Map.ofEntries(
        Map.entry("photon", "dev/upstream/photon_v1.3b.zip"),
        Map.entry("complementary-reimagined", "dev/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"),
        Map.entry("complementary-unbound", "dev/upstream/pack-expansion/ComplementaryUnbound_r5.9.3.zip"),
        Map.entry("makeup-ultra-fast-shaders", "dev/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),
        Map.entry("bsl-shaders", "dev/upstream/other-pack-research/BSL_v10.1.8.zip"),
        Map.entry("solas-shader", "dev/upstream/pack-expansion/Solas Shader V3.7b.zip"),
        Map.entry("astralex", "dev/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip"),
        Map.entry("bliss-shader", "dev/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"),
        Map.entry("sildurs-vibrant-shaders", "dev/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"));
    private String install(String name) throws IOException {
        assumeTrue(Files.exists(PHOTON), "Local pinned Photon fixture required");
        Files.copy(PHOTON, directory.resolve(name)); return name;
    }
    private PackSelectionService.Result apply(String file, boolean enabled) {
        return new PackSelectionService().apply(directory, file, enabled);
    }
    private void sidecar(String pack, String value) throws IOException {
        Files.writeString(directory.resolve(pack + ".txt"), value, StandardCharsets.ISO_8859_1);
    }
    private Properties options(String file) throws IOException {
        Properties result = new Properties();
        try (var input = Files.newInputStream(directory.resolve(file + ".txt"))) { result.load(input); }
        return result;
    }
    private void assertAccepted(PackSelectionService.Result result) {
        assertTrue(result.accepted(), result::detail);
    }
    @Test void nativeGraphRoundtripPreservesOriginalBytesAndLatentChosenScale() throws Exception {
        String original = install("Photon.zip");
        String quality = "# My selected Ultra settings\r\nINFO=3\r\nTAAU=false\r\nTAAU_RENDER_SCALE=0.50\r\n";
        sidecar(original, quality);
        byte[] zip = Files.readAllBytes(directory.resolve(original));
        var on = apply(original, true); assertAccepted(on); assertTrue(on.generatedArchive());
        assertEquals("false", options(on.packFile()).getProperty("TAAU"));
        assertEquals("0.50", options(on.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertEquals("true", options(on.packFile()).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        assertArrayEquals(quality.getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(directory.resolve(original + ".txt")));
        var off = apply(on.packFile(), false); assertAccepted(off); assertEquals(original, off.packFile());
        assertArrayEquals(zip, Files.readAllBytes(directory.resolve(original)));
        assertArrayEquals(quality.getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(directory.resolve(original + ".txt")));
        var again = apply(off.packFile(), true); assertAccepted(again); assertEquals(on.packFile(), again.packFile());
        assertFalse(again.generatedArchive());
        assertEquals("0.50", options(again.packFile()).getProperty("TAAU_RENDER_SCALE"));
    }
    @Test void originallyUpscaledPreservesScaleAcrossRestartAndOff() throws Exception {
        String original = install("Photon.zip"); sidecar(original, "TAA=true\nTAAU=true\nTAAU_RENDER_SCALE=0.50\nINFO=2\n");
        var on = apply(original, true); assertAccepted(on);
        assertEquals("0.50", options(on.packFile()).getProperty("TAAU_RENDER_SCALE"));
        var restarted = apply(on.packFile(), true); assertEquals(PackSelectionService.Status.UNCHANGED, restarted.status());
        var off = apply(on.packFile(), false); assertAccepted(off);
        assertEquals("true", options(original).getProperty("TAAU"));
        assertEquals("0.50", options(original).getProperty("TAAU_RENDER_SCALE"));
    }
    @Test void absentSidecarRemainsAbsentOnOffAndBackupRecordsAbsence() throws Exception {
        String original = install("Photon.zip"); var on = apply(original, true); assertAccepted(on);
        assertNull(options(on.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertNull(options(on.packFile()).getProperty("TAAU"));
        assertEquals("true", options(on.packFile()).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        assertAccepted(apply(on.packFile(), false));
        assertFalse(Files.exists(directory.resolve(original + ".txt")));
        try (var paths = Files.list(directory)) {
            Path state = paths.filter(p -> p.getFileName().toString().startsWith(".shaderlod-selection-")).findFirst().orElseThrow();
            assertEquals("ABSENT", JsonParser.parseString(Files.readString(state)).getAsJsonObject().get("originalOptionsBackup").getAsString());
        }
    }
    @Test void disabledTaaAndTaauPreserveNativeGraphAcrossOnRestartOff() throws Exception {
        assertDisabledTaaRoundtrip("false");
    }
    @Test void disabledTaaWithLatentTaauPreservesChosenGraphAcrossOnRestartOff() throws Exception {
        assertDisabledTaaRoundtrip("true");
    }
    private void assertDisabledTaaRoundtrip(String taau) throws Exception {
        String original = install("Photon.zip");
        String chosen = "# My AA-off preset\r\nTAA=false\r\nTAAU=" + taau
            + "\r\nTAAU_RENDER_SCALE=0.67\r\nINFO=3\r\n";
        sidecar(original, chosen);
        byte[] zip = Files.readAllBytes(directory.resolve(original));
        var on = apply(original, true); assertAccepted(on);
        assertEquals("false", options(on.packFile()).getProperty("TAA"));
        assertEquals(taau, options(on.packFile()).getProperty("TAAU"));
        assertEquals("0.67", options(on.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertEquals("true", options(on.packFile()).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        Map<String, byte[]> before = snapshot();
        assertEquals(PackSelectionService.Status.UNCHANGED, apply(on.packFile(), true).status());
        assertSnapshot(before); // apply() constructs a new service, modelling a restart.
        var off = apply(on.packFile(), false); assertAccepted(off); assertEquals(original, off.packFile());
        assertArrayEquals(chosen.getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(directory.resolve(original + ".txt")));
        assertArrayEquals(zip, Files.readAllBytes(directory.resolve(original)));
        var again = apply(original, true); assertAccepted(again); assertFalse(again.generatedArchive());
        assertEquals(on.packFile(), again.packFile());
        assertEquals("false", options(again.packFile()).getProperty("TAA"));
        assertEquals(taau, options(again.packFile()).getProperty("TAAU"));
        assertEquals("0.67", options(again.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertEquals("true", options(again.packFile()).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        assertArrayEquals(zip, Files.readAllBytes(directory.resolve(original)));
    }
    @Test void effectiveAaOffSelectionOverridesSavedTaaWithoutEnablingReconstruction() throws Exception {
        String original = install("Photon.zip"); sidecar(original, "TAA=true\nINFO=2\n");
        byte[] zip = Files.readAllBytes(directory.resolve(original));
        var on = new PackSelectionService().apply(directory, original, true,
            Map.of("TAA", "false", "TAAU", "true", "TAAU_RENDER_SCALE", "0.50"));
        assertAccepted(on);
        assertEquals("false", options(on.packFile()).getProperty("TAA"));
        assertEquals("true", options(on.packFile()).getProperty("TAAU"));
        assertEquals("0.50", options(on.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertEquals("true", options(on.packFile()).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        assertEquals(PackSelectionService.Status.UNCHANGED, apply(on.packFile(), true).status());
        assertAccepted(apply(on.packFile(), false));
        assertEquals("false", options(original).getProperty("TAA"));
        assertEquals("true", options(original).getProperty("TAAU"));
        assertEquals("0.50", options(original).getProperty("TAAU_RENDER_SCALE"));
        assertNull(options(original).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        assertArrayEquals(zip, Files.readAllBytes(directory.resolve(original)));
    }
    @Test void restartMigratesLegacyOwnedTaaToRecordedAaOffBaseBeforeRecomputingBudget() throws Exception {
        String original = install("Photon.zip");
        String chosen = "# AA-off base\r\nTAA=false\r\nTAAU=false\r\nTAAU_RENDER_SCALE=0.67\r\nINFO=2\r\n";
        sidecar(original, chosen); byte[] zip = Files.readAllBytes(directory.resolve(original));
        var on = apply(original, true); assertAccepted(on);
        byte[] generated = Files.readAllBytes(directory.resolve(on.packFile()));
        Path state;
        try (var paths = Files.list(directory)) {
            state = paths.filter(path -> path.getFileName().toString().startsWith(".shaderlod-selection-")).findFirst().orElseThrow();
        }
        var record = JsonParser.parseString(Files.readString(state)).getAsJsonObject();
        var legacy = record.getAsJsonObject("appliedOverrides");
        legacy.addProperty("TAA", "true"); legacy.addProperty("TAAU", "true");
        legacy.addProperty("TAAU_RENDER_SCALE", "1.00"); legacy.addProperty("SHADERLOD_LIVE_PIXEL_BUDGET", "true");
        Files.writeString(state, record.toString());
        // Same ZIP/recipe/state identity as the old service; only its owned sidecar differs.
        sidecar(on.packFile(), "TAA=true\nTAAU=true\nTAAU_RENDER_SCALE=1.00\nINFO=3\nSHADERLOD_LIVE_PIXEL_BUDGET=true\n");
        var migrated = apply(on.packFile(), true); assertAccepted(migrated);
        assertEquals(PackSelectionService.Status.APPLIED, migrated.status());
        assertFalse(migrated.generatedArchive()); assertTrue(migrated.optionsChanged());
        assertEquals("false", options(on.packFile()).getProperty("TAA"));
        assertEquals("false", options(on.packFile()).getProperty("TAAU"));
        assertEquals("0.67", options(on.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertEquals("true", options(on.packFile()).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        assertEquals("3", options(on.packFile()).getProperty("INFO"));
        var newOwned = JsonParser.parseString(Files.readString(state)).getAsJsonObject().getAsJsonObject("appliedOverrides");
        assertEquals(1, newOwned.size()); assertEquals("true", newOwned.get("SHADERLOD_LIVE_PIXEL_BUDGET").getAsString());
        Map<String, byte[]> before = snapshot();
        assertEquals(PackSelectionService.Status.UNCHANGED, apply(on.packFile(), true).status()); assertSnapshot(before);
        assertAccepted(apply(on.packFile(), false));
        assertEquals("false", options(original).getProperty("TAA"));
        assertEquals("false", options(original).getProperty("TAAU"));
        assertEquals("0.67", options(original).getProperty("TAAU_RENDER_SCALE"));
        assertEquals("3", options(original).getProperty("INFO"));
        assertNull(options(original).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        assertArrayEquals(zip, Files.readAllBytes(directory.resolve(original)));
        assertArrayEquals(generated, Files.readAllBytes(directory.resolve(on.packFile())));
    }
    @Test void userNonOwnedAndOwnedOptionEditsSurviveOffAndNextOn() throws Exception {
        String original = install("Photon.zip"); sidecar(original, "INFO=2\nTAAU=false\nTAAU_RENDER_SCALE=0.75\n");
        var on = apply(original, true); assertAccepted(on);
        sidecar(on.packFile(), "INFO=3\nSHADOW_COLOR=false\nTAAU=true\nTAAU_RENDER_SCALE=0.60\nSHADERLOD_LIVE_PIXEL_BUDGET=true\n");
        assertAccepted(apply(on.packFile(), false));
        assertEquals("3", options(original).getProperty("INFO"));
        assertEquals("false", options(original).getProperty("SHADOW_COLOR"));
        assertEquals("true", options(original).getProperty("TAAU"));
        assertEquals("0.60", options(original).getProperty("TAAU_RENDER_SCALE"));
        var next = apply(original, true); assertAccepted(next);
        assertEquals("3", options(next.packFile()).getProperty("INFO"));
        assertEquals("0.60", options(next.packFile()).getProperty("TAAU_RENDER_SCALE"));
    }
    @Test void selectingOriginalWhileActiveRetainsAdapterEditsAcrossLaterOff() throws Exception {
        String original = install("Photon.zip"); sidecar(original, "INFO=2\n");
        var on = apply(original, true); assertAccepted(on);
        Files.writeString(directory.resolve(on.packFile() + ".txt"), "\nINFO=3\n", java.nio.file.StandardOpenOption.APPEND);
        var again = apply(original, true); assertAccepted(again);
        assertAccepted(apply(again.packFile(), false)); assertEquals("3", options(original).getProperty("INFO"));
    }
    @Test void explicitEffectiveOriginalOptionsPersistWithoutTreatingAdapterOverridesAsOriginal() throws Exception {
        String original = install("Photon.zip");
        var on = new PackSelectionService().apply(directory, original, true,
            Map.of("TAA", "true", "TAAU", "true", "TAAU_RENDER_SCALE", "0.67", "INFO", "3"));
        assertAccepted(on); assertEquals("0.67", options(on.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertAccepted(apply(on.packFile(), false)); assertEquals("3", options(original).getProperty("INFO"));
        assertEquals("true", options(original).getProperty("TAAU"));
    }
    @Test void conflictsFailBeforeChangingAnyFiles() throws Exception {
        String original = install("Photon.zip"); sidecar(original, "INFO=2\n");
        var on = apply(original, true); assertAccepted(on);
        sidecar(original, "INFO=1\n");
        Files.writeString(directory.resolve(on.packFile() + ".txt"), "\nINFO=3\n", java.nio.file.StandardOpenOption.APPEND);
        Map<String, byte[]> before = snapshot();
        assertEquals(PackSelectionService.Status.REFUSED, apply(on.packFile(), false).status()); assertSnapshot(before);
    }
    @Test void unknownAndOriginalOffAreCleanNoOps() throws Exception {
        writeZip(directory.resolve("unknown.zip"), Map.of("shaders/main.fsh", new byte[]{1, 2}));
        Map<String, byte[]> before = snapshot();
        assertEquals(PackSelectionService.Status.UNSUPPORTED, apply("unknown.zip", true).status()); assertSnapshot(before);
        String original = install("Photon.zip"); before = snapshot();
        assertEquals(PackSelectionService.Status.UNCHANGED, apply(original, false).status()); assertSnapshot(before);
    }
    @Test void changedOriginalOrGeneratedArchiveFailsUntouched() throws Exception {
        String original = install("Photon.zip"); var on = apply(original, true); assertAccepted(on);
        Files.write(directory.resolve(on.packFile()), new byte[]{42}, java.nio.file.StandardOpenOption.APPEND);
        Map<String, byte[]> before = snapshot();
        assertEquals(PackSelectionService.Status.REFUSED, apply(on.packFile(), false).status()); assertSnapshot(before);
        Files.copy(PHOTON, directory.resolve(original), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.write(directory.resolve(original), new byte[]{42}, java.nio.file.StandardOpenOption.APPEND);
        before = snapshot(); assertEquals(PackSelectionService.Status.REFUSED, apply(original, true).status()); assertSnapshot(before);
    }
    @Test void untrackedManualAdapterFailsSafe() throws Exception {
        assumeTrue(Files.exists(Path.of("build/live-pixel-budget-portable-enabled.zip")));
        Files.copy(Path.of("build/live-pixel-budget-portable-enabled.zip"), directory.resolve("manual.zip"));
        Map<String, byte[]> before = snapshot(); var result = apply("manual.zip", false);
        assertFalse(new PackSelectionService().apply(directory, "manual.zip", true,
                com.pp.shaderlod.LodPreset.QUALITY, Map.of()).accepted()); assertSnapshot(before);
        assertFalse(result.accepted()); assertSnapshot(before);
    }
    @Test void packSwitchDoesNotMixOriginalSelectionsOrGenerateDerivatives() throws Exception {
        String first = install("Photon-one.zip"), second = install("Photon-two.zip");
        sidecar(first, "INFO=1\nTAAU=false\n"); sidecar(second, "INFO=3\nTAAU=true\nTAAU_RENDER_SCALE=0.50\n");
        var a = apply(first, true); var b = apply(second, true); assertAccepted(a); assertAccepted(b);
        assertNotEquals(a.packFile(), b.packFile());
        assertNull(options(a.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertEquals("false", options(a.packFile()).getProperty("TAAU"));
        assertEquals("0.50", options(b.packFile()).getProperty("TAAU_RENDER_SCALE"));
        assertEquals(first, apply(a.packFile(), false).packFile()); assertEquals(second, apply(b.packFile(), false).packFile());
        assertEquals(a.packFile(), apply(first, true).packFile());
        try (var paths = Files.list(directory)) { assertEquals(4, paths.filter(p -> p.toString().endsWith(".zip")).count()); }
    }
    @Test void unsafeZipPathsAndCaseDuplicatesAreRejected() throws Exception {
        for (Map<String, byte[]> entries : java.util.List.of(Map.of("../escape", new byte[]{1}),
                Map.of("/escape", new byte[]{1}), Map.of("a\\b", new byte[]{1}),
                Map.of("shaders/A", new byte[]{1}, "shaders/a", new byte[]{2}))) {
            writeZip(directory.resolve("bad.zip"), entries); Map<String, byte[]> before = snapshot();
            assertEquals(PackSelectionService.Status.REFUSED, apply("bad.zip", true).status()); assertSnapshot(before);
        }
    }
    @Test void symlinkedSidecarAndInvalidScaleFailBeforeGeneration() throws Exception {
        String original = install("Photon.zip"); sidecar(original, "TAAU_RENDER_SCALE=NaN\n");
        Map<String, byte[]> before = snapshot(); assertEquals(PackSelectionService.Status.REFUSED, apply(original, true).status()); assertSnapshot(before);
        Files.delete(directory.resolve(original + ".txt")); Path target = directory.resolve("personal.txt"); Files.writeString(target, "INFO=3\n");
        Files.createSymbolicLink(directory.resolve(original + ".txt"), target);
        assertEquals(PackSelectionService.Status.REFUSED, apply(original, true).status()); assertEquals("INFO=3\n", Files.readString(target));
    }
    @Test void generatedArchiveRetainsAllOriginalMembersAndLicenseExactly() throws Exception {
        String original = install("Photon.zip"); var on = apply(original, true); assertAccepted(on);
        Map<String, byte[]> base = members(directory.resolve(original)), generated = members(directory.resolve(on.packFile()));
        var changed = new java.util.TreeSet<String>();
        for (var entry : base.entrySet()) {
            assertTrue(generated.containsKey(entry.getKey()));
            if (!Arrays.equals(entry.getValue(), generated.get(entry.getKey()))) changed.add(entry.getKey());
        }
        assertEquals(java.util.Set.of("shaders/settings.glsl", "shaders/include/global.glsl",
            "shaders/program/gbuffers_all_solid.fsh", "shaders/program/gbuffers_all_translucent.fsh",
            "shaders/program/gbuffers_damagedblock.fsh", "shaders/program/gbuffers_armor_glint.fsh",
            "shaders/include/sky/clouds/cumulus.glsl"), changed);
        assertArrayEquals(base.get("LICENSE"), generated.get("LICENSE"));
    }
    @Test void shaderAndRendererSelectionsPreserveQualityAndOriginalBytes() throws Exception {
        for (String source : java.util.List.of("dev/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip",
                "dev/upstream/pack-expansion/ComplementaryUnbound_r5.9.3.zip",
                "dev/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip")) {
            assumeTrue(Files.exists(Path.of(source)), source);
            String name = Path.of(source).getFileName().toString(); Files.copy(Path.of(source), directory.resolve(name));
            byte[] original = Files.readAllBytes(directory.resolve(name)); sidecar(name, "QUALITY=3\nCUSTOM_QUALITY=high\n");
            var on = apply(name, true); assertAccepted(on);
            assertEquals("1", options(on.packFile()).getProperty("SHADERLOD_REFINEMENT_ADAPTER"));
            assertEquals("high", options(on.packFile()).getProperty("CUSTOM_QUALITY"));
            assertAccepted(apply(on.packFile(), false)); assertEquals("high", options(name).getProperty("CUSTOM_QUALITY"));
            assertNull(options(name).getProperty("SHADERLOD_REFINEMENT_ADAPTER"));
            assertArrayEquals(original, Files.readAllBytes(directory.resolve(name)));
        }
    }
    @Test void commentEscapesAndContinuationSurviveNecessaryQualityEdits() throws Exception {
        String original = install("Photon.zip");
        String quality = "# My note\r\nINFO=2\r\nCUSTOM=hello\\\n  world\r\nESCAPED\\ KEY=value\r\n";
        sidecar(original, quality); var on = apply(original, true); assertAccepted(on);
        Files.writeString(directory.resolve(on.packFile() + ".txt"), "\nINFO=3\n", java.nio.file.StandardOpenOption.APPEND);
        assertAccepted(apply(on.packFile(), false));
        String restored = Files.readString(directory.resolve(original + ".txt"));
        assertTrue(restored.startsWith("# My note\r\n"));
        assertTrue(restored.contains("CUSTOM=hello\\\n  world\r\n"));
        assertEquals("helloworld", options(original).getProperty("CUSTOM"));
        assertEquals("value", options(original).getProperty("ESCAPED KEY"));
        assertEquals("3", options(original).getProperty("INFO"));
    }
    @Test void existingUntrackedGeneratedArchiveOrOptionsNeverClobbered() throws Exception {
        String original = install("Photon.zip"); var on = apply(original, true); assertAccepted(on);
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().startsWith(".shaderlod-selection-")).toList()) Files.delete(path);
        }
        Map<String, byte[]> before = snapshot();
        assertEquals(PackSelectionService.Status.REFUSED, apply(original, true).status()); assertSnapshot(before);
        Files.delete(directory.resolve(on.packFile())); before = snapshot();
        assertEquals(PackSelectionService.Status.REFUSED, apply(original, true).status()); assertSnapshot(before);
    }
    @Test void allNinePackSelectionsRoundtripChosenPreset() throws Exception {
        for (var fixture : ORIGINALS.entrySet()) {
            Path source = Path.of(fixture.getValue());
            assertTrue(Files.isRegularFile(source), "Local pinned fixture required: " + source);
            String original = fixture.getKey() + ".zip";
            Files.copy(source, directory.resolve(original));
            byte[] originalZip = Files.readAllBytes(directory.resolve(original));
            String chosen = "# selected quality must survive\r\nCUSTOM_QUALITY=high\r\nQUALITY=3\r\n";
            sidecar(original, chosen);
            Map<String, byte[]> before = snapshot();
            assertEquals(PackSelectionService.Status.UNCHANGED, apply(original, false).status());
            assertSnapshot(before);
            var on = apply(original, true); assertAccepted(on);
            assertEquals(fixture.getKey(), on.adapterId());
            assertEquals("high", options(on.packFile()).getProperty("CUSTOM_QUALITY"));
            assertEquals("3", options(on.packFile()).getProperty("QUALITY"));
            if (!fixture.getKey().equals("photon")) {
                String key = effectRecipe(fixture.getKey()) ? "SHADERLOD_EFFECT_ADAPTER" : "SHADERLOD_REFINEMENT_ADAPTER";
                assertEquals("1", options(on.packFile()).getProperty(key));
                assertNull(options(on.packFile()).getProperty(effectRecipe(fixture.getKey())
                    ? "SHADERLOD_REFINEMENT_ADAPTER" : "SHADERLOD_EFFECT_ADAPTER"));
            }
            var generated = members(directory.resolve(on.packFile()));
            var recognition = SupportedPackCatalog.recognize(memberHashes(generated)).orElseThrow();
            assertEquals(fixture.getKey(), recognition.pack().id());
            assertEquals(SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER, recognition.stage());
            assertFalse(recognition.pack().runtimeValidated());
            var base = members(source);
            for (var member : base.entrySet()) assertTrue(generated.containsKey(member.getKey()), member.getKey());
            if (!fixture.getKey().equals("photon")) {
                var manifest = JsonParser.parseString(Files.readString(Path.of("patches/adapters/" + fixture.getKey() + ".json"))).getAsJsonObject();
                var changed = new java.util.HashSet<String>();
                for (var patch : manifest.getAsJsonArray("patches")) changed.add(patch.getAsJsonObject().get("path").getAsString());
                for (var member : base.entrySet()) if (!changed.contains(member.getKey()))
                    assertArrayEquals(member.getValue(), generated.get(member.getKey()), member.getKey());
                for (var addition : manifest.getAsJsonObject("additions").entrySet()) {
                    String maintained = addition.getValue().getAsJsonObject().get("path").getAsString();
                    assertArrayEquals(Files.readAllBytes(Path.of(maintained)), generated.get(addition.getKey()), maintained);
                }
            }
            before = snapshot();
            assertEquals(PackSelectionService.Status.UNCHANGED, apply(on.packFile(), true).status());
            assertSnapshot(before);
            var off = apply(on.packFile(), false); assertAccepted(off); assertEquals(original, off.packFile());
            assertArrayEquals(chosen.getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(directory.resolve(original + ".txt")));
            assertArrayEquals(originalZip, Files.readAllBytes(directory.resolve(original)));
            var next = apply(original, true); assertAccepted(next);
            assertEquals(on.packFile(), next.packFile()); assertFalse(next.generatedArchive());
        }
    }
    @Test void actualRendererAvailabilityRetainsNativeOnlyForPerformance() throws Exception {
        for (String id : java.util.List.of("photon", "complementary-reimagined", "complementary-unbound",
                "makeup-ultra-fast-shaders", "sildurs-vibrant-shaders", "bsl-shaders", "bliss-shader")) {
            Path source = Path.of(ORIGINALS.get(id)); assertTrue(Files.isRegularFile(source), source.toString());
            String original = id + ".zip"; Files.copy(source, directory.resolve(original));
            sidecar(original, "TAA=true\nTAAU=false\nTAAU_RENDER_SCALE=0.67\nCUSTOM_QUALITY=high\n");
            var before = snapshot(); var service = new PackSelectionService();
            var nativeOn = service.apply(directory, original, true, LodPreset.PERFORMANCE, Map.of(), true);
            assertAccepted(nativeOn); assertEquals(original, nativeOn.packFile());
            assertEquals(PackSelectionService.Status.UNCHANGED, nativeOn.status()); assertSnapshot(before);
            var balanced = service.apply(directory, original, true, LodPreset.BALANCED, Map.of(), true);
            assertAccepted(balanced); assertNotEquals(original, balanced.packFile()); assertTrue(balanced.generatedArchive());
            // Returning to an available Performance route restores the original graph and chosen AA.
            var restored = service.apply(directory, balanced.packFile(), true, LodPreset.PERFORMANCE, Map.of(), true);
            assertAccepted(restored); assertEquals(original, restored.packFile());
            assertEquals("false", options(original).getProperty("TAAU"));
            assertEquals("0.67", options(original).getProperty("TAAU_RENDER_SCALE"));
        }
    }
    @Test void allNineQualityReturnsNativeGraphAndKeepsNonOwnedEdits() throws Exception {
        for (var fixture : ORIGINALS.entrySet()) {
            Path source = Path.of(fixture.getValue()); assertTrue(Files.isRegularFile(source), source.toString());
            String original = fixture.getKey() + ".zip"; Files.copy(source, directory.resolve(original));
            String chosen = "TAA=true\nTAAU=false\nTAAU_RENDER_SCALE=0.67\nCUSTOM_QUALITY=high\n";
            sidecar(original, chosen); var service = new PackSelectionService(); var before = snapshot();
            var quality = service.apply(directory, original, true, LodPreset.QUALITY, Map.of(), false);
            assertAccepted(quality); assertEquals(original, quality.packFile()); assertSnapshot(before);
            var on = service.apply(directory, original, true, LodPreset.PERFORMANCE, Map.of(), false);
            assertAccepted(on); assertNotEquals(original, on.packFile());
            var edited = options(on.packFile()); edited.setProperty("CUSTOM_QUALITY", "user-edit");
            try (var output = Files.newOutputStream(directory.resolve(on.packFile() + ".txt"))) { edited.store(output, null); }
            quality = service.apply(directory, on.packFile(), true, LodPreset.QUALITY, Map.of(), false);
            assertAccepted(quality); assertEquals(original, quality.packFile());
            assertEquals("user-edit", options(original).getProperty("CUSTOM_QUALITY"));
            assertEquals("false", options(original).getProperty("TAAU"));
            assertEquals("0.67", options(original).getProperty("TAAU_RENDER_SCALE"));
            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(directory.resolve(original)));
            var off = service.apply(directory, original, false, LodPreset.QUALITY, Map.of(), false);
            assertAccepted(off); assertEquals(original, off.packFile());
            assertEquals("user-edit", options(original).getProperty("CUSTOM_QUALITY"));
        }
    }
    @Test void volumetricDefaultsAndExplicitDisabledEffectsRemainSelected() throws Exception {
        for (String id : java.util.List.of("bliss-shader")) {
            Path source = Path.of(ORIGINALS.get(id)); assumeTrue(Files.exists(source), source.toString());
            String original = id + ".zip"; Files.copy(source, directory.resolve(original));
            var on = apply(original, true); assertAccepted(on);
            Properties defaults = options(on.packFile());
            assertEquals(1, defaults.size()); assertEquals("1", defaults.getProperty("SHADERLOD_EFFECT_ADAPTER"));
            assertAccepted(apply(on.packFile(), false));
            assertFalse(Files.exists(directory.resolve(original + ".txt")));
            String disabled = "# keep fog and lighting disabled\nVolumetric_Lighting=false\nTOGGLE_VL_FOG=false\nVL_SAMPLES=4\n";
            sidecar(original, disabled);
            on = apply(original, true); assertAccepted(on);
            assertEquals("false", options(on.packFile()).getProperty("Volumetric_Lighting"));
            assertEquals("false", options(on.packFile()).getProperty("TOGGLE_VL_FOG"));
            assertEquals("4", options(on.packFile()).getProperty("VL_SAMPLES"));
            assertAccepted(apply(on.packFile(), false));
            assertArrayEquals(disabled.getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(directory.resolve(original + ".txt")));
        }
    }
    @Test void expandedRecipesRejectTamperedArchivesWithoutChangingState() throws Exception {
        for (String id : java.util.List.of("solas-shader", "astralex", "bliss-shader")) {
            Path source = Path.of(ORIGINALS.get(id)); assumeTrue(Files.exists(source), source.toString());
            String original = id + ".zip"; Files.copy(source, directory.resolve(original));
            var on = apply(original, true); assertAccepted(on);
            Files.write(directory.resolve(on.packFile()), new byte[]{42}, java.nio.file.StandardOpenOption.APPEND);
            Map<String, byte[]> before = snapshot();
            assertEquals(PackSelectionService.Status.REFUSED, apply(on.packFile(), false).status()); assertSnapshot(before);
            Files.write(directory.resolve(original), new byte[]{42}, java.nio.file.StandardOpenOption.APPEND);
            before = snapshot();
            assertEquals(PackSelectionService.Status.REFUSED, apply(original, true).status()); assertSnapshot(before);
        }
    }
    @Test void packagedMaintainedResourcesAreExactAndExcludeProjectedPom() throws Exception {
        for (String id : ORIGINALS.keySet()) {
            String path = id.equals("photon") ? "patches/photon-experiments/live-pixel-budget.json" : "patches/adapters/" + id + ".json";
            byte[] packaged;
            try (var stream = PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/" + path)) {
                assertNotNull(stream, path); packaged = stream.readAllBytes();
            }
            assertArrayEquals(Files.readAllBytes(Path.of(path)), packaged, path);
            if (!id.equals("photon")) {
                var manifest = JsonParser.parseString(new String(packaged, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(effectRecipe(id) ? "volumetric-sample-budget" : "ssr-hit-refinement", manifest.get("kind").getAsString());
                for (var addition : manifest.getAsJsonObject("additions").entrySet()) {
                    var entry = addition.getValue().getAsJsonObject(); String dependency = entry.get("path").getAsString();
                    try (var stream = PackSelectionService.class.getResourceAsStream("/assets/shaderlod/adapters/" + dependency)) {
                        assertNotNull(stream, dependency); byte[] bytes = stream.readAllBytes();
                        assertArrayEquals(Files.readAllBytes(Path.of(dependency)), bytes);
                        assertEquals(entry.get("sha256").getAsString(), sha256(bytes));
                    }
                }
            }
        }
        assertNull(PackSelectionService.class.getResource("/assets/shaderlod/adapters/patches/adapters/complementary-projected-pom.json"));
    }
    @Test void changedNonExecutableLicenseIsRefusedBeforeGeneration() throws Exception {
        Path source = Path.of(ORIGINALS.get("bliss-shader")); assumeTrue(Files.exists(source), source.toString());
        var changed = members(source); changed.put("LICENSE.md", new byte[]{42});
        assertEquals("bliss-shader", SupportedPackCatalog.recognize(memberHashes(changed)).orElseThrow().pack().id());
        writeZip(directory.resolve("Bliss.zip"), changed);
        Map<String, byte[]> before = snapshot();
        var result = apply("Bliss.zip", true);
        assertEquals(PackSelectionService.Status.REFUSED, result.status());
        assertTrue(result.detail().contains("LICENSE.md")); assertSnapshot(before);
    }
    @Test void changedRecordedRecipeIdentityIsRefusedWithoutSidecarWrites() throws Exception {
        Path source = Path.of(ORIGINALS.get("bliss-shader")); assumeTrue(Files.exists(source), source.toString());
        Files.copy(source, directory.resolve("Bliss.zip"));
        var on = apply("Bliss.zip", true); assertAccepted(on);
        Path state;
        try (var paths = Files.list(directory)) {
            state = paths.filter(p -> p.getFileName().toString().startsWith(".shaderlod-selection-")).findFirst().orElseThrow();
        }
        var record = JsonParser.parseString(Files.readString(state)).getAsJsonObject();
        record.addProperty("recipeSha256", "0".repeat(64)); Files.writeString(state, record.toString());
        Map<String, byte[]> before = snapshot();
        assertEquals(PackSelectionService.Status.REFUSED, apply(on.packFile(), false).status()); assertSnapshot(before);
    }
    private static boolean effectRecipe(String id) { return id.equals("bliss-shader") || id.equals("sildurs-vibrant-shaders"); }
    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Map<String, String> memberHashes(Map<String, byte[]> members) throws Exception {
        Map<String, String> result = new TreeMap<>();
        for (var member : members.entrySet()) result.put(member.getKey(), sha256(member.getValue()));
        return result;
    }
    private Map<String, byte[]> snapshot() throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        try (var paths = Files.list(directory)) { for (Path path : paths.toList()) result.put(path.getFileName().toString(), Files.readAllBytes(path)); }
        return result;
    }
    private void assertSnapshot(Map<String, byte[]> before) throws IOException {
        Map<String, byte[]> after = snapshot(); assertEquals(before.keySet(), after.keySet());
        for (String name : before.keySet()) assertArrayEquals(before.get(name), after.get(name), name);
    }
    private static void writeZip(Path path, Map<String, byte[]> files) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : files.entrySet()) { out.putNextEntry(new ZipEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry(); }
        }
    }
    private static Map<String, byte[]> members(Path path) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry; while ((entry = zip.getNextEntry()) != null) if (!entry.isDirectory()) result.put(entry.getName(), zip.readAllBytes());
        }
        return result;
    }
}
