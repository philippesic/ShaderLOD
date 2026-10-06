package com.pp.shaderlod;

import com.pp.shaderlod.integration.*;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.adapter.*;
import com.pp.shaderlod.mixin.EffectUniformsMixin;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.*;

class AdapterExtensionTest {
    private static final Map<String, Integer> PROTOCOLS = Map.of("shaderlod:core", 2, "shaderlod:effects", 2,
            "shaderlod:capabilities", 1);
    private static final WorkKey CUSTOM = new WorkKey(Effect.REFLECTIONS, "example:reflection_steps", WorkKind.COUNT);
    private static final CapabilityExport EXPORT = new CapabilityExport(CUSTOM, "shaderlodExampleReflectionAvailable");
    private static final AdapterDescriptor DESCRIPTOR = descriptor("example:reference", CUSTOM);
    private static final SourceBinding SOURCE = source(DESCRIPTOR, "a", Map.of("EXAMPLE_LOD", Set.of("true")));

    private static AdapterDescriptor descriptor(String id, WorkKey operation) {
        return new AdapterDescriptor(new AdapterIdentity(id, "exact-r1"), List.of(
                new ProtocolRequirement("shaderlod:core", Set.of(2)),
                new ProtocolRequirement("shaderlod:capabilities", Set.of(1))),
                List.of(new WorkCapability(operation, new WorkCapability.CountFloor(1))));
    }

    private static SourceBinding source(AdapterDescriptor descriptor, String hash, Map<String, Set<String>> options) {
        return new SourceBinding(descriptor.identity(), hash.repeat(64), Map.of("shaders/work.glsl", "b".repeat(64)), options);
    }

    private static AdapterRegistration withReference() {
        var registration = new AdapterRegistration(PROTOCOLS);
        registration.register(PhotonReferenceAdapter.descriptor(), PhotonReferenceAdapter.sourceBindings(),
                List.of(new CapabilityExport(LodOperations.VOLUME_INTEGRATION, "shaderlodVolumeIntegrationAvailable")));
        return registration;
    }

    @Test void secondAdapterSelectsItsCustomOperationAndRequiredOptionsWithoutPhotonEdits() {
        var registration = withReference();
        registration.register(DESCRIPTOR, List.of(SOURCE), List.of(EXPORT));
        var sealed = registration.seal();
        assertEquals(2, sealed.descriptors().descriptors().size());
        assertEquals(2, sealed.exports().size());
        assertTrue(sealed.optionKeys().contains("EXAMPLE_LOD"));
        var reads = new AtomicInteger();
        var access = new Access();
        var binding = sealed.bind(access, path -> {
            reads.incrementAndGet();
            return new SourceFingerprint(SOURCE.sourceSha256(), SOURCE.memberSha256());
        });
        binding.registerCurrentPack();
        binding.registerCurrentPack();
        assertTrue(binding.snapshot().available(CUSTOM));
        assertFalse(binding.snapshot().available(LodOperations.VOLUME_INTEGRATION));
        access.options = Map.of("EXAMPLE_LOD", "false");
        assertEquals(AdapterRuntimeBinding.Reason.REQUIRED_OPTION_MISSING_OR_UNSUPPORTED, binding.snapshot().reason());
        assertEquals(1, reads.get());
    }

    @Test void customSuppliersShareOneCoherentFrameAndDoNotReadRuntimePerUniform() {
        var binding = new AtomicReference<>(new AdapterRuntimeBinding.Snapshot(AdapterRuntimeBinding.Reason.AVAILABLE,
                DESCRIPTOR, Set.of(CUSTOM)));
        var reads = new AtomicInteger();
        var frame = new EffectUniformFrame(Map::of, () -> { reads.incrementAndGet(); return binding.get(); });
        var supplier = EXPORT.supplier(frame);
        var volume = new CapabilityExport(LodOperations.VOLUME_INTEGRATION, "shaderlodVolumeIntegrationAvailable").supplier(frame);
        binding.set(AdapterRuntimeBinding.Snapshot.unavailable(AdapterRuntimeBinding.Reason.FALLBACK));
        for (int i = 0; i < 100; i++) {
            assertEquals(1, supplier.getAsInt());
            assertEquals(0, volume.getAsInt());
        }
        assertEquals(1, reads.get());
        frame.beginFrame();
        assertEquals(0, supplier.getAsInt());
        assertEquals(0, volume.getAsInt());
        assertEquals(2, reads.get());
    }

