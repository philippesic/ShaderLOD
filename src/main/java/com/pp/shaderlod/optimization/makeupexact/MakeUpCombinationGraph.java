package com.pp.shaderlod.optimization.makeupexact;

import com.pp.shaderlod.optimization.*;
import java.util.*;

/** Explicit experiment only: G01 property copies, G03 post-sample alpha, G04 translucent and G06 deferred arithmetic.
 * Original source/API/platform/native-quality contracts remain independent.
 * No other peer graph composition or ordinary client is admitted here. */
public final class MakeUpCombinationGraph {
    private MakeUpCombinationGraph() {}
    public static boolean permittedHost(String os, String arch) {
        return "Linux".equals(os) && "aarch64".equals(arch);
    }
    public static boolean enabled() {
        return permittedHost(System.getProperty("os.name"), System.getProperty("os.arch"))
            && Boolean.getBoolean("shaderlod.dev.shortlistCombinationRuntime")
            && !System.getProperty("shaderlod.dev.benchmarkControlProbe", "").isBlank();
    }
    public static boolean pair(OptimizationSnapshot flags) {
        if (!enabled() || flags == null) return false;
        int count = 0;
        for (var id : OptimizationId.values()) {
            if (!flags.enabled(id)) continue;
            if (!Set.of(OptimizationId.DEAD_FRAME_COPY, OptimizationId.EARLY_ALPHA, OptimizationId.TRANSLUCENT_FASTPATH, OptimizationId.AO_ATTENUATION).contains(id)) return false;
            count++;
        }
        return count >= 2;
    }
    public static boolean selected(OptimizationSnapshot flags, OptimizationId id) {
        return pair(flags) && flags.enabled(id);
    }
    /** Normalize only the independently guarded G03/G04/G06 binary controls for the
     * original G01 profile comparison. Unknown options/native edits still fail. */
    public static Map<String,String> copyProfile(Map<String,String> actual) {
        if (!enabled() || actual == null) return actual;
        var copy = new TreeMap<>(actual);
        for (String key : List.of(CompileOptionControl.MAKEUP_EARLY_ALPHA, CompileOptionControl.MAKEUP_AO_ATTENUATION, CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH)) {
            if (!actual.containsKey(key)) continue;
            if (!Set.of("0", "1").contains(actual.get(key))) return null;
            copy.put(key, "0");
        }
        return copy;
    }
}
