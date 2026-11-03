import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.google.gson.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;

/** Offline broad declaration delta. Deliberately does not impersonate a loaded ShaderPack. */
class IrisOptionInventory {
    record Inventory(OptionSet options, Map<String, String> defaults, int sourceCount) {}

    static Inventory inventory(String filename) throws Exception {
        var sources = new TreeMap<String, OptionAnnotatedSource>();
        var references = new HashSet<String>();
        try (var zip = new ZipFile(filename)) {
            var seen = new HashSet<String>();
            for (var entry : zip.stream().toList()) {
                String name = entry.getName();
                if (!seen.add(name)) throw new IllegalArgumentException("Duplicate archive member: " + name);
                if (!name.startsWith("shaders/") || entry.isDirectory() ||
                        !name.matches(".*\\.(glsl|vsh|fsh|csh|gsh|tcs|tes|settings)$")) continue;
                if (name.contains("\\") || Arrays.asList(name.split("/")).contains(".."))
                    throw new IllegalArgumentException("Unsafe shader member: " + name);
                try (var input = zip.getInputStream(entry)) {
                    var source = new OptionAnnotatedSource(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    sources.put(name, source);
                    references.addAll(source.getBooleanDefineReferences().keySet());
                }
            }
        }
        if (sources.isEmpty()) throw new IllegalArgumentException("No shader sources: " + filename);
        var builder = OptionSet.builder();
        sources.forEach((name, source) -> builder.addAll(source.getOptionSet(
                AbsolutePackPath.fromAbsolutePath("/" + name.substring(8)), references)));
        var options = builder.build();
        var defaults = new TreeMap<String, String>();
        options.getBooleanOptions().forEach((key, option) -> defaults.put(key,
                Boolean.toString(option.getOption().getDefaultValue())));
        options.getStringOptions().forEach((key, option) -> {
            if (defaults.put(key, option.getOption().getDefaultValue()) != null)
                throw new IllegalArgumentException("Boolean/string collision: " + key);
        });
        return new Inventory(options, defaults, sources.size());
    }

    static Map<String, String> readMap(String filename) throws Exception {
        var object = JsonParser.parseString(Files.readString(Path.of(filename))).getAsJsonObject();
        var map = new TreeMap<String, String>();
        object.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Option value must be a string: " + entry.getKey());
            map.put(entry.getKey(), entry.getValue().getAsString());
        });
        return map;
    }

    static void validate(Inventory inventory, Map<String, String> requested) {
        var values = new MutableOptionValues(inventory.options(), requested);
        requested.forEach((key, requestedValue) -> {
            String effective;
            if (inventory.options().getBooleanOptions().containsKey(key))
                effective = Boolean.toString(values.getBooleanValueOrDefault(key));
            else if (inventory.options().getStringOptions().containsKey(key)) {
                var declaration = inventory.options().getStringOptions().get(key).getOption();
                if (!declaration.getAllowedValues().contains(requestedValue))
                    throw new IllegalArgumentException("Iris declared range rejects supplied value: " + key + "=" + requestedValue);
                effective = values.getStringValueOrDefault(key);
            }
            else throw new IllegalArgumentException("Undeclared supplied option: " + key);
            if (!requestedValue.equals(effective)) throw new IllegalArgumentException(
                    "Iris rejected supplied value: " + key + "=" + requestedValue + " (effective " + effective + ")");
        });
    }

    static Map<String, Object> broad(Inventory inventory) {
        return Map.of("declarationCount", inventory.defaults().size(), "sourceCount", inventory.sourceCount(),
                "defaults", inventory.defaults());
    }

    public static void main(String[] args) throws Exception {
        var original = inventory(args[0]);
        var generated = inventory(args[1]);
        var nativeMap = readMap(args[2]);
        var overrides = readMap(args[3]);
        if (nativeMap.isEmpty()) throw new IllegalArgumentException("Native observation must not be empty");
        validate(original, nativeMap);
        var added = new TreeSet<>(generated.defaults().keySet());
        added.removeAll(original.defaults().keySet());
        var removed = new TreeSet<>(original.defaults().keySet());
        removed.removeAll(generated.defaults().keySet());
        if (!removed.isEmpty()) throw new IllegalArgumentException("Removed original declarations require review: " + removed);
        if (!added.containsAll(overrides.keySet())) throw new IllegalArgumentException(
                "Overrides must name newly added declarations only: " + overrides.keySet());
        var candidate = new TreeMap<>(nativeMap);
        for (String key : added) candidate.put(key, generated.defaults().get(key));
        candidate.putAll(overrides);
        validate(generated, candidate);
        var changed = new TreeMap<String, Object>();
        original.defaults().forEach((key, value) -> {
            if (!value.equals(generated.defaults().get(key)))
                changed.put(key, Map.of("original", value, "generated", generated.defaults().get(key)));
        });
        var excluded = new TreeSet<>(original.defaults().keySet());
        excluded.removeAll(nativeMap.keySet());
        var report = new TreeMap<String, Object>();
        report.put("schema", 1);
        report.put("scope", "Broad Iris declaration delta anchored to supplied native observation; actual loaded equality remains required");
        report.put("loadedMapVerified", false);
        report.put("nativeMapPreserved", true);
        report.put("nativeOptionCount", nativeMap.size());
        report.put("candidateExpectedOptions", candidate);
        report.put("candidateOptionCount", candidate.size());
        report.put("addedDeclarations", added);
        report.put("removedDeclarations", removed);
        report.put("changedDefaults", changed);
        report.put("excludedOriginalDeclarations", excluded);
        report.put("originalBroadInventory", broad(original));
        report.put("generatedBroadInventory", broad(generated));
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