    @Test void emptyAndUnknownSourcesKeepCustomExportAtZero() {
        for (var sources : List.of(List.<SourceBinding>of(), List.of(SOURCE))) {
            var registration = new AdapterRegistration(PROTOCOLS);
            registration.register(DESCRIPTOR, sources, List.of(EXPORT));
            var binding = registration.seal().bind(new Access(), path -> new SourceFingerprint("c".repeat(64), Map.of()));
            var frame = new EffectUniformFrame(Map::of, binding::snapshot);
            assertEquals(0, EXPORT.supplier(frame).getAsInt());
            binding.registerCurrentPack();
            frame.beginFrame();
            assertEquals(0, EXPORT.supplier(frame).getAsInt());
            assertEquals(AdapterRuntimeBinding.Reason.UNKNOWN_SOURCE, frame.binding().reason());
        }
    }

    @Test void duplicateDescriptorAndSourceDigestsAreRejectedAtomically() {
        var registration = new AdapterRegistration(PROTOCOLS);
        registration.register(DESCRIPTOR, List.of(SOURCE), List.of(EXPORT));
        assertThrows(IllegalArgumentException.class, () -> registration.register(DESCRIPTOR, List.of(), List.of()));
        var other = descriptor("example:other", CUSTOM);
        assertThrows(IllegalArgumentException.class, () -> registration.register(other,
                List.of(source(other, "a", Map.of())), List.of()));
        registration.register(other, List.of(source(other, "c", Map.of())), List.of());
        assertEquals(2, registration.seal().descriptors().descriptors().size());
        assertEquals(1, registration.seal().exports().size()); // Existing exact shared export is reused.
    }

    @Test void identityMismatchAndDuplicateSourcesWithinOneSubmissionLeaveNoPartialRegistration() {
        var registration = new AdapterRegistration(PROTOCOLS);
        var other = descriptor("example:other", CUSTOM);
        assertThrows(IllegalArgumentException.class, () -> registration.register(DESCRIPTOR,
                List.of(source(other, "a", Map.of())), List.of(EXPORT)));
        assertThrows(IllegalArgumentException.class, () -> registration.register(DESCRIPTOR,
                List.of(SOURCE, SOURCE), List.of(EXPORT)));
        registration.register(DESCRIPTOR, List.of(SOURCE), List.of(EXPORT));
        assertEquals(1, registration.seal().sources().size());
    }

    @Test void duplicateExportOperationOrNameAndUndeclaredOperationsAreRejected() {
        var registration = new AdapterRegistration(PROTOCOLS);
        assertThrows(IllegalArgumentException.class, () -> registration.register(DESCRIPTOR, List.of(SOURCE),
                List.of(EXPORT, EXPORT)));
        registration.register(DESCRIPTOR, List.of(SOURCE), List.of(EXPORT));
        var other = descriptor("example:other", CUSTOM);
        assertThrows(IllegalArgumentException.class, () -> registration.register(other, List.of(),
                List.of(new CapabilityExport(CUSTOM, "shaderlodAnotherAvailable"))));
        var operation = new WorkKey(Effect.REFLECTIONS, "example:another", WorkKind.COUNT);
        var another = descriptor("example:another", operation);
        assertThrows(IllegalArgumentException.class, () -> registration.register(another, List.of(),
                List.of(new CapabilityExport(operation, EXPORT.uniformName()))));
        assertThrows(IllegalArgumentException.class, () -> registration.register(another, List.of(), List.of(EXPORT)));
        assertEquals(1, registration.seal().descriptors().descriptors().size());
    }

    @Test void malformedOrReservedUniformNamesAreRejected() {
        for (String name : List.of("gl_example", "exampleAvailable", "shaderlod bad", "shaderlod", "shaderlodCaps[0]", "shaderlod__Reserved",
                "shaderlodCapabilitiesVersion", "shaderlodEffectsVersion", "shaderlodBridgeVersion",
                "shaderlodVolumetricsMode", "shaderlodReflectionsFarWorkRatio")) {
            assertThrows(IllegalArgumentException.class, () -> new CapabilityExport(CUSTOM, name), name);
        }
    }

    @Test void unknownIncompatibleOrMissingCapabilityProtocolIsRejected() {
        for (var requirement : List.of(new ProtocolRequirement("example:missing", Set.of(1)),
                new ProtocolRequirement("shaderlod:core", Set.of(3)))) {
            var descriptor = new AdapterDescriptor(DESCRIPTOR.identity(), List.of(requirement,
                    new ProtocolRequirement("shaderlod:capabilities", Set.of(1))), DESCRIPTOR.capabilities());
            assertThrows(IllegalArgumentException.class, () -> new AdapterRegistration(PROTOCOLS)
                    .register(descriptor, List.of(), List.of(EXPORT)));
        }
        var missing = new AdapterDescriptor(DESCRIPTOR.identity(), List.of(new ProtocolRequirement("shaderlod:core", Set.of(2))),
                DESCRIPTOR.capabilities());
        assertThrows(IllegalArgumentException.class, () -> new AdapterRegistration(PROTOCOLS)
                .register(missing, List.of(), List.of(EXPORT)));
        assertThrows(IllegalArgumentException.class, () -> new AdapterRegistration(Map.of("shaderlod:capabilities", 2)));
        assertThrows(IllegalArgumentException.class, () -> new AdapterRegistration(Map.of("shaderlod:capabilities", 1, "bad", 1)));
    }

