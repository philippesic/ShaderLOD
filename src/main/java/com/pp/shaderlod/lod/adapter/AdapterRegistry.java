package com.pp.shaderlod.lod.adapter;

import com.pp.shaderlod.lod.WorkBudget;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import static com.pp.shaderlod.lod.adapter.WorkNegotiation.Reason;

/** Immutable extensible registrations. Registration and negotiation never load or rewrite shaders. */
public final class AdapterRegistry {
    private final Map<AdapterIdentity, AdapterDescriptor> descriptors;

    public AdapterRegistry() { descriptors = Map.of(); }

    private AdapterRegistry(Map<AdapterIdentity, AdapterDescriptor> descriptors) {
        this.descriptors = Collections.unmodifiableMap(new LinkedHashMap<>(descriptors));
    }

    public Map<AdapterIdentity, AdapterDescriptor> descriptors() { return descriptors; }

    /** Returns a new snapshot; duplicate exact identities are errors even for identical declarations. */
    public AdapterRegistry register(AdapterDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptors.containsKey(descriptor.identity())) {
            throw new IllegalArgumentException("Adapter already registered: " + descriptor.identity());
        }
        var extended = new LinkedHashMap<>(descriptors);
        extended.put(descriptor.identity(), descriptor);
        return new AdapterRegistry(extended);
    }

    public WorkNegotiation negotiate(WorkRequest request) {
        Objects.requireNonNull(request, "request");
        WorkBudget requested = request.policy() == null || request.work() == null
                ? new WorkBudget(request.upstreamWork(), request.upstreamWork(), 1, WorkBudget.Reason.INVALID_RUNTIME_INPUT)
                : request.policy().decide(request.globalEnabled(), request.work().effect(), request.distanceBlocks(),
                        request.upstreamWork(), request.minimumRetainedWork());
        if (requested.reason() != WorkBudget.Reason.POLICY) {
            return fallback(requested, Reason.valueOf(requested.reason().name()));
        }
        var descriptor = descriptors.get(request.adapter());
        if (descriptor == null) return fallback(requested, Reason.UNREGISTERED_ADAPTER);
        for (var protocol : descriptor.protocols()) {
            Integer version = request.protocolVersions().get(protocol.id());
            if (version == null) return fallback(requested, Reason.MISSING_PROTOCOL);
            if (!protocol.acceptedVersions().contains(version)) return fallback(requested, Reason.UNSUPPORTED_PROTOCOL);
        }
        if (descriptor.capabilities().stream().noneMatch(capability -> capability.key().effect() == request.work().effect())) {
            return fallback(requested, Reason.UNSUPPORTED_EFFECT);
        }
        var capability = descriptor.capabilities().stream()
                .filter(candidate -> candidate.key().equals(request.work())).findFirst().orElse(null);
        if (capability == null) return fallback(requested, Reason.UNSUPPORTED_WORK);
        if (capability.domain() instanceof WorkCapability.CountFloor floor) {
            // A zero upstream count has no operation to retain. Positive counts must satisfy the declaration.
            if (request.upstreamWork() > 0 && floor.minimumRetainedWork() > request.upstreamWork()) {
                return fallback(requested, Reason.UNSUPPORTED_UPSTREAM_WORK);
            }
            int minimum = request.upstreamWork() == 0 ? 0
                    : Math.max(request.minimumRetainedWork(), floor.minimumRetainedWork());
            requested = request.policy().decide(request.globalEnabled(), request.work().effect(),
                    request.distanceBlocks(), request.upstreamWork(), minimum);
        } else if (capability.domain() instanceof WorkCapability.DiscreteRates rates) {
            double quantizedRate = request.upstreamWork() == 0 ? 1
                    : (double) requested.workLimit() / request.upstreamWork();
            if (!rates.retainedRates().contains(requested.retentionRatio())
                    || !rates.retainedRates().contains(quantizedRate)) {
                return fallback(requested, Reason.UNSUPPORTED_RATE);
            }
        }
        return new WorkNegotiation(requested, requested.workLimit(), Reason.POLICY_APPLIED);
    }

    private static WorkNegotiation fallback(WorkBudget budget, Reason reason) {
        return new WorkNegotiation(budget, budget.upstreamWork(), reason);
    }
}
