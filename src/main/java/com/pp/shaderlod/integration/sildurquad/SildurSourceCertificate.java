package com.pp.shaderlod.integration.sildurquad;

import com.pp.shaderlod.packs.SupportedPackCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/** Pure exact union source join. It grants no loader/API, driver, DH/PBR or speed acceptance. */
public final class SildurSourceCertificate {
    public static final String UNION_SOURCE_SHA = "1f2bf94bb4089d0f8608dc3039ff2d641e9bd85832fe34cd44a04d290947970a";
    private static final String SETTINGS_SHA = "0b01a467a751b46be90717ed8299e19dbb9c3e7a199b7ac02509f0fde8dc534c";
    private static final Map<String,String> PRESERVED_VERTICES = Map.of(
        "shaders/gbuffers_textured.vsh", "3d0bae0cf674899918476ea70fc9feb9a65656f290c778dbb8fb1570cf15a084");
    public record Result(boolean eligible, String reason) {}
    private SildurSourceCertificate() {}
    private static final Map<String,String> ADDED_DECLARATIONS = Map.ofEntries(
        Map.entry("SHADERLOD_SILDUR_PIXEL_GRAPH","0"),
        Map.entry("SHADERLOD_SILDUR_AO_HALF_RES","0"),
        Map.entry("SHADERLOD_SILDUR_SPATIAL_REUSE","0"),
        Map.entry("SHADERLOD_SILDUR_LIGHTING_RATE","0"),
        Map.entry("SHADERLOD_SILDUR_DEPTH_PRIMING","0"),
        Map.entry("SHADERLOD_EFFECT_ADAPTER", "1"),
        Map.entry("SHADERLOD_EFFECT_ADAPTER_INCLUDED", "true"),
        Map.entry("SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED", "true"),
        Map.entry("SHADERLOD_PROJECTED_MATERIAL_BUDGET_INCLUDED", "true"),
        Map.entry("SHADERLOD_RECEIVER_FOOTPRINT_BUDGET_R1_INCLUDED", "true"),
        Map.entry("SHADERLOD_REFINEMENT_BUDGET_INCLUDED", "true"),
        Map.entry("SHADERLOD_SILDUR_PROJECTED_POM_INCLUDED", "true"),
        Map.entry("SHADERLOD_SILDUR_RECEIVER_ACTIVE", "true"),
        Map.entry("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH", "0"),
        Map.entry("SHADERLOD_SILDUR_SHADOW_QUERY_INCLUDED", "true"),
        Map.entry("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED", "true"));
    /** Exact pinned Iris declaration delta (native115 to130), not a loaded-option observation. */
    public static Map<String,String> generatedOptions() {
        var expected = new HashMap<>(SildurQuadEligibility.nativeOptions());
        if (expected.size()!=115) return Map.of();
        expected.putAll(ADDED_DECLARATIONS);
        return Map.copyOf(expected);
    }
    public static boolean generatedOptionsMatch(Map<String,String> actual) {
        if (actual==null || !("0".equals(actual.get("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH")) ||
                            "1".equals(actual.get("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH")))) return false;
        if(!java.util.Set.of("0","1").contains(actual.get("SHADERLOD_SILDUR_AO_HALF_RES")))return false;
        if(!java.util.Set.of("0","1").contains(actual.get("SHADERLOD_SILDUR_DEPTH_PRIMING")))return false;
        for(String k:java.util.List.of("SHADERLOD_SILDUR_SPATIAL_REUSE","SHADERLOD_SILDUR_LIGHTING_RATE"))if(!java.util.Set.of("0","1").contains(actual.get(k)))return false;
        if(!java.util.Set.of("0","1").contains(actual.get("SHADERLOD_SILDUR_PIXEL_GRAPH")))return false;
        var normalized = new HashMap<>(actual);normalized.put("SHADERLOD_SILDUR_PIXEL_GRAPH","0");normalized.put("SHADERLOD_SILDUR_SPATIAL_REUSE","0");normalized.put("SHADERLOD_SILDUR_LIGHTING_RATE","0");normalized.put("SHADERLOD_SILDUR_DEPTH_PRIMING","0");
        normalized.put("SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH","0");normalized.put("SHADERLOD_SILDUR_AO_HALF_RES","0");
        var expected = generatedOptions();
        return expected.size()==131 && expected.equals(normalized);
    }

    /** Source/POM profile only. Does not widen original or generated CPU eligibility. */
    public static boolean nativeSourceProfileOptionsMatch(Map<String,String> actual) {
        if (actual==null || !("0".equals(actual.get("nMap")) || "2".equals(actual.get("nMap")))) return false;
        var expected=new HashMap<>(SildurQuadEligibility.nativeOptions());
        if(expected.size()!=115) return false;
        expected.put("nMap",actual.get("nMap"));
        return expected.equals(actual);
    }
    public static boolean generatedSourceProfileOptionsMatch(Map<String,String> actual) {
        if(actual==null || !("0".equals(actual.get("nMap")) || "2".equals(actual.get("nMap")))) return false;
        var sourceOnly=new HashMap<>(actual);sourceOnly.put("nMap","0");
        return generatedOptionsMatch(sourceOnly);
    }
    public static Result validateNativeSourceProfile(Path archive,Map<String,String> actual) {
        if(!nativeSourceProfileOptionsMatch(actual)) return new Result(false,"Exact native115 POM source profile refused");
        var nativeProof=SildurQuadEligibility.validate(archive,SildurQuadEligibility.nativeOptions());
        return new Result(nativeProof.eligible(),nativeProof.reason()+"; source-only nMap="+actual.get("nMap"));
    }
    public static Result validateSourceProfile(Path archive,Map<String,String> actual) {
        if(!generatedSourceProfileOptionsMatch(actual)) return new Result(false,"Exact generated126 POM source profile refused");
        var sourceOnly=new HashMap<>(actual);sourceOnly.put("nMap","0");
        var proof=validate(archive,sourceOnly);
        return new Result(proof.eligible(),proof.reason()+"; source-only nMap="+actual.get("nMap"));
    }

    public static Result validate(Path archive, Map<String,String> options) {
        try {
            if (!generatedOptionsMatch(options))
                return new Result(false,"Actual Iris union126 declarations/native115 options refused");
            if (archive == null || !Files.isRegularFile(archive)) return new Result(false,"Missing selected union archive");
            var hashes = new HashMap<String,String>();
            try (var zip = new ZipFile(archive.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String n = entry.getName();
                    if (n.startsWith("/") || n.contains("\\") || java.util.Arrays.asList(n.split("/")).contains(".."))
                        return new Result(false,"Unsafe archive member");
                    String h = SildurQuadEligibility.sha(zip.getInputStream(entry).readAllBytes());
                    if (hashes.put(n,h) != null) return new Result(false,"Duplicate archive member");
                }
            }
            if (!UNION_SOURCE_SHA.equals(SupportedPackCatalog.shaderSourceDigest(hashes)) ||
                !SETTINGS_SHA.equals(hashes.get("shaders/shaders.settings")))
                return new Result(false,"Union source/settings identity changed");
            for (var e:PRESERVED_VERTICES.entrySet()) if (!e.getValue().equals(hashes.get(e.getKey())))
                return new Result(false,"Native terrain vertex/encoded-index source changed");
            return new Result(true,"Exact Sildur union source/actual126 declarations/native115/unchanged terrain vertex; common API/config/lifecycle gates and runtime evidence remain required");
        } catch (Exception | LinkageError unavailable) {
            return new Result(false,"Sildur union source certificate unavailable");
        }
    }
}
