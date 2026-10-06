package com.pp.shaderlod;

import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.integration.IrisRuntimeAccess;
import com.pp.shaderlod.integration.PhotonReferenceAdapter;
import com.pp.shaderlod.integration.SourceBinding;
import com.pp.shaderlod.integration.SourceFingerprint;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import com.pp.shaderlod.lod.adapter.AdapterRegistry;
import com.pp.shaderlod.lod.adapter.WorkCapability;
import com.pp.shaderlod.lod.adapter.WorkKey;
import com.pp.shaderlod.lod.adapter.WorkKind;
import com.pp.shaderlod.mixin.EffectUniformsMixin;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ServiceConfigurationError;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.FileNode;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;

import static com.pp.shaderlod.integration.AdapterRuntimeBinding.Reason;
import static org.junit.jupiter.api.Assertions.*;

class AdapterRuntimeBindingTest {
    private static final String SOURCE_HASH = "a".repeat(64);
    private static final String MEMBER_HASH = "b".repeat(64);
    private static final String MEMBER = "shaders/include/shaderlod/work_policy.glsl";
    private static final String OPTION = "SHADERLOD_VOLUMETRIC_INTEGRATION_LOD";
    private static final SourceFingerprint PIN = new SourceFingerprint(SOURCE_HASH, Map.of(MEMBER, MEMBER_HASH));
    private static final SourceBinding SOURCE = new SourceBinding(PhotonReferenceAdapter.IDENTITY, SOURCE_HASH,
            Map.of(MEMBER, MEMBER_HASH), Map.of(OPTION, Set.of("true")));
    private static final Map<String, Integer> PROTOCOLS = Map.of("shaderlod:core", 2, "shaderlod:effects", 2,
            "shaderlod:capabilities", 1);

    @TempDir Path directory;

    private static final class Access implements AdapterRuntimeBinding.RuntimeAccess {
        AdapterRuntimeBinding.Pack pack = pack(new Object(), Map.of(OPTION, "true"));
        boolean fallback;
        boolean pipeline;
        int availabilityReads;
        @Override public Optional<AdapterRuntimeBinding.Pack> currentPack() { return Optional.ofNullable(pack); }
        @Override public boolean fallback() { return fallback; }
        @Override public boolean pipelineAvailable() { availabilityReads++; return pipeline; }
    }

    private static AdapterRuntimeBinding.Pack pack(Object identity, Map<String, String> options) {
        return new AdapterRuntimeBinding.Pack(identity, Path.of("any-exact-name.zip"), options);
    }

    private static AdapterRuntimeBinding resolver(Access access, AtomicInteger reads) {
        return resolver(access, List.of(SOURCE), PROTOCOLS, source -> { reads.incrementAndGet(); return PIN; });
    }

    private static AdapterRuntimeBinding resolver(Access access, List<SourceBinding> sources,
                                                  Map<String, Integer> protocols,
                                                  AdapterRuntimeBinding.SourceReader reader) {
        return new AdapterRuntimeBinding(new AdapterRegistry().register(PhotonReferenceAdapter.descriptor()),
                sources, protocols, access, reader);
    }

    @Test void validatesAtRegistrationOncePerPackIdentityBeforePipelineExists() {
        var access = new Access();
        var reads = new AtomicInteger();
        var binding = resolver(access, reads);
        binding.registerCurrentPack();
        binding.registerCurrentPack();
        assertEquals(1, reads.get());
        assertEquals(0, access.availabilityReads);
        assertEquals(Reason.PIPELINE_UNAVAILABLE, binding.snapshot().reason());
        access.pipeline = true;
        for (int supplier = 0; supplier < 100; supplier++) {
            assertTrue(binding.snapshot().available(PhotonReferenceAdapter.VOLUME_INTEGRATION));
        }
        assertEquals(1, reads.get());
    }

