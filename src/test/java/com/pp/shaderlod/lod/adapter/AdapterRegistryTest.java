package com.pp.shaderlod.lod.adapter;

import com.pp.shaderlod.lod.DistanceBands;
import com.pp.shaderlod.lod.Effect;
import com.pp.shaderlod.lod.EffectPolicy;
import com.pp.shaderlod.lod.WorkBudget;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.pp.shaderlod.lod.adapter.WorkNegotiation.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class AdapterRegistryTest {
    private static final AdapterIdentity ID = new AdapterIdentity("example:adapter", "revision-1");
    private static final WorkKey COUNT = new WorkKey(Effect.VOLUMETRICS, "example:integration", WorkKind.COUNT);
    private static final WorkKey RATE = new WorkKey(Effect.OPAQUE_LIGHTING, "example:evaluations", WorkKind.RATE);
    private static final DistanceBands BANDS = new DistanceBands(32, 96, 8);
    private static final EffectPolicy ACTIVE = new EffectPolicy(true, true, BANDS, .5, .25);
    private static final Map<String, Integer> VERSIONS = Map.of("shaderlod:core", 2, "shaderlod:effects", 2);

    private static AdapterDescriptor descriptor(WorkCapability... capabilities) {
        return new AdapterDescriptor(ID, List.of(
                new ProtocolRequirement("shaderlod:core", Set.of(2)),
                new ProtocolRequirement("shaderlod:effects", Set.of(1, 2))), List.of(capabilities));
    }

    private static AdapterRegistry counts(int floor) {
        return new AdapterRegistry().register(descriptor(new WorkCapability(COUNT, new WorkCapability.CountFloor(floor))));
    }

    private static WorkRequest request(WorkKey key, EffectPolicy policy, boolean enabled,
                                       double distance, int upstream, int floor) {
        return new WorkRequest(ID, VERSIONS, key, policy, enabled, distance, upstream, floor);
    }

    private static WorkRequest far(WorkKey key, int upstream) {
        return request(key, ACTIVE, true, 1000, upstream, 0);
    }

    @Test void extensionRegistrationIsImmutableAndSupportsExactRevisionSelection() {
        var first = counts(1);
        var extensionId = new AdapterIdentity("extension:adapter", "pinned-2");
        var extension = new AdapterDescriptor(extensionId, List.of(new ProtocolRequirement("extension:transport", Set.of(3))),
                List.of(new WorkCapability(RATE, new WorkCapability.DiscreteRates(Set.of(1.0, .25)))));
        var second = first.register(extension);
        assertEquals(1, first.descriptors().size());
        assertEquals(2, second.descriptors().size());
        assertThrows(UnsupportedOperationException.class, () -> second.descriptors().clear());
        var accepted = second.negotiate(new WorkRequest(extensionId, Map.of("extension:transport", 3), RATE,
                ACTIVE, true, 1000, 4, 0));
        assertEquals(1, accepted.negotiatedWorkLimit());
        assertEquals(POLICY_APPLIED, accepted.reason());
        var unpinned = second.negotiate(new WorkRequest(new AdapterIdentity(ID.id(), "revision-2"), VERSIONS,
                COUNT, ACTIVE, true, 1000, 4, 0));
        assertFull(unpinned, 4, UNREGISTERED_ADAPTER);
        assertEquals(3, second.register(new AdapterDescriptor(new AdapterIdentity(ID.id(), "revision-2"),
                extension.protocols(), extension.capabilities())).descriptors().size());
    }

    @Test void conflictingRegistrationsAndDeclarationsAreRejected() {
        var floor = new WorkCapability(COUNT, new WorkCapability.CountFloor(1));
        var registry = counts(1);
        assertThrows(IllegalArgumentException.class, () -> registry.register(descriptor(floor)));
        assertThrows(IllegalArgumentException.class, () -> descriptor(floor, floor));
        var protocol = new ProtocolRequirement("shaderlod:core", Set.of(2));
        assertThrows(IllegalArgumentException.class, () -> new AdapterDescriptor(ID, List.of(protocol, protocol), List.of(floor)));
    }

    @Test void descriptorsDefensivelyCopyAllDeclarationCollections() {
        var versions = new HashSet<>(Set.of(2));
        var protocol = new ProtocolRequirement("shaderlod:core", versions);
        versions.clear();
        assertEquals(Set.of(2), protocol.acceptedVersions());
        var rates = new HashSet<>(Set.of(1.0, .25));
        var domain = new WorkCapability.DiscreteRates(rates);
        rates.clear();
        assertEquals(Set.of(1.0, .25), domain.retainedRates());
        var protocols = new ArrayList<>(List.of(protocol));
        var capabilities = new ArrayList<>(List.of(new WorkCapability(RATE, domain)));
        var declaration = new AdapterDescriptor(ID, protocols, capabilities);
        protocols.clear();
        capabilities.clear();
        assertEquals(1, declaration.protocols().size());
        assertEquals(1, declaration.capabilities().size());
        assertThrows(UnsupportedOperationException.class, () -> declaration.capabilities().clear());
    }

    @Test void malformedIdentityProtocolAndDomainDeclarationsFailBeforeRegistration() {
        for (String key : new String[]{"adapter", "Example:adapter", "example:", " example:a"}) {
            assertThrows(IllegalArgumentException.class, () -> new AdapterIdentity(key, "revision"));
        }
        assertThrows(IllegalArgumentException.class, () -> new AdapterIdentity("example:a", " "));
        assertThrows(IllegalArgumentException.class, () -> new ProtocolRequirement("example:p", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProtocolRequirement("example:p", Set.of(0, 2)));
        assertThrows(IllegalArgumentException.class, () -> new WorkCapability.CountFloor(-1));
        for (Set<Double> rates : List.of(Set.of(.25), Set.of(1.0, -.1), Set.of(1.0, Double.NaN),
                Set.of(1.0, Double.POSITIVE_INFINITY), Set.of(1.0, 1.1))) {
            assertThrows(IllegalArgumentException.class, () -> new WorkCapability.DiscreteRates(rates));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new WorkCapability(COUNT, new WorkCapability.DiscreteRates(Set.of(1.0))));
        assertThrows(IllegalArgumentException.class, () -> new WorkCapability(RATE, new WorkCapability.CountFloor(1)));
        assertEquals("count", WorkKind.COUNT.key());
        assertEquals("rate", WorkKind.RATE.key());
    }

    @Test void missingUnknownAndNewerProtocolsRetainFullWorkWithDistinctReasons() {
        var registry = counts(1);
        assertFull(registry.negotiate(new WorkRequest(ID, Map.of("shaderlod:core", 2), COUNT,
                ACTIVE, true, 1000, 16, 0)), 16, MISSING_PROTOCOL);
        for (int version : new int[]{-1, 0, 1, 3, Integer.MAX_VALUE}) {
            assertFull(registry.negotiate(new WorkRequest(ID, Map.of("shaderlod:core", version, "shaderlod:effects", 2),
                    COUNT, ACTIVE, true, 1000, 16, 0)), 16, UNSUPPORTED_PROTOCOL);
        }
        assertEquals(4, registry.negotiate(new WorkRequest(ID, Map.of("shaderlod:core", 2, "shaderlod:effects", 1),
                COUNT, ACTIVE, true, 1000, 16, 0)).negotiatedWorkLimit());
    }

    @Test void effectOperationAndWorkKindMustBeDeclaredIndividually() {
        var registry = counts(1);
        assertFull(registry.negotiate(far(RATE, 16)), 16, UNSUPPORTED_EFFECT);
        assertFull(registry.negotiate(far(new WorkKey(COUNT.effect(), "example:other", WorkKind.COUNT), 16)),
                16, UNSUPPORTED_WORK);
        assertFull(registry.negotiate(far(new WorkKey(COUNT.effect(), COUNT.operation(), WorkKind.RATE), 16)),
                16, UNSUPPORTED_WORK);
    }

    @Test void countDecisionsRetainExistingCeilAndBothLegalFloors() {
        var registry = counts(4);
        var floor = registry.negotiate(far(COUNT, 9));
        assertEquals(.25, floor.requestedBudget().retentionRatio());
        assertEquals(4, floor.requestedBudget().workLimit());
        assertEquals(4, floor.negotiatedWorkLimit());
        assertEquals(6, registry.negotiate(request(COUNT, ACTIVE, true, 1000, 9, 6)).negotiatedWorkLimit());
        assertEquals(3, counts(0).negotiate(far(COUNT, 9)).negotiatedWorkLimit());
        assertFull(registry.negotiate(far(COUNT, 3)), 3, UNSUPPORTED_UPSTREAM_WORK);
        assertEquals(0, registry.negotiate(far(COUNT, 0)).negotiatedWorkLimit());
    }

    @Test void countBudgetMatchesTheExistingOracleAcrossDistancesAndLargeCounts() {
        var registry = counts(1);
        for (int upstream : new int[]{0, 1, 2, 9, 64, Integer.MAX_VALUE}) {
            int floor = upstream == 0 ? 0 : 1;
            for (double distance : new double[]{0, 28, 32, 36, 92, 96, 100, 1000}) {
                var result = registry.negotiate(request(COUNT, ACTIVE, true, distance, upstream, floor));
                assertEquals(ACTIVE.decide(true, COUNT.effect(), distance, upstream, floor), result.requestedBudget());
                assertEquals(result.requestedBudget().workLimit(), result.negotiatedWorkLimit());
                assertEquals(POLICY_APPLIED, result.reason());
            }
        }
    }

    @Test void ratesAcceptOnlyDeclaredRequestsAndDeclaredQuantizedCounts() {
        var registry = new AdapterRegistry().register(descriptor(
                new WorkCapability(RATE, new WorkCapability.DiscreteRates(Set.of(1.0, .25)))));
        assertEquals(1, registry.negotiate(far(RATE, 4)).negotiatedWorkLimit());
        assertFull(registry.negotiate(request(RATE, ACTIVE, true, 36, 4, 0)), 4, UNSUPPORTED_RATE);
        assertFull(registry.negotiate(request(RATE, ACTIVE, true, 96, 4, 0)), 4, UNSUPPORTED_RATE);
        assertFull(registry.negotiate(far(RATE, 9)), 9, UNSUPPORTED_RATE);
        assertFull(registry.negotiate(request(RATE, ACTIVE, true, 1000, 4, 2)), 4, UNSUPPORTED_RATE);
        assertEquals(4, registry.negotiate(request(RATE, ACTIVE, true, 0, 4, 0)).negotiatedWorkLimit());
    }

    @Test void zeroRateRequiresExplicitSkipCapability() {
        var skip = new EffectPolicy(true, true, BANDS, 0, 0);
        var registry = new AdapterRegistry().register(descriptor(new WorkCapability(RATE,
                new WorkCapability.DiscreteRates(Set.of(1.0, 0.0)))));
        assertEquals(0, registry.negotiate(request(RATE, skip, true, 1000, 16, 0)).negotiatedWorkLimit());
        assertFull(registry.negotiate(request(RATE, skip, true, 1000, 16, 1)), 16, UNSUPPORTED_RATE);
        assertEquals(0, counts(0).negotiate(request(COUNT, skip, true, 1000, 16, 0)).negotiatedWorkLimit());
        assertEquals(1, counts(1).negotiate(request(COUNT, skip, true, 1000, 16, 0)).negotiatedWorkLimit());
    }

    @Test void fallbackSeparatesRequestedCapsFromAcceptedWorkAndNeverClaimsRealizedSavings() {
        var result = new AdapterRegistry().negotiate(far(COUNT, 16));
        assertEquals(4, result.requestedBudget().workLimit());
        assertTrue(result.reductionRequested());
        assertFalse(result.reductionNegotiated());
        assertFull(result, 16, UNREGISTERED_ADAPTER);
        assertThrows(IllegalArgumentException.class, () -> new WorkNegotiation(result.requestedBudget(), 4, UNSUPPORTED_PROTOCOL));
        assertThrows(IllegalArgumentException.class, () -> new WorkNegotiation(result.requestedBudget(), 3, POLICY_APPLIED));
    }

    @Test void globalAndEffectDisablePrecedeCapabilityChecksAndRetainOriginal() {
        var registry = new AdapterRegistry();
        assertFull(registry.negotiate(request(COUNT, ACTIVE, false, 1000, 16, 0)), 16, GLOBAL_DISABLED);
        assertFull(registry.negotiate(request(COUNT, new EffectPolicy(false, true, BANDS, 0, 0), true,
                1000, 16, 0)), 16, EFFECT_DISABLED);
    }

    @Test void lightingProtectionCannotBeBypassedByRegistration() {
        for (Effect effect : new Effect[]{Effect.AMBIENT_OCCLUSION, Effect.SHADOW_FILTERING}) {
            var key = new WorkKey(effect, "example:lighting", WorkKind.COUNT);
            var registry = new AdapterRegistry().register(descriptor(new WorkCapability(key, new WorkCapability.CountFloor(0))));
            assertFull(registry.negotiate(far(key, 16)), 16, PROTECTED_LIGHTING);
            assertEquals(4, registry.negotiate(request(key, new EffectPolicy(true, false, BANDS, .5, .25),
                    true, 1000, 16, 0)).negotiatedWorkLimit());
        }
    }

    @Test void invalidRuntimeInputsRetainOriginalIncludingNegativeCounts() {
        var registry = counts(0);
        for (double distance : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertFull(registry.negotiate(request(COUNT, ACTIVE, true, distance, 16, 0)), 16, INVALID_RUNTIME_INPUT);
        }
        assertFull(registry.negotiate(far(COUNT, -3)), -3, INVALID_RUNTIME_INPUT);
        assertFull(registry.negotiate(request(COUNT, ACTIVE, true, 1000, 16, -1)), 16, INVALID_RUNTIME_INPUT);
        assertFull(registry.negotiate(request(COUNT, ACTIVE, true, 1000, 16, 17)), 16, INVALID_RUNTIME_INPUT);
        assertFull(registry.negotiate(request(null, ACTIVE, true, 1000, 16, 0)), 16, INVALID_RUNTIME_INPUT);
        assertFull(registry.negotiate(request(COUNT, null, true, 1000, 16, 0)), 16, INVALID_RUNTIME_INPUT);
        assertFull(registry.negotiate(new WorkRequest(null, VERSIONS, COUNT, ACTIVE, true, 1000, 16, 0)),
                16, UNREGISTERED_ADAPTER);
        assertFull(registry.negotiate(new WorkRequest(ID, null, COUNT, ACTIVE, true, 1000, 16, 0)),
                16, MISSING_PROTOCOL);
        var nullVersion = new HashMap<>(VERSIONS);
        nullVersion.put("shaderlod:core", null);
        var missing = new WorkRequest(ID, nullVersion, COUNT, ACTIVE, true, 1000, 16, 0);
        nullVersion.put("shaderlod:core", 2);
        assertFull(registry.negotiate(missing), 16, MISSING_PROTOCOL);
        assertThrows(UnsupportedOperationException.class, () -> missing.protocolVersions().clear());
        assertThrows(NullPointerException.class, () -> registry.negotiate(null));
    }

    private static void assertFull(WorkNegotiation result, int upstream, WorkNegotiation.Reason reason) {
        assertEquals(upstream, result.requestedBudget().upstreamWork());
        assertEquals(upstream, result.negotiatedWorkLimit());
        assertEquals(reason, result.reason());
        assertFalse(result.reductionNegotiated());
    }
}
