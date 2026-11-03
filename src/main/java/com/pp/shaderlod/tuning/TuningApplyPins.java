package com.pp.shaderlod.tuning;

import java.util.*;

/** Pin each verified graph exactly, and retain native settings/device identity across graph changes. */
final class TuningApplyPins {
    enum Graph { ORIGINAL, GENERATED }
    private record Key(boolean enabled,Graph graph) {}
    private final Map<Key,Map<String,String>> graphs=new HashMap<>();
    private Map<String,String> common;
    private Set<String> nativeKeys;

    /** Validate positive evidence before comparing identities; equal unknowns are not pins.
     * complete_physical_inputs=false is legitimate for NeoForge's complete effective
     * SecureJar view; this does not claim hidden secondary physical archives were read. */
    static void requireValid(Map<String,String> pins) {
        Objects.requireNonNull(pins,"pins");
        if(!"true".equals(pins.get("mod_artifact_valid")))throw new IllegalStateException("Valid loaded mod artifact identity required");
        for(String key:List.of("mod_artifact_sha256","mod_artifact_manifest_sha256","source_sha256")) {
            String value=pins.get(key);
            if(value==null||!value.matches("[a-f0-9]{64}")||value.equals("0".repeat(64)))throw new IllegalStateException("Missing/invalid identity digest: "+key);
        }
        if(!Set.of("true","false").contains(Objects.toString(pins.get("mod_artifact_complete_physical_inputs"),"")))
            throw new IllegalStateException("Artifact physical-input scope must be explicit");
        for(String key:List.of("mod_artifact_loader","mod_artifact_scope","mod_artifact_stability","mod_artifact","mod_artifact_roots",
                "source_path","source_family","gpu_vendor","gpu_renderer","gl_version")) {
            String value=pins.get(key);String normalized=value==null?"":value.trim().toLowerCase(Locale.ROOT);
            if(normalized.isBlank()||Set.of("null","unknown","unavailable","[]","{}").contains(normalized)||normalized.startsWith("unavailable")||normalized.startsWith("unknown")||normalized.startsWith("unsupported"))
                throw new IllegalStateException("Missing/unknown measurement identity: "+key);
        }
        if(!Set.of("loaded archive","exploded loader module manifest","loader module view manifest").contains(pins.get("mod_artifact_kind")))
            throw new IllegalStateException("Unknown loaded artifact kind");
        for(String key:List.of("mod_artifact_files","mod_artifact_bytes"))try {
            if(Long.parseLong(pins.getOrDefault(key,"0"))<=0)throw new NumberFormatException();
        }catch(NumberFormatException bad){throw new IllegalStateException("Missing/invalid bounded artifact count: "+key,bad);}
        String anchors=pins.getOrDefault("mod_artifact_backend_anchors","");
        for(String anchor:List.of("MeasurementPins","RuntimeMeasurement","OpenGlElapsedBackend"))
            if(!java.util.regex.Pattern.compile("com/pp/shaderlod/measurement/"+anchor+"\\.class=[a-f0-9]{64} @ ").matcher(anchors).find())
                throw new IllegalStateException("Loaded measurement anchor identity missing: "+anchor);
        var size=java.util.regex.Pattern.compile("Dimensions\\[width=([1-9][0-9]*), height=([1-9][0-9]*)\\]").matcher(pins.getOrDefault("resolution",""));
        if(!size.matches())throw new IllegalStateException("Actual positive rendered resolution required");
        try {Integer.parseInt(size.group(1));Integer.parseInt(size.group(2));}
        catch(NumberFormatException bad){throw new IllegalStateException("Rendered resolution exceeds integer dimensions",bad);}
        try {if(!java.nio.file.Path.of(pins.get("source_path")).isAbsolute())throw new IllegalStateException("Actual absolute selected source path required");}
        catch(java.nio.file.InvalidPathException bad){throw new IllegalStateException("Invalid selected source path",bad);}
    }

    // Caller must first positively verify TuningSourceIdentity.requireCurrent for this Apply.
    void verify(boolean enabled,Graph graph,Map<String,String> pins,Map<String,String> nativeOptions) {
        requireValid(pins);
        if(nativeOptions==null||nativeOptions.isEmpty())throw new IllegalStateException("Complete native option identity is required");
        if(nativeKeys==null){
            if(graph!=Graph.ORIGINAL)throw new IllegalStateException("Initial tuning source must be the verified native original");
            nativeKeys=Set.copyOf(nativeOptions.keySet());
        }
        var signature=new TreeMap<>(pins);
        for(var key:List.of("scene","simulation","effective_options","settings"))signature.remove(key);
        signature.put("native_option_values",new TreeMap<>(nativeOptions).toString());
        var prior=graphs.get(new Key(enabled,graph));
        if(prior!=null&&!prior.equals(signature))throw new IllegalStateException("Native options/source/device/settings changed within the verified graph");
        var shared=new TreeMap<>(signature);
        // These three fields differ only between the strictly verified original/generated pair.
        for(var key:List.of("source_path","source_sha256","source_family"))shared.remove(key);
        var originalValues=new TreeMap<String,String>();
        for(var key:nativeKeys){
            var value=nativeOptions.get(key);
            if(value==null)throw new IllegalStateException("Native option missing after graph transition: "+key);
            originalValues.put(key,value);
        }
        shared.put("native_option_values",originalValues.toString());
        if(common!=null&&!common.equals(shared))throw new IllegalStateException("Native options/device/settings changed across the verified source pair");
        graphs.putIfAbsent(new Key(enabled,graph),Map.copyOf(signature));
        if(common==null)common=Map.copyOf(shared);
    }
}
