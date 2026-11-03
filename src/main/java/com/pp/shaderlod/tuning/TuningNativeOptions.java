package com.pp.shaderlod.tuning;

import com.google.common.collect.ImmutableList;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.include.ShaderPackSourceNames;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;

/** Untimed native-option identity. Adapter declarations never become personal native settings. */
public final class TuningNativeOptions {
    private TuningNativeOptions() {}

    /** Complete original option values, preserving legal personal choices. Unknown inputs refuse. */
    public static Map<String,String> project(Path shaderpacks, String selected, String family,
                                             Map<String,String> actual) throws IOException {
        return project(shaderpacks, selected, family, actual, TuningNativeOptions::domains);
    }

    @FunctionalInterface interface Domains { Map<String,Set<String>> read(Path archive) throws IOException; }
    /** Same source/identity/projection path; tests supply domains from authentic parser fixtures. */
    static Map<String,String> project(Path shaderpacks, String selected, String family,
                                      Map<String,String> actual, Domains parser) throws IOException {
        if (actual == null || family == null) throw new IOException("Missing native option context");
        for (var entry : actual.entrySet())
            if (entry.getKey() == null || entry.getValue() == null) throw new IOException("Null selected shader option");
        var values = new TreeMap<>(actual);
        var directory = shaderpacks.toAbsolutePath().normalize();
        var identity = TuningSourceIdentity.capture(directory, selected);
        var original = directory.resolve(identity.originalFile());
        var source = PackSourceReader.read(original);
        var recognized = SupportedPackCatalog.recognize(source.memberSha256());
        if (recognized.isEmpty() || recognized.get().stage() != SupportedPackCatalog.Stage.RECOGNIZED_ORIGINAL
                || !recognized.get().pack().id().equals(family))
            throw new IOException("Native option family/source mismatch");
        var nativeDomains = parser.read(original);
        boolean generated = !selected.equals(identity.originalFile());
        Map<String,Set<String>> selectedDomains = nativeDomains;
        if (generated) {
            if (!family.equals(PackSelectionService.verifyGeneratedRendererGraph(directory.resolve(selected), values)))
                throw new IOException("Generated native option source/state mismatch");
            selectedDomains = parser.read(directory.resolve(selected));
            // A maintained adapter may add declarations, but may not erase a native setting.
            if (!selectedDomains.keySet().containsAll(nativeDomains.keySet()))
                throw new IOException("Generated graph lost native option declarations");
        }
        var result = new TreeMap<String,String>();
        for (var entry : values.entrySet()) {
            var allowed = selectedDomains.get(entry.getKey());
            if (allowed == null || !allowed.contains(entry.getValue()))
                throw new IOException("Unknown or invalid selected shader option: " + entry.getKey());
            var nativeAllowed = nativeDomains.get(entry.getKey());
            if (nativeAllowed != null) {
                if (!nativeAllowed.contains(entry.getValue()))
                    throw new IOException("Invalid original shader option: " + entry.getKey());
                result.put(entry.getKey(), entry.getValue());
            }
            // Only a declaration added by this exact verified generated source is omitted.
        }
        if (!result.keySet().equals(nativeDomains.keySet()))
            throw new IOException("Incomplete native shader option map");
        identity.requireCurrent(directory, selected);
        return Map.copyOf(result);
    }

    private static Map<String,Set<String>> domains(Path archive) throws IOException {
        try (var zip = FileSystems.newFileSystem(archive)) {
            var root = zip.getPath("/shaders");
            var starts = ImmutableList.<AbsolutePackPath>builder();
            for (String dimension : new String[]{"/", "/world0/", "/world-1/", "/world1/"})
                ShaderPackSourceNames.findPresentSources(starts, root,
                    AbsolutePackPath.fromAbsolutePath(dimension), ShaderPackSourceNames.POTENTIAL_STARTS);
            var entries = starts.build();
            if (entries.isEmpty()) throw new IOException("No original shader program roots");
            var graph = new IncludeGraph(root, entries, false);
            if (!graph.getFailures().isEmpty()) throw new IOException("Shader option include graph is incomplete");
            var options = new ShaderPackOptions(graph, Map.of()).getOptionSet();
            var result = new TreeMap<String,Set<String>>();
            options.getBooleanOptions().forEach((key, value) -> result.put(key, Set.of("false", "true")));
            options.getStringOptions().forEach((key, value) ->
                result.put(key, Set.copyOf(value.getOption().getAllowedValues())));
            if (result.isEmpty()) throw new IOException("No native shader option declarations");
            return Map.copyOf(result);
        } catch (RuntimeException | LinkageError failure) {
            throw new IOException("Cannot establish original shader option declarations", failure);
        }
    }
}