    @Test void reloadNeedsRegistrationEvenWhenNameAndObjectEqualityAreUnchanged() {
        var access = new Access();
        var reads = new AtomicInteger();
        var binding = resolver(access, reads);
        access.pipeline = true;
        // Equal values do not make these two separately loaded packs identical.
        access.pack = pack(new String("same"), Map.of(OPTION, "true"));
        binding.registerCurrentPack();
        access.pack = pack(new String("same"), Map.of(OPTION, "true"));
        assertEquals(Reason.UNVALIDATED_PACK, binding.snapshot().reason());
        assertEquals(1, reads.get());
        binding.registerCurrentPack();
        assertTrue(binding.snapshot().available(PhotonReferenceAdapter.VOLUME_INTEGRATION));
        assertEquals(2, reads.get());
    }

    @Test void retainedCurrentPackCannotEnableCapabilityDuringFallback() {
        var access = new Access();
        var reads = new AtomicInteger();
        var binding = resolver(access, reads);
        binding.registerCurrentPack();
        access.pipeline = true;
        assertEquals(Reason.AVAILABLE, binding.snapshot().reason());
        access.fallback = true;
        assertFullQuality(binding.snapshot(), Reason.FALLBACK);
        access.fallback = false;
        assertEquals(Reason.AVAILABLE, binding.snapshot().reason());
        assertEquals(1, reads.get());
    }

    @Test void noPackAndUnregisteredReloadFailClosedWithoutFrameHashing() {
        var access = new Access();
        var reads = new AtomicInteger();
        var binding = resolver(access, reads);
        assertFullQuality(binding.snapshot(), Reason.UNVALIDATED_PACK);
        assertEquals(0, reads.get());
        binding.registerCurrentPack();
        access.pack = null;
        assertFullQuality(binding.snapshot(), Reason.NO_PACK);
        binding.registerCurrentPack();
        assertEquals(1, reads.get());
    }

    @Test void unknownArchiveAndChangedConsumerMemberStayFullQuality() {
        for (var source : List.of(new SourceFingerprint("c".repeat(64), Map.of(MEMBER, MEMBER_HASH)),
                new SourceFingerprint(SOURCE_HASH, Map.of(MEMBER, "c".repeat(64))),
                new SourceFingerprint(SOURCE_HASH, Map.of()))) {
            var access = new Access();
            access.pipeline = true;
            var binding = resolver(access, List.of(SOURCE), PROTOCOLS, path -> source);
            binding.registerCurrentPack();
            assertFullQuality(binding.snapshot(), Reason.UNKNOWN_SOURCE);
        }
    }

    @Test void unreadableSourceIsCachedUntilActualReload() {
        var access = new Access();
        access.pipeline = true;
        var reads = new AtomicInteger();
        var binding = resolver(access, List.of(SOURCE), PROTOCOLS,
                path -> { reads.incrementAndGet(); throw new IOException("fixture unreadable source"); });
        binding.registerCurrentPack();
        binding.registerCurrentPack();
        assertFullQuality(binding.snapshot(), Reason.SOURCE_UNREADABLE);
        assertEquals(1, reads.get());
    }

    @Test void declarationWithoutSourcePinHasNoCapabilityAndDoesNotReadArchive() {
        var access = new Access();
        access.pipeline = true;
        var binding = resolver(access, List.of(), PROTOCOLS, path -> { fail("No registered source requires hashing"); return PIN; });
        binding.registerCurrentPack();
        assertFullQuality(binding.snapshot(), Reason.UNKNOWN_SOURCE);
        assertEquals(8, ((WorkCapability.CountFloor) PhotonReferenceAdapter.descriptor()
                .capabilities().getFirst().domain()).minimumRetainedWork());
    }

