package com.pp.shaderlod.integration;

import com.pp.shaderlod.lod.adapter.AdapterDescriptor;
import java.util.List;
import java.util.Map;

/** Production composition of explicit adapter declarations and separately pinned source bindings. */
public final class IrisAdapterRuntime {
    private static final AdapterRegistration REGISTRATION = ReferenceComposition.create();
    private static volatile AdapterRuntimeBinding binding;
    private static AdapterRegistration.Sealed sealed;

    private IrisAdapterRuntime() {}

    /** Call during client startup, before Iris begins its first common-uniform/pipeline registration. */
    public static void register(AdapterDescriptor descriptor, List<SourceBinding> sources,
                                List<CapabilityExport> exports) {
        REGISTRATION.register(descriptor, sources, exports);
    }

    private static synchronized AdapterRuntimeBinding runtime() {
        if (binding == null) {
            sealed = REGISTRATION.seal();
            binding = sealed.bind(new IrisRuntimeAccess(sealed.optionKeys()), PackSourceReader::read);
        }
        return binding;
    }

    public static void registerCurrentPack() { runtime().registerCurrentPack(); }

    /** Called during uniform binding; the list cannot change on later pipelines or reloads. */
    public static synchronized List<CapabilityExport> capabilityExports() {
        runtime();
        return sealed.exports();
    }

    public static AdapterRuntimeBinding.Snapshot snapshot() {
        var current = binding;
        return current == null ? AdapterRuntimeBinding.Snapshot.unavailable(AdapterRuntimeBinding.Reason.UNVALIDATED_PACK)
                : current.snapshot();
    }

    /** Reference registration is composition only; generic lifecycle/resolution has no pack-specific branches. */
    private static final class ReferenceComposition {
        private static AdapterRegistration create() {
            var registration = new AdapterRegistration(Map.of("shaderlod:core", 2, "shaderlod:effects", 2,
                    "shaderlod:capabilities", AdapterRuntimeBinding.CAPABILITIES_VERSION));
            registration.register(PhotonReferenceAdapter.descriptor(), PhotonReferenceAdapter.sourceBindings(),
                    List.of(new CapabilityExport(LodOperations.VOLUME_INTEGRATION, "shaderlodVolumeIntegrationAvailable")));
            return registration;
        }
    }
}
