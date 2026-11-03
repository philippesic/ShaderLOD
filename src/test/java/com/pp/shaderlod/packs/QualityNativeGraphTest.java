package com.pp.shaderlod.packs;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real pinned source, generated archives, state and queued edits; only Iris access is replaced. */
class QualityNativeGraphTest {
    @Test void loadedNativeRendererRouteAdmitsOnlyExplicitRendererAndProofControls(){
        var pair=com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested()
            .with(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION,true)
            .with(com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE,true);
        var nativeControls=java.util.EnumSet.of(
            com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION,
            com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE,
            com.pp.shaderlod.optimization.OptimizationId.INDEX_SCALAR_CACHE,
            com.pp.shaderlod.optimization.OptimizationId.SHADOW_POSITIVE_VERTEX,
            com.pp.shaderlod.optimization.OptimizationId.SHADOW_VISIBILITY_LISTS,
            com.pp.shaderlod.optimization.OptimizationId.SHADOW_FRUSTUM_COHERENCE,
            com.pp.shaderlod.optimization.OptimizationId.BINDING_CERTIFICATE_CACHE,
            com.pp.shaderlod.optimization.OptimizationId.BINDING_DIGEST_STREAMING);
        var bundle=pair;
        for(var id:nativeControls)bundle=bundle.with(id,true);
        for(String family:java.util.List.of("makeup-ultra-fast-shaders","sildurs-vibrant-shaders","bsl-shaders",
                "complementary-reimagined","complementary-unbound","bliss-shader","photon","solas-shader","astralex")){
            assertTrue(PackSelectionService.nativeRendererGraph(family,LodPreset.PERFORMANCE,true,true,pair));
            assertTrue(PackSelectionService.nativeRendererGraph(family,LodPreset.PERFORMANCE,true,true,bundle));
            for(var id:com.pp.shaderlod.optimization.OptimizationId.values()){
                if(nativeControls.contains(id)) {
                    assertTrue(PackSelectionService.nativeRendererGraph(family,LodPreset.PERFORMANCE,true,true,
                        com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested().with(id,true)),family+"/"+id);
                } else {
                    assertFalse(PackSelectionService.nativeRendererGraph(family,LodPreset.PERFORMANCE,true,true,bundle.with(id,true)),family+"/"+id);
                }
            }
            assertFalse(PackSelectionService.nativeRendererGraph(family,LodPreset.PERFORMANCE,false,true,pair));
            for(var preset:LodPreset.values())if(preset!=LodPreset.PERFORMANCE)
                assertFalse(PackSelectionService.nativeRendererGraph(family,preset,true,true,bundle),family+"/"+preset);
            assertTrue(com.pp.shaderlod.optimization.OptimizationEditTransaction.cpuOnlyFamily(family));
        }
        for(String family:java.util.List.of("unknown")){
            assertFalse(PackSelectionService.nativeRendererGraph(family,LodPreset.PERFORMANCE,true,true,pair),family);
            assertFalse(com.pp.shaderlod.optimization.OptimizationEditTransaction.cpuOnlyFamily(family));
        }
        assertTrue(PackSelectionService.nativeRendererGraph("photon",LodPreset.PERFORMANCE,true,false,pair));
        assertFalse(PackSelectionService.nativeRendererGraph("makeup-ultra-fast-shaders",LodPreset.QUALITY,true,true,pair));
    }
    @Test void solasAndAstraNormalApplyStillRefusesUnknownKnownFailureFactsBeforeMutation() throws Exception {
        for(var source:java.util.List.of(Path.of("dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip"),
                Path.of("dependencies/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip"))){
            Files.copy(source,directory.resolve(source.getFileName()));
            var backend=new Backend();backend.selected=source.getFileName().toString();var before=snapshot();
            assertThrows(IOException.class,()->new PackSelectionTransaction(backend).prepare(true,LodPreset.PERFORMANCE));
            assertEquals(source.getFileName().toString(),backend.selected);unchanged(before);
        }
    }
    @TempDir Path directory;
    private final class Backend implements PackSelectionTransaction.Backend {
        String selected = "Photon.zip";
        Map<String, String> queue = new HashMap<>();
        public Path directory() { return directory; }
        public String selected() { return selected; }
        public Map<String, String> queue() { return queue; }
        public Map<String, String> effectiveOptions() { return Map.of(); }
        public void select(String name) { selected = name; }
        public void save() {}
    }
    private Backend fixture(String options) throws IOException {
        var backend = new Backend();
        Files.copy(Path.of("dependencies/upstream/photon_v1.3b.zip"), directory.resolve(backend.selected));
        if (options != null) Files.writeString(directory.resolve(backend.selected + ".txt"), options);
        return backend;
    }
    private boolean apply(Backend backend, LodPreset preset) throws IOException {
        return new PackSelectionTransaction(backend).prepare(true, preset);
    }
    private Properties options(String name) throws IOException {
        var values = new Properties();
        try (var input = Files.newInputStream(directory.resolve(name + ".txt"))) { values.load(input); }
        return values;
    }
    private void assertNativeAaOptions(String name, String taa, String taau, String scale) throws IOException {
        var values = options(name);
        assertEquals(taa, values.getProperty("TAA"));
        assertEquals(taau, values.getProperty("TAAU"));
        assertEquals(scale, values.getProperty("TAAU_RENDER_SCALE"));
    }
    private Map<String, byte[]> snapshot() throws IOException {
        var result = new HashMap<String, byte[]>();
        try (var files = Files.list(directory)) {
            for (var file : files.toList()) result.put(file.getFileName().toString(), Files.readAllBytes(file));
        }
        return result;
    }
    private void unchanged(Map<String, byte[]> expected) throws IOException {
        var actual = snapshot(); assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((name, bytes) -> assertArrayEquals(bytes, actual.get(name), name));
    }
    @Test void freshQualityKeepsExactOriginalGraphAndAbsentSidecarWithoutCreatingState() throws Exception {
        var backend = fixture(null); var before = snapshot();
        assertFalse(apply(backend, LodPreset.QUALITY));
        assertEquals("Photon.zip", backend.selected); unchanged(before);
        // The legacy entry point selects Performance but owns no native AA overrides.
        assertTrue(new PackSelectionTransaction(backend).prepare(true));
        assertNotEquals("Photon.zip", backend.selected);
        assertEquals("true", options(backend.selected).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
        assertNativeAaOptions(backend.selected, null, null, null);
        assertFalse(Files.exists(directory.resolve("Photon.zip.txt")));
        assertTrue(apply(backend, LodPreset.QUALITY));
        assertEquals("Photon.zip", backend.selected);
        assertFalse(Files.exists(directory.resolve("Photon.zip.txt")));
    }
    @Test void restartPerformanceQualityPerformanceOffRetainsNativeTaaAndArchive() throws Exception {
        var backend = fixture("# chosen native\nTAA=true\nTAAU=false\nTAAU_RENDER_SCALE=0.75\nINFO=2\n");
        byte[] originalZip = Files.readAllBytes(directory.resolve("Photon.zip"));
        byte[] chosen = Files.readAllBytes(directory.resolve("Photon.zip.txt"));
        assertTrue(apply(backend, LodPreset.PERFORMANCE)); String generated = backend.selected;
        assertNativeAaOptions(generated, "true", "false", "0.75");
        assertTrue(apply(backend, LodPreset.QUALITY)); assertEquals("Photon.zip", backend.selected);
        assertArrayEquals(chosen, Files.readAllBytes(directory.resolve("Photon.zip.txt")));
        var before = snapshot(); assertFalse(apply(backend, LodPreset.QUALITY)); unchanged(before);
        assertTrue(apply(backend, LodPreset.PERFORMANCE)); assertEquals(generated, backend.selected);
        assertNativeAaOptions(generated, "true", "false", "0.75");
        assertTrue(new PackSelectionTransaction(backend).prepare(false, LodPreset.QUALITY));
        assertEquals("Photon.zip", backend.selected);
        assertNativeAaOptions("Photon.zip", "true", "false", "0.75");
        assertArrayEquals(chosen, Files.readAllBytes(directory.resolve("Photon.zip.txt")));
        assertArrayEquals(originalZip, Files.readAllBytes(directory.resolve("Photon.zip")));
    }
    @Test void nativeUpscalingAndAaOffChoicesSurviveQualityWithoutForcedFullScale() throws Exception {
        for (var choice : java.util.List.of("true:true:0.75", "true:true:0.50", "true:false:0.75", "false:false:0.67", "false:true:0.50")) {
            var fields = choice.split(":");
            String name = "Choice" + choice.replace(':', '-') + ".zip";
            Files.copy(Path.of("dependencies/upstream/photon_v1.3b.zip"), directory.resolve(name));
            String chosen = "TAA=" + fields[0] + "\nTAAU=" + fields[1] + "\nTAAU_RENDER_SCALE=" + fields[2] + "\n";
            Files.writeString(directory.resolve(name + ".txt"), chosen);
            var backend = new Backend(); backend.selected = name;
            assertTrue(apply(backend, LodPreset.PERFORMANCE));
            assertNativeAaOptions(backend.selected, fields[0], fields[1], fields[2]);
            assertTrue(apply(backend, LodPreset.QUALITY)); assertEquals(name, backend.selected);
            assertEquals(fields[0], options(name).getProperty("TAA"));
            assertEquals(fields[1], options(name).getProperty("TAAU"));
            assertEquals(fields[2], options(name).getProperty("TAAU_RENDER_SCALE"));
            assertNull(options(name).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
            assertEquals(chosen, Files.readString(directory.resolve(name + ".txt")));
            assertTrue(apply(backend, LodPreset.PERFORMANCE));
            assertNativeAaOptions(backend.selected, fields[0], fields[1], fields[2]);
            assertTrue(new PackSelectionTransaction(backend).prepare(false, LodPreset.QUALITY));
            assertEquals(name, backend.selected);
            assertNativeAaOptions(name, fields[0], fields[1], fields[2]);
            assertEquals(chosen, Files.readString(directory.resolve(name + ".txt")));
        }
    }
    @Test void queuedNativeEditsMergeBeforeQualityAndSurviveNextPerformance() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nTAAU_RENDER_SCALE=0.75\nINFO=2\n");
        apply(backend, LodPreset.PERFORMANCE);
        backend.queue.putAll(Map.of("INFO", "3", "SHADOW_COLOR", "false"));
        assertTrue(apply(backend, LodPreset.QUALITY)); assertTrue(backend.queue.isEmpty());
        assertEquals("3", options("Photon.zip").getProperty("INFO"));
        assertEquals("false", options("Photon.zip").getProperty("SHADOW_COLOR"));
        assertNativeAaOptions("Photon.zip", "true", "false", "0.75");
        apply(backend, LodPreset.PERFORMANCE);
        assertEquals("3", options(backend.selected).getProperty("INFO"));
        assertEquals("false", options(backend.selected).getProperty("SHADOW_COLOR"));
        assertNativeAaOptions(backend.selected, "true", "false", "0.75");
    }
    @Test void failedQualityReloadRollsBackGeneratedStateSelectionAndQueueExactly() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nINFO=2\n"); apply(backend, LodPreset.PERFORMANCE);
        String generated = backend.selected; var before = snapshot(); backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend);
        assertTrue(transaction.prepare(true, LodPreset.QUALITY));
        assertEquals("Photon.zip", backend.selected);
        transaction.restore(); // Real wrapper invokes this after injected reload rejection.
        assertEquals(generated, backend.selected); assertEquals(Map.of("INFO", "3"), backend.queue);
        unchanged(before);
    }
    @Test void conflictingNativeEditsRefuseQualityAndRollbackItsQueuedWrite() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nINFO=2\n"); apply(backend, LodPreset.PERFORMANCE);
        Files.writeString(directory.resolve("Photon.zip.txt"), "TAA=true\nTAAU=false\nINFO=1\n");
        var before = snapshot(); String selected = backend.selected; backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend);
        assertThrows(IOException.class, () -> transaction.prepare(true, LodPreset.QUALITY));
        transaction.restore(); unchanged(before); assertEquals(selected, backend.selected);
        assertEquals(Map.of("INFO", "3"), backend.queue);
    }
    @Test void inactiveStateMustStillValidateBeforeQualityBypass() throws Exception {
        var backend = fixture(null); apply(backend, LodPreset.PERFORMANCE); apply(backend, LodPreset.QUALITY);
        Path state;
        try (var files = Files.list(directory)) { state = files.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow(); }
        var record = JsonParser.parseString(Files.readString(state)).getAsJsonObject();
        record.addProperty("recipeSha256", "0".repeat(64)); Files.writeString(state, record.toString());
        var before = snapshot(); assertThrows(IOException.class, () -> apply(backend, LodPreset.QUALITY)); unchanged(before);
    }
    @Test void qualityRetainsExistingPhotonEligibilityRefusalsWithoutWrites() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nVOXY=true\n"); var before = snapshot();
        assertThrows(IOException.class, () -> apply(backend, LodPreset.QUALITY)); unchanged(before);
    }
    @Test void balancedAndLegacyAggressiveRetainTheGeneratedPath() throws Exception {
        var backend = fixture(null);
        assertTrue(apply(backend, LodPreset.BALANCED)); String generated = backend.selected;
        assertFalse(apply(backend, LodPreset.AGGRESSIVE)); assertEquals(generated, backend.selected);
        assertEquals("true", options(generated).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
    }
    @Test void literalNullLegacyEffectiveMapStillCompilesAndUsesPerformance() throws Exception {
        var backend = fixture(null);
        var result = new PackSelectionService().apply(directory, backend.selected, true, null);
        assertTrue(result.accepted(), result.detail()); assertNotEquals("Photon.zip", result.packFile());
        assertEquals("true", options(result.packFile()).getProperty("SHADERLOD_LIVE_PIXEL_BUDGET"));
    }
    @Test void originalQualityPendingNativeQueueNeedsReloadAndRecoveryKeepsIt() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nINFO=2\n");
        assertFalse(apply(backend, LodPreset.QUALITY)); var before = snapshot();
        backend.queue.put("INFO", "3"); var transaction = new PackSelectionTransaction(backend);
        assertTrue(transaction.prepare(true, LodPreset.QUALITY));
        assertEquals("Photon.zip", backend.selected); assertEquals(Map.of("INFO", "3"), backend.queue);
        unchanged(before);
        backend.queue.clear(); // Iris consumed the queue before a reload failure.
        transaction.restore(); assertEquals(Map.of("INFO", "3"), backend.queue); unchanged(before);
        assertFalse(new PackSelectionTransaction(backend).prepare(false, LodPreset.QUALITY));
        assertEquals(Map.of("INFO", "3"), backend.queue); unchanged(before);
    }
    @Test void qualityUnknownOriginalRefusesWithoutFileOrQueueMutation() throws Exception {
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(directory.resolve("Unknown.zip")))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("shaders/example.glsl"));
            zip.write("void main() {}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
        var backend = new Backend(); backend.selected = "Unknown.zip"; backend.queue.put("INFO", "3");
        var before = snapshot(); assertThrows(IOException.class, () -> apply(backend, LodPreset.QUALITY));
        assertEquals("Unknown.zip", backend.selected); assertEquals(Map.of("INFO", "3"), backend.queue); unchanged(before);
    }
    @Test void qualityInvalidNativeScaleRefusesBeforeAnyMutation() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nTAAU_RENDER_SCALE=NaN\n"); var before = snapshot();
        assertThrows(IOException.class, () -> apply(backend, LodPreset.QUALITY)); unchanged(before);
    }
    @Test void qualityPreservesCertifiedReimaginedNativeGraphWithoutWrites() throws Exception {
        Files.copy(Path.of("dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"), directory.resolve("Comp.zip"));
        var before = snapshot();
        var result = new PackSelectionService().apply(directory, "Comp.zip", true, LodPreset.QUALITY, Map.of());
        assertTrue(result.accepted(), result.detail()); assertEquals("Comp.zip", result.packFile());
        assertFalse(result.generatedArchive()); unchanged(before);
    }
    @Test void qualityKeepsNativeBlissWithoutRendererResearchPermission() throws Exception {
        Files.copy(Path.of("dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"), directory.resolve("Bliss.zip"));
        var before = snapshot();
        var result = new PackSelectionService().apply(directory, "Bliss.zip", true, LodPreset.QUALITY, Map.of());
        assertTrue(result.accepted(), result.detail()); assertEquals("Bliss.zip", result.packFile());
        assertFalse(result.generatedArchive()); unchanged(before);
    }
    private void witnessedIrisWrite(Backend backend) throws Exception {
        var values = new Properties(); Path path = directory.resolve("Photon.zip.txt");
        try (var input = Files.newInputStream(path)) { values.load(input); }
        values.putAll(backend.queue); backend.queue.clear();
        try (var output = Files.newOutputStream(path)) { values.store(output, null); }
        PackSelectionTransaction.irisOptionsWritten(path, values);
    }
    @Test void idleAndUnrelatedWriterCallbacksPerformNoPropertyOrFileWork() throws Exception {
        var unusable = new Properties() { @Override public boolean isEmpty() { throw new AssertionError("Must not inspect unrelated Properties"); } };
        PackSelectionTransaction.irisOptionsWritten(directory.resolve("Unrelated.zip.txt"), unusable);
        var backend = fixture("TAA=true\nTAAU=false\nINFO=2\n"); backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend); transaction.prepare(true, LodPreset.QUALITY);
        transaction.reload(() -> PackSelectionTransaction.irisOptionsWritten(directory.resolve("Unrelated.zip.txt"), unusable));
    }
    @Test void concurrentModeEditPreventsOverwritingWitnessedNativeFile() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nINFO=2\n"); Path path = directory.resolve("Photon.zip.txt");
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
        backend.queue.put("INFO", "3"); var transaction = new PackSelectionTransaction(backend);
        transaction.prepare(true, LodPreset.QUALITY);
        assertThrows(IOException.class, () -> transaction.reload(() -> {
            witnessedIrisWrite(backend);
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            throw new IOException("Rejected after independent mode edit");
        }));
        byte[] concurrent = Files.readAllBytes(path); var mode = Files.getPosixFilePermissions(path);
        assertThrows(IOException.class, transaction::restoreForRecovery); transaction.finishRecovery();
        assertArrayEquals(concurrent, Files.readAllBytes(path)); assertEquals(mode, Files.getPosixFilePermissions(path));
        assertEquals(Map.of("INFO", "3"), backend.queue);
    }
    @Test void noncanonicalConcurrentCommentAtWriterReturnIsNotClaimedAsOwned() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nINFO=2\n"); backend.queue.put("INFO", "3");
        var transaction = new PackSelectionTransaction(backend); transaction.prepare(true, LodPreset.QUALITY);
        Path path = directory.resolve("Photon.zip.txt");
        assertThrows(IOException.class, () -> transaction.reload(() -> {
            var values = new Properties(); values.putAll(Map.of("TAA", "true", "TAAU", "false", "INFO", "3"));
            backend.queue.clear();
            try (var output = Files.newOutputStream(path)) { values.store(output, null); }
            Files.writeString(path, "# unrelated comment\n", java.nio.file.StandardOpenOption.APPEND);
            PackSelectionTransaction.irisOptionsWritten(path, values);
        }));
        byte[] concurrent = Files.readAllBytes(path);
        assertThrows(IOException.class, transaction::restoreForRecovery); transaction.finishRecovery();
        assertArrayEquals(concurrent, Files.readAllBytes(path)); assertEquals(Map.of("INFO", "3"), backend.queue);
    }
    @Test void canonicalWindowsCrLfHeaderKeepsItsExactPropertyBody() throws Exception {
        var method = PackSelectionTransaction.class.getDeclaredMethod("propertiesBody", byte[].class);
        method.setAccessible(true);
        byte[] serialized = "#Wed Oct 07 07:00:00 PDT 2026\r\nINFO=3\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] body = (byte[]) method.invoke(null, (Object) serialized);
        assertArrayEquals("INFO=3\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), body);
    }
    @Test void newerIndependentQueuedIntentIsDeferredDuringValidRecoveryAndNotReplacedByFailedValues() throws Exception {
        var backend = fixture("TAA=true\nTAAU=false\nINFO=2\n"); byte[] prior = Files.readAllBytes(directory.resolve("Photon.zip.txt"));
        backend.queue.put("INFO", "3"); var transaction = new PackSelectionTransaction(backend);
        transaction.prepare(true, LodPreset.QUALITY);
        assertThrows(IOException.class, () -> transaction.reload(() -> {
            witnessedIrisWrite(backend);
            backend.queue.putAll(Map.of("INFO", "1", "INDEPENDENT", "chosen"));
            throw new IOException("Rejected after native write and independent queue edit");
        }));
        transaction.restoreForRecovery(); assertTrue(backend.queue.isEmpty());
        transaction.recoveryReload(() -> {
            assertTrue(backend.queue.isEmpty());
            assertArrayEquals(prior, Files.readAllBytes(directory.resolve("Photon.zip.txt")));
            witnessedIrisWrite(backend);
        });
        transaction.finishRecovery();
        assertEquals(Map.of("INFO", "1", "INDEPENDENT", "chosen"), backend.queue);
        assertArrayEquals(prior, Files.readAllBytes(directory.resolve("Photon.zip.txt")));
    }
}
