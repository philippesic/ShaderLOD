package com.pp.shaderlod.lod.adapter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable declarations for an explicitly pinned adapter, with no runtime discovery. */
public record AdapterDescriptor(AdapterIdentity identity, List<ProtocolRequirement> protocols,
                                List<WorkCapability> capabilities) {
    public AdapterDescriptor {
        Objects.requireNonNull(identity, "identity");
        protocols = List.copyOf(protocols);
        capabilities = List.copyOf(capabilities);
        if (protocols.isEmpty() || capabilities.isEmpty()) {
            throw new IllegalArgumentException("Declare required protocols and at least one work capability");
        }
        var protocolIds = new HashSet<String>();
        for (var protocol : protocols) {
            if (!protocolIds.add(protocol.id())) throw new IllegalArgumentException("Conflicting protocol " + protocol.id());
        }
        var workKeys = new HashSet<WorkKey>();
        for (var capability : capabilities) {
            if (!workKeys.add(capability.key())) throw new IllegalArgumentException("Conflicting work " + capability.key());
        }
    }
}
