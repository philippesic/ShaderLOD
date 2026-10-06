package com.pp.shaderlod.integration;

import com.pp.shaderlod.lod.adapter.AdapterDescriptor;
import com.pp.shaderlod.lod.adapter.AdapterIdentity;
import com.pp.shaderlod.lod.adapter.AdapterRegistry;
import com.pp.shaderlod.lod.adapter.WorkKey;
import com.pp.shaderlod.lod.adapter.layout.Dimensions;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.ServiceConfigurationError;

/** Explicit source resolution at registration, separate from live pipeline availability. */
public final class AdapterRuntimeBinding {
    public static final int CAPABILITIES_VERSION = 1;

    /** The identity must be the actual ShaderPack object; a display name is only a source locator. */
    public record Pack(Object identity, Path source, Map<String, String> effectiveOptions) {
        public Pack {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(source, "source");
            effectiveOptions = Map.copyOf(effectiveOptions);
        }
    }

    public interface RuntimeAccess {
        Optional<Pack> currentPack();
        boolean fallback();
        boolean pipelineAvailable();
        /** Actual rendered image dimensions, never window/display dimensions. Unavailable is fail-full. */
        default Optional<Dimensions> renderedSurface() { return Optional.empty(); }
    }

    @FunctionalInterface public interface SourceReader { SourceFingerprint read(Path source) throws IOException; }

    public enum Reason { AVAILABLE, NO_PACK, UNVALIDATED_PACK, UNKNOWN_SOURCE, SOURCE_UNREADABLE,
        AMBIGUOUS_SOURCE, UNREGISTERED_ADAPTER, MISSING_PROTOCOL, UNSUPPORTED_PROTOCOL,
        REQUIRED_OPTION_MISSING_OR_UNSUPPORTED, LAYOUT_DIMENSIONS_UNAVAILABLE, LAYOUT_INCOMPATIBLE,
        FALLBACK, PIPELINE_UNAVAILABLE, RUNTIME_UNAVAILABLE }

    /** Eligible declaration is not proof that a linked program consumes any of these operations. */
    public record Snapshot(Reason reason, AdapterDescriptor descriptor, Set<WorkKey> operations) {
        public Snapshot { operations = Set.copyOf(operations); }
        public boolean available(WorkKey work) { return reason == Reason.AVAILABLE && operations.contains(work); }
        public static Snapshot unavailable(Reason reason) { return new Snapshot(reason, null, Set.of()); }
    }

    private static final class Validated {
        private final WeakReference<Object> identity;
        private final SourceBinding binding;
        private final Snapshot selection;
        private Dimensions layoutDimensions;
        private boolean layoutAccepted;

        private Validated(Object identity, SourceBinding binding, Snapshot selection) {
            this.identity = new WeakReference<>(identity);
            this.binding = binding;
            this.selection = selection;
        }
    }
    private final AdapterRegistry descriptors;
    private final List<SourceBinding> bindings;
    private final Map<String, Integer> protocols;
    private final RuntimeAccess access;
    private final SourceReader sourceReader;
    private final Map<AdapterIdentity, RuntimeLayoutRequirement> layouts;
    private final List<Validated> cache = new ArrayList<>();

    public AdapterRuntimeBinding(AdapterRegistry descriptors, List<SourceBinding> bindings,
                                 Map<String, Integer> protocols, RuntimeAccess access, SourceReader sourceReader) {
        this(descriptors, bindings, protocols, access, sourceReader, Map.of());
    }

    public AdapterRuntimeBinding(AdapterRegistry descriptors, List<SourceBinding> bindings,
                                 Map<String, Integer> protocols, RuntimeAccess access, SourceReader sourceReader,
                                 Map<AdapterIdentity, RuntimeLayoutRequirement> layouts) {
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.bindings = List.copyOf(bindings);
        this.protocols = Map.copyOf(protocols);
        this.access = Objects.requireNonNull(access, "access");
        this.sourceReader = Objects.requireNonNull(sourceReader, "sourceReader");
        this.layouts = Map.copyOf(layouts);
        if (!descriptors.descriptors().keySet().containsAll(this.layouts.keySet())) {
            throw new IllegalArgumentException("Layout requires a registered adapter identity");
        }
    }