    @Test void missingUnknownAndIncompatibleProtocolsNeverEnableDeclaredOperation() {
        for (var protocols : List.of(Map.of("shaderlod:core", 2),
                Map.of("shaderlod:core", 3, "shaderlod:effects", 2, "shaderlod:capabilities", 1))) {
            var access = new Access();
            access.pipeline = true;
            var binding = resolver(access, List.of(SOURCE), protocols, path -> PIN);
            binding.registerCurrentPack();
            assertFullQuality(binding.snapshot(), protocols.size() == 1 ? Reason.MISSING_PROTOCOL : Reason.UNSUPPORTED_PROTOCOL);
        }
        var access = new Access();
        access.pipeline = true;
        var binding = new AdapterRuntimeBinding(new AdapterRegistry(), List.of(SOURCE), PROTOCOLS, access, path -> PIN);
        binding.registerCurrentPack();
        assertFullQuality(binding.snapshot(), Reason.UNREGISTERED_ADAPTER);
    }

    @Test void missingOrDisabledEffectiveKeyDoesNotUseFilenameOrConfigAsEvidence() {
        var access = new Access();
        access.pipeline = true;
        var reads = new AtomicInteger();
        var binding = resolver(access, reads);
        binding.registerCurrentPack();
        var identity = access.pack.identity();
        for (var options : List.of(Map.<String, String>of(), Map.of(OPTION, "false"), Map.of(OPTION, "TRUE"))) {
            access.pack = new AdapterRuntimeBinding.Pack(identity,
                    Path.of("photon-fully-supported-shaderlod-adaptive.zip"), options);
            assertFullQuality(binding.snapshot(), Reason.REQUIRED_OPTION_MISSING_OR_UNSUPPORTED);
        }
        access.pack = pack(identity, Map.of(OPTION, "true"));
        assertEquals(Reason.AVAILABLE, binding.snapshot().reason());
        assertEquals(1, reads.get());
    }

    @Test void conflictingExactBindingsFailClosed() {
        var access = new Access();
        access.pipeline = true;
        var binding = resolver(access, List.of(SOURCE, SOURCE), PROTOCOLS, path -> PIN);
        binding.registerCurrentPack();
        assertFullQuality(binding.snapshot(), Reason.AMBIGUOUS_SOURCE);
    }

    @Test void exactOperationDoesNotGrantOtherCountsRatesOrEffects() {
        var access = new Access();
        access.pipeline = true;
        var binding = resolver(access, new AtomicInteger());
        binding.registerCurrentPack();
        var state = binding.snapshot();
        assertTrue(state.available(PhotonReferenceAdapter.VOLUME_INTEGRATION));
        assertFalse(state.available(new WorkKey(Effect.VOLUMETRICS, "shaderlod:secondary_samples", WorkKind.COUNT)));
        assertFalse(state.available(new WorkKey(Effect.VOLUMETRICS, "shaderlod:volume_integration", WorkKind.RATE)));
        assertFalse(state.available(new WorkKey(Effect.REFLECTIONS, "shaderlod:volume_integration", WorkKind.COUNT)));
    }

    @Test void configAndCapabilitySuppliersStayCoherentUntilNextNotifierFrame() {
        var access = new Access();
        access.pipeline = true;
        var binding = resolver(access, new AtomicInteger());
        binding.registerCurrentPack();
        var policy = new ShaderLodEffectConfig.Control(ShaderLodEffectConfig.Mode.ADAPTIVE,
                EffectPolicy.defaultsFor(Effect.VOLUMETRICS));
        var reads = new AtomicInteger();
        var frame = new EffectUniformFrame(() -> Map.of(Effect.VOLUMETRICS, policy),
                () -> { reads.incrementAndGet(); return binding.snapshot(); });
        access.fallback = true;
        for (int supplier = 0; supplier < 100; supplier++) {
            assertSame(policy, frame.control(Effect.VOLUMETRICS));
            assertTrue(frame.binding().available(PhotonReferenceAdapter.VOLUME_INTEGRATION));
        }
        assertEquals(1, reads.get());
        frame.beginFrame();
        assertFullQuality(frame.binding(), Reason.FALLBACK);
        assertEquals(2, reads.get());
    }

