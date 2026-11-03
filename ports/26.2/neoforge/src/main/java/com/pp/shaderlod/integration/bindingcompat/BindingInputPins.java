package com.pp.shaderlod.integration.bindingcompat;
import java.util.*;
/** Exact classloader-scoped foreign resource pins; module-local Class lookup cannot establish these. */
public final class BindingInputPins {
    private static final Map<String,String> PINS=Map.ofEntries(
            Map.entry("com/mojang/blaze3d/pipeline/RenderPipeline.class","caff787429aef432ee86dda471cb4439474246a236a0c22df7dd7e22cf5af9eb"),
            Map.entry("com/mojang/blaze3d/opengl/GlRenderPass.class","060a403ba75f84b6e6aed61f7d72f7075308cff8b049df2d42b52b725f19cdc9"),
            Map.entry("com/mojang/blaze3d/opengl/GlCommandEncoder.class","59803aaf5821ac664d5d5cc254e341a2d45f770d080771d197372556baaf79e3"),
            Map.entry("com/mojang/blaze3d/opengl/VertexArrayCache$Emulated.class","b10cf09183b0b8f131a37443bffcdff518aa472c061b8e64cc8094b930f27b7e"),
            Map.entry("com/mojang/blaze3d/opengl/VertexArrayCache$Separate.class","5dc71c9e9e472b9c4c70116140993f25d581fcf22cbe727b766f6d9e942e1b9b"),
            Map.entry("net/irisshaders/iris/mixin/MixinRenderPipeline.class","b64627a9d80d21cf95a7ca4c6f5355137c8671412365812e89526ac07d50a89c") );
    private static final List<Map<String,Object>> OBSERVATIONS=inspectResources(BindingInputPins.class.getClassLoader(),PINS);
    private static final boolean MATCH=OBSERVATIONS.stream().allMatch(pin->Boolean.TRUE.equals(pin.get("matches")));
    private BindingInputPins() {}
    public static boolean match() { return MATCH; }
    public static List<Map<String,Object>> observations() { return OBSERVATIONS; }
    public static boolean resourcesMatch(ClassLoader loader) {
        return inspectResources(loader,PINS).stream().allMatch(pin->Boolean.TRUE.equals(pin.get("matches")));
    }
    /** Authentic loader checks and wrong/missing-resource negatives; never substitutes a dependency hash. */
    public static List<Map<String,Object>> inspectResources(ClassLoader loader,Map<String,String> expectedPins) {
        var result=new ArrayList<Map<String,Object>>();
        for(var pin:new TreeMap<>(expectedPins).entrySet()) {
            var row=new LinkedHashMap<String,Object>();row.put("resource",pin.getKey());row.put("expectedSha256",pin.getValue());
            row.put("found",false);row.put("matches",false);
            try {
                if(loader==null)throw new IllegalStateException("Exact helper classloader unavailable");
                if(pin.getKey().startsWith("/"))throw new IllegalArgumentException("Classloader resource name must be relative");
                var url=loader.getResource(pin.getKey());row.put("url",String.valueOf(url));
                try(var in=loader.getResourceAsStream(pin.getKey())) {
                    if(in!=null) {
                        row.put("found",true);String actual=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
                        row.put("actualSha256",actual);row.put("matches",pin.getValue().equals(actual));
                    }
                }
            }catch(Exception|LinkageError failure){row.put("unavailable",failure.toString());}
            result.add(Collections.unmodifiableMap(row));
        }
        return List.copyOf(result);
    }
    /** Parent-run real produced-class checks, no client or substitute resource content. */
    public static void main(String[] args) {
        var loader=BindingInputPins.class.getClassLoader();
        if(!resourcesMatch(loader))throw new AssertionError("Authentic binding classloader pins unavailable/mismatched: "+OBSERVATIONS);
        String known="com/mojang/blaze3d/pipeline/RenderPipeline.class";
        var wrong=inspectResources(loader,Map.of(known,"wrong-sha256"));
        if(!Boolean.TRUE.equals(wrong.getFirst().get("found")) || Boolean.TRUE.equals(wrong.getFirst().get("matches")))
            throw new AssertionError("Wrong authentic resource hash must refuse");
        var missing=inspectResources(loader,Map.of("shaderlod/definitely-missing-binding-resource.class",PINS.get(known)));
        if(Boolean.TRUE.equals(missing.getFirst().get("found")) || Boolean.TRUE.equals(missing.getFirst().get("matches")))
            throw new AssertionError("Missing resource must refuse");
        if(resourcesMatch(null))throw new AssertionError("Missing exact classloader must refuse");
        System.out.println(new com.google.gson.Gson().toJson(Map.of("pins",OBSERVATIONS,"wrongHash",wrong,"missingResource",missing,
            "scope","Actual produced classloader resource checks only; named FML runtime gate/getter/driver unverified")));
    }
}
