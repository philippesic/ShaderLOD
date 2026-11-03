package com.pp.shaderlod.optimization.framecopy;

import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import com.pp.shaderlod.optimization.CompileOptionControl;
import com.pp.shaderlod.optimization.CompileOptionControl.NativeOption;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Setup-only G01 predicate. No source scans, GL queries, pack mutation or render-loop owner. */
public final class MakeUpDeadCopyAdmission {
    private MakeUpDeadCopyAdmission() {}
    /** Existing Mac route plus the exact experimentally qualified Asahi tuple.
     * New Linux GPUs/drivers retain native work until independently qualified. */
    public static boolean platformQualified(String os, String renderer, String driver) {
        if (os == null) return false;
        String name = os.toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("mac") || name.equals("darwin")) return true;
        return name.equals("linux") && "Apple M1 (G13G B1)".equals(renderer)
            && "4.6 (Core Profile) Mesa 25.1.0-asahi20250221".equals(driver);
    }
    /** Recorded labels only; callers must independently enforce the raw engine class pins. */
    public static String canonicalIrisMetadata(String actual) {
        return Set.of("1.8.12","1.8.12+mc1.21.1","1.8.12-snapshot+mc1.21.1-local").contains(actual)?"1.8.12":actual;
    }
    /** Existing independent graph contracts may ignore this newly declared key only while Off. */
    public static Map<String,String> withoutInactiveControl(Map<String,String> actual) {
        if(actual==null)return null;
        var copy=new TreeMap<>(actual);
        for(String key:Set.of(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY,CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH,CompileOptionControl.MAKEUP_AO_ATTENUATION)) {
            if(!copy.containsKey(key))continue;
            if(!"0".equals(copy.remove(key)))return null;
        }
        return Map.copyOf(copy);
    }
    /** Caller supplies current selected-source certification, never a cached family name alone.
     * exactApi must come from FrameCopyPins.exactApi() at the setup boundary. */
    public record Context(String minecraft, String loader, String iris, boolean exactApi,
                          boolean selectedGeneratedSource, String dimension, boolean dh, boolean voxy) {}
    private static final Set<String> MARKERS = Set.of(
        "SHADERLOD_REFINEMENT_ADAPTER_INCLUDED", "SHADERLOD_REFINEMENT_BUDGET_INCLUDED",
        "SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED", "SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED");
    private static final Set<String> NATIVE_GRAPH_CONTROLS = Set.of(
        CompileOptionControl.MAKEUP_AO_HALF, CompileOptionControl.MAKEUP_DEPTH,
        CompileOptionControl.MAKEUP_EARLY_ALPHA, CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH, CompileOptionControl.MAKEUP_AO_ATTENUATION, CompileOptionControl.MAKEUP_SPATIAL_REUSE,
        CompileOptionControl.MAKEUP_SPATIAL_RATE, CompileOptionControl.MAKEUP_WORLD_PIXEL);

    public static boolean eligible(Context context, Map<String, NativeOption> options) {
        return context != null && "1.21.1".equals(context.minecraft()) && "neoforge".equals(context.loader())
            && "1.8.12".equals(context.iris()) && context.exactApi() && context.selectedGeneratedSource()
            && "minecraft:overworld".equals(context.dimension()) && !context.dh() && !context.voxy()
            && options(options);
    }

    /** Compare all native100 values exactly. Strip only individually named, actually declared mod options.
     * Peer graph transforms must be off; the private proof does not establish their composition. */
    public static boolean options(Map<String, NativeOption> actual) {
        if (actual == null) return false;
        var nativeValues = new TreeMap<String, String>();
        for (var entry : actual.entrySet()) {
            String key = entry.getKey(); var option = entry.getValue();
            if (option == null || !option.allowed().contains(option.value())) return false;
            if (key.equals(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY)) {
                if (!option.binary()) return false;
            } else if (NATIVE_GRAPH_CONTROLS.contains(key)) {
                if (!option.binary() || (!"0".equals(option.value()) && !(Set.of(CompileOptionControl.MAKEUP_EARLY_ALPHA,CompileOptionControl.MAKEUP_AO_ATTENUATION,CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH).contains(key)
                        && com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.enabled()))) return false;
            } else if (key.equals("SHADERLOD_REFINEMENT_ADAPTER")) {
                if (!option.binary()) return false;
            } else if (MARKERS.contains(key)) {
                if (!"BOOLEAN".equals(option.type()) || !option.allowed().equals(Set.of("false", "true"))
                        || !"true".equals(option.defaultValue()) || !"true".equals(option.value())) return false;
            } else nativeValues.put(key, option.value());
        }
        return actual.containsKey(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY)
            && actual.containsKey("SHADERLOD_REFINEMENT_ADAPTER")
            && MakeUpQuadEligibility.optionsMatch(nativeValues);
    }
}
