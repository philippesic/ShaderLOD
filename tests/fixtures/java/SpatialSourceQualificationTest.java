package com.pp.shaderlod;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParser;
import com.pp.shaderlod.integration.*;
import com.pp.shaderlod.lod.adapter.WorkKey;
import com.pp.shaderlod.lod.adapter.layout.Dimensions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipFile;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.FileNode;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in frozen artifact qualification. Missing artifacts are failures, never skips. */
class SpatialSourceQualificationTest {
    private static final Map<String, Integer> PROTOCOLS = Map.of("shaderlod:core", 2,
            "shaderlod:effects", 2, "shaderlod:capabilities", 1);
    private static final WorkKey WORK = PhotonSpatialReferenceAdapter.OPAQUE_SPATIAL_IRRADIANCE;
    private static final SourceBinding SOURCE = PhotonSpatialReferenceAdapter.sourceBindings().getFirst();

    private static final class Access implements AdapterRuntimeBinding.RuntimeAccess {
        final Object identity = new Object();
        Path source;
        Map<String, String> options = supportedOptions();
        @Override public Optional<AdapterRuntimeBinding.Pack> currentPack() {
            return Optional.of(new AdapterRuntimeBinding.Pack(identity, source, options));
        }
        @Override public boolean fallback() { return false; }
        @Override public boolean pipelineAvailable() { return true; }
        @Override public Optional<Dimensions> renderedSurface() { return Optional.of(new Dimensions(2976, 1672)); }
    }

    private static Map<String, String> supportedOptions() {
        var options = new LinkedHashMap<String, String>();
        SOURCE.requiredOptions().forEach((key, values) -> options.put(key, values.iterator().next()));
        return Map.copyOf(options);
    }

    private static AdapterRegistration registration() {
        var registration = new AdapterRegistration(PROTOCOLS);
        registration.register(PhotonSpatialReferenceAdapter.descriptor(), PhotonSpatialReferenceAdapter.sourceBindings(),
                PhotonSpatialReferenceAdapter.exports(), PhotonSpatialReferenceAdapter.layoutRequirement());
        return registration;
    }

    @Test void frozenLedgerEqualsEveryContainerMemberOptionAndIdentityPin() throws Exception {
        var ledgerPath = Path.of("build/photon-shared-spatial-freeze.json");
        assertTrue(Files.isRegularFile(ledgerPath), "Generate the frozen spatial ledger before source qualification");
        var ledger = JsonParser.parseString(Files.readString(ledgerPath)).getAsJsonObject();
        var bindings = PhotonSpatialReferenceAdapter.sourceBindings();
        for (int arm = 0; arm < 2; arm++) {
            var metadata = ledger.getAsJsonObject(arm == 0 ? "disabled" : "enabled");
            var declared = metadata.getAsJsonObject("reference_registration");
            var source = declared.getAsJsonArray("sourceBindings").get(0).getAsJsonObject();
            var actual = bindings.get(arm);
            assertEquals(metadata.get("archive_sha256").getAsString(), actual.sourceSha256());
            assertEquals(declared.getAsJsonObject("identity").get("revision").getAsString(), actual.adapter().revision());
            assertEquals(25, actual.memberSha256().size());
            assertEquals(source.getAsJsonObject("memberSha256").size(), actual.memberSha256().size());
            source.getAsJsonObject("memberSha256").entrySet().forEach(entry ->
                    assertEquals(entry.getValue().getAsString(), actual.memberSha256().get(entry.getKey()), entry.getKey()));
            assertEquals(source.getAsJsonObject("requiredOptions").size(), actual.requiredOptions().size());
            source.getAsJsonObject("requiredOptions").entrySet().forEach(entry -> {
                var values = new java.util.HashSet<String>();
                entry.getValue().getAsJsonArray().forEach(value -> values.add(value.getAsString()));
                assertEquals(values, actual.requiredOptions().get(entry.getKey()), entry.getKey());
            });
            assertThrows(UnsupportedOperationException.class, () -> actual.memberSha256().clear());
            assertThrows(UnsupportedOperationException.class, () -> actual.requiredOptions().get("TAAU").add("true"));
        }
        assertTrue(ledger.getAsJsonObject("original").getAsJsonObject("reference_registration")
                .getAsJsonArray("sourceBindings").isEmpty());
    }

