package com.pp.shaderlod.optimization.reimaginedearlyalpha;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;

/**
 * Observations captured when Iris finishes constructing its options and rewriting its graph.
 * This is input evidence, not a source certificate or permission to optimize a draw.
 *
 * <p>The graph is the actual immutable, pre-option-rewrite constructor input. Its lines have
 * already undergone Iris's line splitting; they are not a byte-exact archive snapshot. The
 * typed maps contain every declaration's effective value, including unchanged defaults, and
 * are independent of later changes to Iris's mutable option values.
 */
public record ReimaginedCompilationInputs(
        IncludeGraph rawGraph,
        Map<String, Boolean> booleanOptions,
        Map<String, String> stringOptions) {
    public ReimaginedCompilationInputs {
        Objects.requireNonNull(rawGraph, "rawGraph");
        booleanOptions = Map.copyOf(booleanOptions);
        stringOptions = Map.copyOf(stringOptions);
    }

    /** Called synchronously at the pinned ShaderPackOptions constructor's return. No I/O. */
    public static ReimaginedCompilationInputs capture(
            IncludeGraph rawGraph, OptionSet declarations, OptionValues values) {
        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(values, "values");
        if (values.getOptionSet() != declarations) {
            throw new IllegalArgumentException("Option values do not belong to the declarations");
        }
        var booleans = new LinkedHashMap<String, Boolean>();
        for (String key : declarations.getBooleanOptions().keySet()) {
            booleans.put(key, values.getBooleanValueOrDefault(key));
        }
        var strings = new LinkedHashMap<String, String>();
        for (String key : declarations.getStringOptions().keySet()) {
            strings.put(key, values.getStringValueOrDefault(key));
        }
        return new ReimaginedCompilationInputs(rawGraph, booleans, strings);
    }
}
