package com.pp.shaderlod.optimization.makeuppresence;

import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import java.util.List;
import java.util.regex.Pattern;

/** Only the three mod-owned shadow queries in authenticated MakeUp 9.5g terrain. */
public final class MakeUpTerrainPresence {
    private MakeUpTerrainPresence() {}
    public static final List<OptimizationId> CONTROLS = List.of(OptimizationId.EARLY_ALPHA,
        OptimizationId.SHADOW_SAMPLE_BUDGET, OptimizationId.SHADOW_RECEIVER_FOOTPRINT,
        OptimizationId.TEMPORAL_SHADOW_FILTER);

    /** Conservative request union, independent of transient draw permission and core Off/Quality. */
    public static int mask(OptimizationSnapshot requests) {
        int mask = 0;
        for (int i = 0; i < CONTROLS.size(); i++) if (requests.enabled(CONTROLS.get(i))) mask |= 1 << i;
        return mask;
    }

    public static boolean changesPresence(OptimizationSnapshot previous, OptimizationSnapshot next) {
        return mask(previous) != mask(next);
    }

    /** Caller supplies the raw receipt first. Enabled masks and the existing alpha path stay intact. */
    public static String specialize(String fragment, int mask, String compiledAlpha) {
        if (fragment == null || mask != 0 || !"0".equals(compiledAlpha)) return null;
        String result = fragment;
        for (var id : CONTROLS.subList(1, CONTROLS.size())) {
            var declaration = uniform(id).matcher(result);
            if (!declaration.find()) return null;
            int start = declaration.start(), end = declaration.end();
            if (declaration.find()) return null;
            result = result.substring(0, start) + "const int " + id.uniform() + " = 0;" + result.substring(end);
            var query = Pattern.compile("\\bshaderlod_optimization_enabled\\s*\\(\\s*"
                + id.uniform() + "\\s*\\)").matcher(result);
            // Native colored shadows preprocess the receiver/temporal consumers away.
            // Their declarations still exist; zero query occurrences is a valid native branch.
            result = query.replaceAll("false");
        }
        return result;
    }

    public static boolean transformed(String fragment) {
        if (fragment == null) return false;
        for (var id : CONTROLS.subList(1, CONTROLS.size())) {
            if (uniform(id).matcher(fragment).find()) return false;
            var constant = Pattern.compile("\\bconst\\s+int\\s+" + id.uniform() + "\\s*=\\s*0\\s*;").matcher(fragment);
            if (!constant.find() || constant.find()) return false;
            if (Pattern.compile("\\bshaderlod_optimization_enabled\\s*\\(\\s*" + id.uniform() + "\\s*\\)")
                    .matcher(fragment).find()) return false;
        }
        return true;
    }

    private static Pattern uniform(OptimizationId id) {
        return Pattern.compile("\\buniform\\s+int\\s+" + id.uniform() + "\\s*;");
    }
}