    @Test void actualImmutableArchivesQualifyLoadedIrisDefaultsAndOverrides() throws Exception {
        for (int arm = 0; arm < 2; arm++) {
            var archive = Path.of("build/Photon-SharedSpatial-" + (arm == 0 ? "disabled" : "enabled") + ".zip");
            assertTrue(Files.isRegularFile(archive), "Generate frozen archives before qualification");
            var source = PhotonSpatialReferenceAdapter.sourceBindings().get(arm);
            assertTrue(source.matches(PackSourceReader.read(archive)), "Every frozen container/member pin must match");
            var overrides = new LinkedHashMap<>(supportedOptions());
            overrides.remove(PhotonSpatialReferenceAdapter.PRIVATE_OPTION);
            var defaults = effective(archive, overrides);
            assertEquals(Boolean.toString(arm == 1), defaults.get(PhotonSpatialReferenceAdapter.PRIVATE_OPTION));
            assertEquals(arm == 1, source.acceptsOptions(defaults));
            overrides.put(PhotonSpatialReferenceAdapter.PRIVATE_OPTION, "true");
            var loaded = effective(archive, overrides);
            assertEquals(supportedOptions(), loaded);
            var access = new Access();
            access.source = archive;
            access.options = loaded;
            var binding = registration().seal().bind(access, PackSourceReader::read);
            binding.registerCurrentPack();
            assertTrue(binding.snapshot().available(WORK));
            assertFalse(binding.snapshot().available(LodOperations.VOLUME_INTEGRATION));
            overrides.put(PhotonSpatialReferenceAdapter.PRIVATE_OPTION, "false");
            access.options = effective(archive, overrides);
            assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.REQUIRED_OPTION_MISSING_OR_UNSUPPORTED);
        }
    }

    private static Map<String, String> effective(Path archivePath, Map<String, String> overrides) throws Exception {
        var nodes = ImmutableMap.<AbsolutePackPath, FileNode>builder();
        try (var archive = new ZipFile(archivePath.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("shaders/") || !(name.endsWith(".glsl") || name.endsWith(".fsh") || name.endsWith(".vsh"))) continue;
                var path = AbsolutePackPath.fromAbsolutePath("/" + name.substring("shaders/".length()));
                try (var input = archive.getInputStream(entry)) {
                    String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    nodes.put(path, new FileNode(path, ImmutableList.copyOf(source.split("\\R"))));
                }
            }
        }
        var constructor = IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class, ImmutableMap.class);
        constructor.setAccessible(true);
        var graph = constructor.newInstance(nodes.buildOrThrow(), ImmutableMap.of());
        return IrisRuntimeAccess.effectiveOptions(new ShaderPackOptions(graph, overrides), SOURCE.requiredOptions().keySet());
    }

    @Test void actualOriginalArchiveRemainsUnbound() throws Exception {
        var original = Path.of("build/Photon-SharedSpatial-original.zip");
        assertTrue(Files.isRegularFile(original), "Generate frozen original archive before qualification");
        var fingerprint = PackSourceReader.read(original);
        assertEquals("c4e956dfee9ceebb6df245ea4792f5626cbc2d0ff995ccf9b49766d2286b57f1", fingerprint.sourceSha256());
        var access = new Access();
        access.source = original;
        var binding = registration().seal().bind(access, PackSourceReader::read);
        binding.registerCurrentPack();
        assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.UNKNOWN_SOURCE);
    }

    private static void assertFull(AdapterRuntimeBinding.Snapshot snapshot, AdapterRuntimeBinding.Reason reason) {
        assertEquals(reason, snapshot.reason());
        assertFalse(snapshot.available(WORK));
        assertNull(snapshot.descriptor());
        assertTrue(snapshot.operations().isEmpty());
    }
}
