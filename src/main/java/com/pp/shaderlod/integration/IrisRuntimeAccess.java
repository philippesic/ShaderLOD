package com.pp.shaderlod.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;

/** Pinned Iris 1.8.12 APIs; only loaded OptionSet keys count as effective options. */
public final class IrisRuntimeAccess implements AdapterRuntimeBinding.RuntimeAccess {
    private final Set<String> requiredOptionKeys;

    public IrisRuntimeAccess(Set<String> requiredOptionKeys) { this.requiredOptionKeys = Set.copyOf(requiredOptionKeys); }

    @Override public Optional<AdapterRuntimeBinding.Pack> currentPack() {
        return Iris.getCurrentPack().map(pack -> {
            var directory = Iris.getShaderpacksDirectory().toAbsolutePath().normalize();
            var source = directory.resolve(Iris.getCurrentPackName()).normalize();
            if (source.equals(directory) || !source.startsWith(directory)) {
                throw new IllegalArgumentException("Pack source outside shaderpacks directory");
            }
            return new AdapterRuntimeBinding.Pack(pack, source,
                    effectiveOptions(pack.getShaderPackOptions(), requiredOptionKeys));
        });
    }

    public static Map<String, String> effectiveOptions(ShaderPackOptions options, Set<String> requiredOptionKeys) {
        var declared = options.getOptionSet();
        var values = options.getOptionValues();
        var effective = new LinkedHashMap<String, String>();
        for (String key : requiredOptionKeys) {
            if (declared.getBooleanOptions().containsKey(key)) {
                effective.put(key, Boolean.toString(values.getBooleanValueOrDefault(key)));
            } else if (declared.getStringOptions().containsKey(key)) {
                effective.put(key, values.getStringValueOrDefault(key));
            }
        }
        return Map.copyOf(effective);
    }

    @Override public boolean fallback() { return Iris.isFallback(); }

    /** Called at frame snapshot, never while constructing or validating a pipeline. */
    @Override public boolean pipelineAvailable() { return IrisApi.getInstance().isShaderPackInUse(); }
}
