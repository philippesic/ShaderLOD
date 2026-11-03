package com.pp.shaderlod;

import com.pp.shaderlod.integration.*;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.adapter.*;
import com.pp.shaderlod.lod.adapter.layout.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpatialReferenceBindingTest {
    private static final Map<String, Integer> PROTOCOLS = Map.of("shaderlod:core", 2,
            "shaderlod:effects", 2, "shaderlod:capabilities", 1);
    private static final WorkKey WORK = PhotonSpatialReferenceAdapter.OPAQUE_SPATIAL_IRRADIANCE;
    private static final SourceBinding SOURCE = PhotonSpatialReferenceAdapter.sourceBindings().getFirst();

    private static final class Access implements AdapterRuntimeBinding.RuntimeAccess {
        Object identity = new Object();
        Path source = Path.of("display-name-is-not-evidence.zip");
        Map<String, String> options = supportedOptions();
        Dimensions dimensions = new Dimensions(2976, 1672);
        boolean fallback;
        @Override public Optional<AdapterRuntimeBinding.Pack> currentPack() {
            return Optional.of(new AdapterRuntimeBinding.Pack(identity, source, options));
        }
        @Override public boolean fallback() { return fallback; }
        @Override public boolean pipelineAvailable() { return true; }
        @Override public Optional<Dimensions> renderedSurface() { return Optional.ofNullable(dimensions); }
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

    @Test void exactDescriptorPinsRateDomainProtocolsAndOneNamespacedExport() {
        var descriptor = PhotonSpatialReferenceAdapter.descriptor();
        assertEquals("shaderlod:photon-shared-spatial", descriptor.identity().id());
        assertEquals(Effect.OPAQUE_LIGHTING, WORK.effect());
        assertEquals(WorkKind.RATE, WORK.kind());
        assertEquals(Set.of(1.0, 0.25), ((WorkCapability.DiscreteRates) descriptor.capabilities().getFirst().domain()).retainedRates());
        assertEquals(List.of(new ProtocolRequirement("shaderlod:core", Set.of(2)),
                new ProtocolRequirement("shaderlod:effects", Set.of(2)),
                new ProtocolRequirement("shaderlod:capabilities", Set.of(1))), descriptor.protocols());
        assertEquals(List.of(new CapabilityExport(WORK, "shaderlodOpaqueSpatialAvailable")), PhotonSpatialReferenceAdapter.exports());
        assertThrows(IllegalArgumentException.class, () -> new WorkCapability(WORK, new WorkCapability.CountFloor(1)));
        assertThrows(IllegalArgumentException.class, () -> new WorkCapability.DiscreteRates(Set.of(0.25)));
        assertThrows(IllegalArgumentException.class, () -> new WorkCapability.DiscreteRates(Set.of(1.0, Double.NaN)));
    }

    @Test void unknownHistoricalAndEveryChangedMemberRemainFull() {
        for (var source : PhotonSpatialReferenceAdapter.sourceBindings()) {
            for (var hash : List.of("0".repeat(64),
                    "a8c004f5be0b341ed117220da0d2e2a0dadeefd62fd899ff64a3100c19a54ceb",
                    "c2790b61bd4d096e24eedbf9ca800d35541ca81e8ec5efeecec122b764dbf6f0")) {
                assertUnknown(new SourceFingerprint(hash, source.memberSha256()));
            }
            assertUnknown(new SourceFingerprint(source.sourceSha256(), Map.of()));
            for (var member : source.memberSha256().keySet()) {
                var members = new LinkedHashMap<>(source.memberSha256());
                members.put(member, "0".repeat(64));
                assertUnknown(new SourceFingerprint(source.sourceSha256(), members));
                members.remove(member);
                assertUnknown(new SourceFingerprint(source.sourceSha256(), members));
            }
        }
    }

    private static void assertUnknown(SourceFingerprint fingerprint) {
        var binding = registration().seal().bind(new Access(), ignored -> fingerprint);
        binding.registerCurrentPack();
        assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.UNKNOWN_SOURCE);
    }

    @Test void everyMissingAndUnsupportedOptionFailsFullWithoutRehashing() {
        var access = new Access();
        var reads = new AtomicInteger();
        var binding = registration().seal().bind(access, ignored -> {
            reads.incrementAndGet();
            return new SourceFingerprint(SOURCE.sourceSha256(), SOURCE.memberSha256());
        });
        binding.registerCurrentPack();
        assertTrue(binding.snapshot().available(WORK));
        for (var key : SOURCE.requiredOptions().keySet()) {
            var values = new LinkedHashMap<>(supportedOptions());
            values.remove(key);
            access.options = values;
            assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.REQUIRED_OPTION_MISSING_OR_UNSUPPORTED);
            values.put(key, key.equals(PhotonSpatialReferenceAdapter.PRIVATE_OPTION) ? "false" : "true");
            assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.REQUIRED_OPTION_MISSING_OR_UNSUPPORTED);
        }
        access.options = supportedOptions();
        assertTrue(binding.snapshot().available(WORK));
        assertEquals(1, reads.get());
    }

    @Test void mixedFormatLayoutHasExactProducerConsumerAndInterleavedViews() {
        var layout = PhotonSpatialReferenceAdapter.layout();
        assertEquals(List.of(TargetFormat.R11F_G11F_B10F, TargetFormat.RGBA32F, TargetFormat.RGBA8),
                layout.targets().stream().map(TargetDeclaration::format).toList());
        assertEquals(3, layout.targets().getFirst().quantities().getFirst().channels());
        assertEquals(TargetDeclaration.Role.GENERAL, layout.targets().getFirst().quantities().getFirst().role());
        assertEquals(3, layout.targets().get(1).quantities().size());
        assertEquals(1L << 24, layout.targets().get(1).quantities().get(1).maximumIntegerMagnitude());
        assertEquals(List.of(PhotonSpatialReferenceAdapter.COMPOSITION, PhotonSpatialReferenceAdapter.CACHE),
                layout.passes().getFirst().draws().getFirst().attachments());
        assertEquals(List.of(PhotonSpatialReferenceAdapter.COMPOSITION, PhotonSpatialReferenceAdapter.AUXILIARY),
                layout.passes().get(1).draws().getFirst().attachments());
        assertTrue(layout.passes().stream().allMatch(pass ->
                pass.feedbackPolicy() == PassDeclaration.FeedbackPolicy.DISTINCT_IMAGES_EXTERNALLY_VERIFIED));
        for (var dimensions : List.of(new Dimensions(2976, 1672), new Dimensions(1709, 961), new Dimensions(8192, 8192))) {
            var acceptance = layout.validate(dimensions);
            assertTrue(acceptance.valid());
            assertTrue(PhotonSpatialReferenceAdapter.acceptsRenderedDimensions(dimensions));
            assertTrue(acceptance.targetDimensions().values().stream().allMatch(dimensions::equals));
        }
    }

    @Test void unavailableOrUnsupportedSurfaceFailsFullAndResizeRequalifies() {
        var access = new Access();
        var reads = new AtomicInteger();
        var binding = registration().seal().bind(access, ignored -> {
            reads.incrementAndGet();
            return new SourceFingerprint(SOURCE.sourceSha256(), SOURCE.memberSha256());
        });
        binding.registerCurrentPack();
        for (int repeat = 0; repeat < 3; repeat++) assertTrue(binding.snapshot().available(WORK));
        access.dimensions = null;
        assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.LAYOUT_DIMENSIONS_UNAVAILABLE);
        for (var invalid : List.of(new Dimensions(1, 961), new Dimensions(1709, 1),
                new Dimensions(8193, 8192), new Dimensions(8192, 8193))) {
            access.dimensions = invalid;
            assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.LAYOUT_INCOMPATIBLE);
        }
        access.dimensions = new Dimensions(1709, 961);
        assertTrue(binding.snapshot().available(WORK));
        access.fallback = true;
        assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.FALLBACK);
        assertEquals(1, reads.get());
    }

    @Test void malformedDeclarationFailsGateAndNoLayoutRegistrationKeepsExistingSemantics() {
        var valid = PhotonSpatialReferenceAdapter.layout();
        var bad = new AdapterLayout(valid.targets(), List.of(new PassDeclaration("test:wrong_viewport", List.of(),
                List.of(new PassDeclaration.DrawGroup(List.of(PhotonSpatialReferenceAdapter.COMPOSITION,
                        PhotonSpatialReferenceAdapter.CACHE), TargetSize.relative(1, 2, TargetSize.Rounding.CEIL))),
                PassDeclaration.FeedbackPolicy.UNSUPPORTED)));
        var badRequirement = new RuntimeLayoutRequirement(bad, new Dimensions(2, 2), new Dimensions(8192, 8192));
        var registration = new AdapterRegistration(PROTOCOLS);
        registration.register(PhotonSpatialReferenceAdapter.descriptor(), List.of(SOURCE),
                PhotonSpatialReferenceAdapter.exports(), badRequirement);
        var access = new Access();
        var binding = registration.seal().bind(access, ignored -> new SourceFingerprint(SOURCE.sourceSha256(), SOURCE.memberSha256()));
        binding.registerCurrentPack();
        assertFull(binding.snapshot(), AdapterRuntimeBinding.Reason.LAYOUT_INCOMPATIBLE);
        assertThrows(IllegalArgumentException.class, () -> new RuntimeLayoutRequirement(valid,
                new Dimensions(8192, 2), new Dimensions(2, 2)));
        assertThrows(IllegalArgumentException.class, () -> new TargetDeclaration.Quantity("test:invalid_integer",
                TargetDeclaration.Role.EXACT_BINARY32_BOUNDED_INTEGER_METADATA, 3, (1L << 24) + 1));
        var badStorage = new TargetDeclaration(PhotonSpatialReferenceAdapter.CACHE,
                valid.targets().get(1).size(), TargetFormat.R11F_G11F_B10F, valid.targets().get(1).quantities());
        assertFalse(new AdapterLayout(List.of(valid.targets().getFirst(), badStorage, valid.targets().get(2)),
                valid.passes()).validate(new Dimensions(1709, 961)).valid());
        var legacy = new AdapterRegistration(PROTOCOLS);
        legacy.register(PhotonSpatialReferenceAdapter.descriptor(), List.of(SOURCE), PhotonSpatialReferenceAdapter.exports());
        access.dimensions = null;
        var legacyBinding = legacy.seal().bind(access, ignored -> new SourceFingerprint(SOURCE.sourceSha256(), SOURCE.memberSha256()));
        legacyBinding.registerCurrentPack();
        assertTrue(legacyBinding.snapshot().available(WORK));
    }

    @Test void conflictingSourceExportIdentityAndProtocolRegistrationsAreAtomic() {
        var registration = registration();
        assertThrows(IllegalArgumentException.class, () -> registration.register(PhotonSpatialReferenceAdapter.descriptor(),
                PhotonSpatialReferenceAdapter.sourceBindings(), PhotonSpatialReferenceAdapter.exports()));
        var other = new AdapterDescriptor(new AdapterIdentity("example:external", "r1"),
                PhotonSpatialReferenceAdapter.descriptor().protocols(), PhotonSpatialReferenceAdapter.descriptor().capabilities());
        assertThrows(IllegalArgumentException.class, () -> registration.register(other,
                List.of(new SourceBinding(other.identity(), SOURCE.sourceSha256(), SOURCE.memberSha256(), SOURCE.requiredOptions())), List.of()));
        assertThrows(IllegalArgumentException.class, () -> registration.register(other, List.of(), PhotonSpatialReferenceAdapter.exports()));
        assertThrows(IllegalArgumentException.class, () -> new AdapterRegistration(Map.of("shaderlod:core", 3,
                "shaderlod:effects", 2, "shaderlod:capabilities", 1)).register(PhotonSpatialReferenceAdapter.descriptor(),
                List.of(SOURCE), PhotonSpatialReferenceAdapter.exports()));
        var sealed = registration.seal();
        assertEquals(1, sealed.descriptors().descriptors().size());
        assertEquals(1, sealed.layouts().size());
        assertEquals(2, sealed.sources().size());
        assertThrows(UnsupportedOperationException.class, () -> sealed.layouts().clear());
    }

    @Test void actualReferenceCompositionPreservesVolumeAndExternalRegistration() throws Exception {
        var composition = Class.forName("com.pp.shaderlod.integration.IrisAdapterRuntime$ReferenceComposition");
        var create = composition.getDeclaredMethod("create");
        create.setAccessible(true);
        var registration = (AdapterRegistration) create.invoke(null);
        var externalWork = new WorkKey(Effect.REFLECTIONS, "example:external_count", WorkKind.COUNT);
        var external = new AdapterDescriptor(new AdapterIdentity("example:external", "r1"),
                PhotonSpatialReferenceAdapter.descriptor().protocols(),
                List.of(new WorkCapability(externalWork, new WorkCapability.CountFloor(1))));
        registration.register(external, List.of(), List.of(new CapabilityExport(externalWork, "shaderlodExternalAvailable")));
        var sealed = registration.seal();
        assertEquals(3, sealed.descriptors().descriptors().size());
        assertEquals(3, sealed.exports().size());
        assertEquals(Set.of(PhotonSpatialReferenceAdapter.IDENTITY), sealed.layouts().keySet());
        assertEquals(PhotonReferenceAdapter.sourceBindings().size() + 2, sealed.sources().size());
        assertTrue(sealed.exports().stream().anyMatch(export -> export.operation().equals(LodOperations.VOLUME_INTEGRATION)));
    }

    private static void assertFull(AdapterRuntimeBinding.Snapshot snapshot, AdapterRuntimeBinding.Reason reason) {
        assertEquals(reason, snapshot.reason());
        assertFalse(snapshot.available(WORK));
        assertNull(snapshot.descriptor());
        assertTrue(snapshot.operations().isEmpty());
    }
}
