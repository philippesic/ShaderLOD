package com.pp.shaderlod.integration;

import com.pp.shaderlod.lod.adapter.AdapterDescriptor;
import com.pp.shaderlod.lod.adapter.AdapterRegistry;
import com.pp.shaderlod.lod.adapter.AdapterIdentity;
import com.pp.shaderlod.lod.adapter.WorkKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit startup composition; no discovery, source reads, shader rewriting or support inference. */
public final class AdapterRegistration {
    public record Sealed(AdapterRegistry descriptors, List<SourceBinding> sources,
                         List<CapabilityExport> exports, Map<String, Integer> protocols, Set<String> optionKeys,
                         Map<AdapterIdentity, RuntimeLayoutRequirement> layouts) {
        public Sealed(AdapterRegistry descriptors, List<SourceBinding> sources,
                      List<CapabilityExport> exports, Map<String, Integer> protocols, Set<String> optionKeys) {
            this(descriptors, sources, exports, protocols, optionKeys, Map.of());
        }

        public Sealed {
            Objects.requireNonNull(descriptors, "descriptors");
            sources = List.copyOf(sources);
            exports = List.copyOf(exports);
            protocols = Map.copyOf(protocols);
            optionKeys = Set.copyOf(optionKeys);
            layouts = Map.copyOf(layouts);
        }

        public AdapterRuntimeBinding bind(AdapterRuntimeBinding.RuntimeAccess access,
                                          AdapterRuntimeBinding.SourceReader reader) {
            return new AdapterRuntimeBinding(descriptors, sources, protocols, access, reader, layouts);
        }
    }

    private final Map<String, Integer> protocols;
    private AdapterRegistry descriptors = new AdapterRegistry();
    private List<SourceBinding> sources = List.of();
    private Map<WorkKey, CapabilityExport> exports = Map.of();
    private Map<AdapterIdentity, RuntimeLayoutRequirement> layouts = Map.of();
    private Sealed sealed;

    public AdapterRegistration(Map<String, Integer> protocols) {
        this.protocols = Map.copyOf(protocols);
        this.protocols.forEach((id, version) -> {
            // Reuse the contract's namespaced-id and positive-version checks.
            new com.pp.shaderlod.lod.adapter.ProtocolRequirement(id, Set.of(version));
        });
        if (!Objects.equals(this.protocols.get("shaderlod:capabilities"), AdapterRuntimeBinding.CAPABILITIES_VERSION)) {
            throw new IllegalArgumentException("Capability exports require transport schema 1");
        }
    }

    /** Atomic registration. Existing shared exports may be reused by omitting them from newExports. */
    public synchronized void register(AdapterDescriptor descriptor, List<SourceBinding> sourceBindings,
                                      List<CapabilityExport> newExports) {
        register(descriptor, sourceBindings, newExports, null);
    }

    /** An optional layout gate is checked at the shared frame snapshot, including after resize. */
    public synchronized void register(AdapterDescriptor descriptor, List<SourceBinding> sourceBindings,
                                      List<CapabilityExport> newExports, RuntimeLayoutRequirement layout) {
        if (sealed != null) {
            throw new IllegalStateException("Adapter registration is sealed: register before the first Iris pipeline/uniform binding");
        }
        Objects.requireNonNull(descriptor, "descriptor");
        var extendedDescriptors = descriptors.register(descriptor);
        var suppliedSources = List.copyOf(sourceBindings);
        var suppliedExports = List.copyOf(newExports);
        for (var requirement : descriptor.protocols()) {
            var version = protocols.get(requirement.id());
            if (version == null || !requirement.acceptedVersions().contains(version)) {
                throw new IllegalArgumentException("Unavailable or incompatible runtime protocol: " + requirement.id());
            }
        }
        if (descriptor.protocols().stream().noneMatch(requirement -> requirement.id().equals("shaderlod:capabilities"))) {
            throw new IllegalArgumentException("Declare the shaderlod:capabilities protocol explicitly");
        }
        var extendedSources = new ArrayList<>(sources);
        for (var source : suppliedSources) {
            if (!descriptor.identity().equals(source.adapter())) {
                throw new IllegalArgumentException("Source binding identity does not match descriptor: " + source.adapter());
            }
            if (extendedSources.stream().anyMatch(existing -> existing.sourceSha256().equals(source.sourceSha256()))) {
                throw new IllegalArgumentException("Source digest already bound: " + source.sourceSha256());
            }
            extendedSources.add(source);
        }
        var extendedExports = new LinkedHashMap<>(exports);
        for (var export : suppliedExports) {
            if (descriptor.capabilities().stream().noneMatch(capability -> capability.key().equals(export.operation()))) {
                throw new IllegalArgumentException("Export operation is not declared by this adapter: " + export.operation());
            }
            if (extendedExports.containsKey(export.operation()) || extendedExports.values().stream()
                    .anyMatch(existing -> existing.uniformName().equals(export.uniformName()))) {
                throw new IllegalArgumentException("Duplicate or conflicting capability export: " + export);
            }
            extendedExports.put(export.operation(), export);
        }
        var extendedLayouts = new LinkedHashMap<>(layouts);
        if (layout != null) extendedLayouts.put(descriptor.identity(), layout);
        descriptors = extendedDescriptors;
        sources = List.copyOf(extendedSources);
        exports = java.util.Collections.unmodifiableMap(extendedExports);
        layouts = Map.copyOf(extendedLayouts);
    }

    /** Idempotent immutable freeze, taken before any pipeline supplier is registered. */
    public synchronized Sealed seal() {
        if (sealed == null) {
            var optionKeys = sources.stream().flatMap(source -> source.requiredOptions().keySet().stream())
                    .collect(Collectors.toUnmodifiableSet());
            sealed = new Sealed(descriptors, sources, List.copyOf(exports.values()), protocols, optionKeys, layouts);
        }
        return sealed;
    }
}