    /** Called only by generalCommonUniforms registration; never gate pipeline construction on its readiness. */
    public synchronized void registerCurrentPack() {
        try {
            var pack = access.currentPack().orElse(null);
            if (pack == null || find(pack.identity()) != null) return;
            SourceBinding binding = null;
            Snapshot selection;
            try {
                var matches = bindings.isEmpty() ? List.<SourceBinding>of() : matchingSources(pack);
                if (matches.size() != 1) {
                    selection = Snapshot.unavailable(matches.isEmpty() ? Reason.UNKNOWN_SOURCE : Reason.AMBIGUOUS_SOURCE);
                } else {
                    binding = matches.getFirst();
                    selection = select(binding);
                }
            } catch (IOException | IllegalArgumentException exception) {
                selection = Snapshot.unavailable(Reason.SOURCE_UNREADABLE);
            }
            cache.add(new Validated(pack.identity(), binding, selection));
        } catch (RuntimeException | LinkageError | ServiceConfigurationError exception) {
            // A missing/transitioning Iris runtime cannot enable work reduction.
        }
    }

    /** Cheap frame-boundary read: no source hash, no I/O and no new pack validation. */
    public synchronized Snapshot snapshot() {
        if (cache.isEmpty()) return Snapshot.unavailable(Reason.UNVALIDATED_PACK);
        try {
            var pack = access.currentPack().orElse(null);
            if (pack == null) return Snapshot.unavailable(Reason.NO_PACK);
            var validated = find(pack.identity());
            if (validated == null) return Snapshot.unavailable(Reason.UNVALIDATED_PACK);
            if (access.fallback()) return Snapshot.unavailable(Reason.FALLBACK);
            if (!access.pipelineAvailable()) return Snapshot.unavailable(Reason.PIPELINE_UNAVAILABLE);
            if (validated.selection.reason() != Reason.AVAILABLE) return validated.selection;
            if (!validated.binding.acceptsOptions(pack.effectiveOptions())) {
                return Snapshot.unavailable(Reason.REQUIRED_OPTION_MISSING_OR_UNSUPPORTED);
            }
            var layout = layouts.get(validated.binding.adapter());
            if (layout != null) {
                var dimensions = access.renderedSurface().orElse(null);
                if (dimensions == null) return Snapshot.unavailable(Reason.LAYOUT_DIMENSIONS_UNAVAILABLE);
                if (!dimensions.equals(validated.layoutDimensions)) {
                    validated.layoutAccepted = layout.accepts(dimensions);
                    validated.layoutDimensions = dimensions;
                }
                if (!validated.layoutAccepted) return Snapshot.unavailable(Reason.LAYOUT_INCOMPATIBLE);
            }
            return validated.selection;
        } catch (RuntimeException | LinkageError | ServiceConfigurationError exception) {
            return Snapshot.unavailable(Reason.RUNTIME_UNAVAILABLE);
        }
    }

    private List<SourceBinding> matchingSources(Pack pack) throws IOException {
        var source = sourceReader.read(pack.source());
        return bindings.stream().filter(candidate -> candidate.matches(source)).toList();
    }

    private Snapshot select(SourceBinding binding) {
        var descriptor = descriptors.descriptors().get(binding.adapter());
        if (descriptor == null) return Snapshot.unavailable(Reason.UNREGISTERED_ADAPTER);
        for (var required : descriptor.protocols()) {
            var version = protocols.get(required.id());
            if (version == null) return Snapshot.unavailable(Reason.MISSING_PROTOCOL);
            if (!required.acceptedVersions().contains(version)) return Snapshot.unavailable(Reason.UNSUPPORTED_PROTOCOL);
        }
        var operations = descriptor.capabilities().stream().map(capability -> capability.key())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Snapshot(Reason.AVAILABLE, descriptor, operations);
    }

    private Validated find(Object identity) {
        cache.removeIf(entry -> entry.identity.get() == null);
        return cache.stream().filter(entry -> entry.identity.get() == identity).findFirst().orElse(null);
    }
}