    @Test void realArchiveAndFolderFingerprintsPinExactBytesAndMembers() throws Exception {
        var source = directory.resolve("unrelated-name.zip");
        byte[] content = "// exact fixture source\n".getBytes(StandardCharsets.UTF_8);
        writeArchive(source, MEMBER, content);
        var fingerprint = PackSourceReader.read(source);
        assertEquals(sha(Files.readAllBytes(source)), fingerprint.sourceSha256());
        assertEquals(sha(content), fingerprint.memberSha256().get(MEMBER));
        var registered = new SourceBinding(PhotonReferenceAdapter.IDENTITY, fingerprint.sourceSha256(),
                fingerprint.memberSha256(), Map.of());
        assertTrue(registered.matches(fingerprint));
        var renamed = directory.resolve("photon-other-display-name.zip");
        Files.copy(source, renamed);
        assertTrue(registered.matches(PackSourceReader.read(renamed)));
        writeArchive(source, MEMBER, "// changed consuming source".getBytes(StandardCharsets.UTF_8));
        assertFalse(registered.matches(PackSourceReader.read(source)));
        var folder = directory.resolve("folder");
        var member = folder.resolve(MEMBER);
        Files.createDirectories(member.getParent());
        Files.write(member, content);
        var directoryFingerprint = PackSourceReader.read(folder);
        assertEquals(sha((MEMBER + "\0" + sha(content) + "\n").getBytes(StandardCharsets.UTF_8)),
                directoryFingerprint.sourceSha256());
        assertEquals(sha(content), directoryFingerprint.memberSha256().get(MEMBER));
    }