    @Test void sealedSnapshotIsImmutableIdempotentAndClearlyRejectsLateRegistration() {
        var registration = new AdapterRegistration(PROTOCOLS);
        var sources = new ArrayList<>(List.of(SOURCE));
        var exports = new ArrayList<>(List.of(EXPORT));
        registration.register(DESCRIPTOR, sources, exports);
        sources.clear();
        exports.clear();
        var sealed = registration.seal();
        assertSame(sealed, registration.seal());
        assertEquals(1, sealed.sources().size());
        assertEquals(1, sealed.exports().size());
        assertThrows(UnsupportedOperationException.class, () -> sealed.exports().clear());
        assertThrows(UnsupportedOperationException.class, () -> sealed.sources().clear());
        assertThrows(UnsupportedOperationException.class, () -> sealed.protocols().clear());
        assertThrows(UnsupportedOperationException.class, () -> sealed.optionKeys().clear());
        var error = assertThrows(IllegalStateException.class, () -> registration.register(descriptor("example:late", CUSTOM),
                List.of(), List.of()));
        assertTrue(error.getMessage().contains("before the first Iris pipeline/uniform binding"));
    }

    @Test void actualProductionRegistrationAndMixinExportSecondAdapterInAnIsolatedClassLoader() throws Exception {
        // Do not modify the process-global production registry used by the existing schema-1 regression tests.
        var urls = new java.net.URL[]{AdapterExtensionTest.class.getProtectionDomain().getCodeSource().getLocation(),
                IrisAdapterRuntime.class.getProtectionDomain().getCodeSource().getLocation()};
        try (var loader = new URLClassLoader(urls, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("com.pp.shaderlod.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    var loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        }) {
            var probe = loader.loadClass("com.pp.shaderlod.AdapterExtensionTest$ProductionProbe");
            var values = (Map<?, ?>) probe.getMethod("run").invoke(null);
            assertEquals(Map.of("shaderlodCapabilitiesVersion", 1, "shaderlodVolumeIntegrationAvailable", 0,
                    "shaderlodOpaqueSpatialAvailable", 0,
                    EXPORT.uniformName(), 0), values);
        }
    }

    public static final class ProductionProbe {
        public static Map<String, Integer> run() throws Exception {
            // An early snapshot must not accidentally seal startup registration.
            IrisAdapterRuntime.snapshot();
            IrisAdapterRuntime.register(DESCRIPTOR, List.of(SOURCE), List.of(EXPORT));
            var suppliers = new LinkedHashMap<String, IntSupplier>();
            var holder = (UniformHolder) Proxy.newProxyInstance(UniformHolder.class.getClassLoader(),
                    new Class<?>[]{UniformHolder.class}, (proxy, method, args) -> {
                        if (!method.getName().equals("uniform1i") || args[0] != UniformUpdateFrequency.PER_FRAME)
                            throw new AssertionError("Unexpected uniform type/frequency");
                        if (suppliers.put((String) args[1], (IntSupplier) args[2]) != null)
                            throw new AssertionError("Duplicate uniform");
                        return proxy;
                    });
            var notifier = new FrameUpdateNotifier();
            for (String name : List.of("shaderlod$sealAdapters", "shaderlod$registerCapabilities")) {
                var callback = EffectUniformsMixin.class.getDeclaredMethod(name, UniformHolder.class,
                        FrameUpdateNotifier.class, PackDirectives.class, CallbackInfo.class);
                callback.setAccessible(true);
                callback.invoke(null, holder, notifier, null, new CallbackInfo("generalCommonUniforms", false));
            }
            try {
                IrisAdapterRuntime.register(descriptor("example:late", CUSTOM), List.of(), List.of());
                throw new AssertionError("Late registration was accepted");
            } catch (IllegalStateException expected) {
                if (!expected.getMessage().contains("sealed")) throw expected;
            }
            notifier.onNewFrame();
            var values = new LinkedHashMap<String, Integer>();
            suppliers.forEach((name, supplier) -> values.put(name, supplier.getAsInt()));
            return values;
        }
    }

    private static final class Access implements AdapterRuntimeBinding.RuntimeAccess {
        private final Object identity = new Object();
        private Map<String, String> options = Map.of("EXAMPLE_LOD", "true");
        @Override public Optional<AdapterRuntimeBinding.Pack> currentPack() {
            return Optional.of(new AdapterRuntimeBinding.Pack(identity, Path.of("unknown-name.zip"), options));
        }
        @Override public boolean fallback() { return false; }
        @Override public boolean pipelineAvailable() { return true; }
    }
}