    @Test void unsafeSourceMembersAndMalformedPinsAreRejected() throws Exception {
        var source = directory.resolve("unsafe.zip");
        writeArchive(source, "../shaders/work.glsl", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> PackSourceReader.read(source));
        assertThrows(IllegalArgumentException.class, () -> new SourceBinding(PhotonReferenceAdapter.IDENTITY,
                "not-a-digest", Map.of(MEMBER, MEMBER_HASH), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new SourceBinding(PhotonReferenceAdapter.IDENTITY,
                SOURCE_HASH, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new SourceBinding(PhotonReferenceAdapter.IDENTITY,
                SOURCE_HASH, Map.of(MEMBER, MEMBER_HASH), Map.of("not an option", Set.of("true"))));
    }

    @Test void runtimeFailureCannotSupplyCapabilities() {
        var access = new Access();
        var binding = new AdapterRuntimeBinding(new AdapterRegistry().register(PhotonReferenceAdapter.descriptor()),
                List.of(SOURCE), PROTOCOLS, new AdapterRuntimeBinding.RuntimeAccess() {
                    @Override public Optional<AdapterRuntimeBinding.Pack> currentPack() { return Optional.of(access.pack); }
                    @Override public boolean fallback() { throw new IllegalStateException("fixture unavailable runtime"); }
                    @Override public boolean pipelineAvailable() { fail("Fallback read failed first"); return true; }
                }, path -> PIN);
        binding.registerCurrentPack();
        assertFullQuality(binding.snapshot(), Reason.RUNTIME_UNAVAILABLE);
    }

    @Test void absentIrisPlatformServiceCannotInterruptRegistrationOrEnableCapabilities() {
        var unavailable = new AdapterRuntimeBinding.RuntimeAccess() {
            @Override public Optional<AdapterRuntimeBinding.Pack> currentPack() {
                throw new ServiceConfigurationError("fixture absent Iris platform loader");
            }
            @Override public boolean fallback() { fail("No runtime available"); return true; }
            @Override public boolean pipelineAvailable() { fail("No runtime available"); return false; }
        };
        var binding = new AdapterRuntimeBinding(new AdapterRegistry().register(PhotonReferenceAdapter.descriptor()),
                PhotonReferenceAdapter.sourceBindings(), PROTOCOLS, unavailable,
                source -> { fail("No pack identity available"); return PIN; });
        assertDoesNotThrow(binding::registerCurrentPack);
        assertFullQuality(binding.snapshot(), Reason.UNVALIDATED_PACK);
    }

    @Test void additiveMixinRegistersReferenceTypedPerFrameCapabilitiesWithSafeInitialValue() throws Exception {
        var registered = new LinkedHashMap<String, IntSupplier>();
        var holder = (UniformHolder) Proxy.newProxyInstance(UniformHolder.class.getClassLoader(),
                new Class<?>[]{UniformHolder.class}, (proxy, method, args) -> {
                    assertEquals("uniform1i", method.getName());
                    assertEquals(UniformUpdateFrequency.PER_FRAME, args[0]);
                    assertNull(registered.put((String) args[1], (IntSupplier) args[2]));
                    return proxy;
                });
        var notifier = new FrameUpdateNotifier();
        var callback = EffectUniformsMixin.class.getDeclaredMethod("shaderlod$registerCapabilities",
                UniformHolder.class, FrameUpdateNotifier.class, PackDirectives.class, CallbackInfo.class);
        callback.setAccessible(true);
        callback.invoke(null, holder, notifier, null, new CallbackInfo("generalCommonUniforms", false));
        assertEquals(Set.of("shaderlodCapabilitiesVersion", "shaderlodVolumeIntegrationAvailable",
                "shaderlodOpaqueSpatialAvailable"), registered.keySet());
        assertEquals(1, registered.get("shaderlodCapabilitiesVersion").getAsInt());
        assertEquals(0, registered.get("shaderlodVolumeIntegrationAvailable").getAsInt());
        assertEquals(0, registered.get("shaderlodOpaqueSpatialAvailable").getAsInt());
        notifier.onNewFrame();
        assertEquals(0, registered.get("shaderlodVolumeIntegrationAvailable").getAsInt());
        assertEquals(0, registered.get("shaderlodOpaqueSpatialAvailable").getAsInt());
    }

    @Test void pinnedIrisOptionsAdmitOnlyDiscoveredKeysAndReadActualDefaultsAndOverrides() throws Exception {
        String text = "//#define " + OPTION + "\n#ifdef " + OPTION + "\n#endif\n#define CELL_COUNT 8 // [8 16]\n";
        var path = AbsolutePackPath.fromAbsolutePath("/settings.glsl");
        var node = new FileNode(path, ImmutableList.copyOf(text.split("\\R")));
        var constructor = IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class, ImmutableMap.class);
        constructor.setAccessible(true);
        var graph = constructor.newInstance(ImmutableMap.of(path, node), ImmutableMap.of());
        var keys = Set.of(OPTION, "CELL_COUNT", "UNDECLARED_OPTION");
        var defaults = new ShaderPackOptions(graph, Map.of("UNDECLARED_OPTION", "true"));
        assertEquals(Map.of(OPTION, "false", "CELL_COUNT", "8"), IrisRuntimeAccess.effectiveOptions(defaults, keys));
        var enabled = new ShaderPackOptions(graph, Map.of(OPTION, "true", "CELL_COUNT", "16", "UNDECLARED_OPTION", "true"));
        assertEquals(Map.of(OPTION, "true", "CELL_COUNT", "16"), IrisRuntimeAccess.effectiveOptions(enabled, keys));
        assertFalse(SOURCE.acceptsOptions(IrisRuntimeAccess.effectiveOptions(defaults, keys)));
        assertTrue(SOURCE.acceptsOptions(IrisRuntimeAccess.effectiveOptions(enabled, keys)));
    }

    @Test void actualFrozenReferenceArchivesQualifyExactSourceMembersAndLoadedIrisOptions() throws Exception {
        var bindings = PhotonReferenceAdapter.sourceBindings();
        assertEquals(2, bindings.size());
        var keys = Set.of(OPTION, "CLOUDS_CUMULUS", "BLOCKY_CLOUDS");
        for (int arm = 0; arm < 2; arm++) {
            var archive = Path.of("build/Photon-VolumetricLOD-" + (arm == 0 ? "disabled" : "enabled") + ".zip");
            Assumptions.assumeTrue(Files.isRegularFile(archive), "Generate frozen reference archives before qualification");
            var sourceBinding = bindings.get(arm);
            assertEquals(10, sourceBinding.memberSha256().size());
            var fingerprint = PackSourceReader.read(archive);
            assertTrue(sourceBinding.matches(fingerprint), "Actual frozen container and every consuming member must match");
            var options = archiveOptions(archive, Map.of(OPTION, "true", "CLOUDS_CUMULUS", "true", "BLOCKY_CLOUDS", "false"));
            var effective = IrisRuntimeAccess.effectiveOptions(options, keys);
            assertEquals(Map.of(OPTION, "true", "CLOUDS_CUMULUS", "true", "BLOCKY_CLOUDS", "false"), effective);
            var access = new Access();
            access.pipeline = true;
            var identity = new Object();
            access.pack = new AdapterRuntimeBinding.Pack(identity, archive, effective);
            var resolver = resolver(access, bindings, PROTOCOLS, PackSourceReader::read);
            resolver.registerCurrentPack();
            assertTrue(resolver.snapshot().available(PhotonReferenceAdapter.VOLUME_INTEGRATION));

            var defaultEffective = IrisRuntimeAccess.effectiveOptions(archiveOptions(archive,
                    Map.of("CLOUDS_CUMULUS", "true", "BLOCKY_CLOUDS", "false")), keys);
            assertEquals(Boolean.toString(arm == 1), defaultEffective.get(OPTION));
            assertEquals(arm == 1, sourceBinding.acceptsOptions(defaultEffective));
            for (var rejected : List.of(Map.of(OPTION, "false", "CLOUDS_CUMULUS", "true", "BLOCKY_CLOUDS", "false"),
                    Map.of(OPTION, "true", "CLOUDS_CUMULUS", "false", "BLOCKY_CLOUDS", "false"),
                    Map.of(OPTION, "true", "CLOUDS_CUMULUS", "true", "BLOCKY_CLOUDS", "true"),
                    Map.of("UNKNOWN_INTEGRATION_FLAG", "true", "CLOUDS_CUMULUS", "true", "BLOCKY_CLOUDS", "false"))) {
                access.pack = new AdapterRuntimeBinding.Pack(identity, archive, rejected);
                assertFullQuality(resolver.snapshot(), Reason.REQUIRED_OPTION_MISSING_OR_UNSUPPORTED);
            }
            var changed = new SourceFingerprint("0".repeat(64), fingerprint.memberSha256());
            assertFalse(sourceBinding.matches(changed));
            var unknown = resolver(access, bindings, PROTOCOLS, path -> changed);
            unknown.registerCurrentPack();
            assertFullQuality(unknown.snapshot(), Reason.UNKNOWN_SOURCE);
        }
    }

    private static ShaderPackOptions archiveOptions(Path archivePath, Map<String, String> overrides) throws Exception {
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
        return new ShaderPackOptions(graph, overrides);
    }

    private static void assertFullQuality(AdapterRuntimeBinding.Snapshot snapshot, Reason reason) {
        assertEquals(reason, snapshot.reason());
        assertFalse(snapshot.available(PhotonReferenceAdapter.VOLUME_INTEGRATION));
        assertTrue(snapshot.operations().isEmpty());
        assertNull(snapshot.descriptor());
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void writeArchive(Path path, String member, byte[] bytes) throws IOException {
        try (var archive = new ZipOutputStream(Files.newOutputStream(path))) {
            archive.putNextEntry(new ZipEntry(member));
            archive.write(bytes);
            archive.closeEntry();
        }
    }
}
