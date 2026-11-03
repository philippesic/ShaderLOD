package com.pp.shaderlod.integration.bindingcompat;
import java.util.*;
/** Exact classloader-scoped foreign resource pins; module-local Class lookup cannot establish these. */
public final class BindingInputPins {
    private static final Map<String,String> PINS=Map.ofEntries(
            Map.entry("com/mojang/blaze3d/pipeline/RenderPipeline.class","c4c736de5c524d3ca1cbe2a6c103279319c21ff5cf11bdc4f66bd5b7f0e86ac5"),
            Map.entry("com/mojang/blaze3d/opengl/GlRenderPass.class","860b03f274462526634a2021c9b521cb17fe5991e9388b7b3d94753d05d1d6c2"),
            Map.entry("com/mojang/blaze3d/opengl/GlCommandEncoder.class","16359d35b89008f6a90691c44e2fce4c1c01390d107a2dd42c9852594b9d0054"),
            Map.entry("com/mojang/blaze3d/opengl/VertexArrayCache$Emulated.class","31f30b0a4a585893f10d51f4672b7deebf31e9f9416ddfb5035c2f094f53c926"),
            Map.entry("com/mojang/blaze3d/opengl/VertexArrayCache$Separate.class","f72765e1014caf8626c5c1a403770ac52d20b580e17f833f3aab882ac4f31878"),
            Map.entry("net/irisshaders/iris/mixin/MixinRenderPipeline.class","b64627a9d80d21cf95a7ca4c6f5355137c8671412365812e89526ac07d50a89c") );
    private static final Map<String,String> RELEASE_PINS=Map.ofEntries(
        Map.entry("com/mojang/blaze3d/pipeline/RenderPipeline.class","876e36debeb0f9e63015d73e79d84d3f1226feefd1035e1cd206a34e36716b45"),
        Map.entry("com/mojang/blaze3d/opengl/GlRenderPass.class","7ad9eed06cc24d3a5c8db45a57d9bb6691cf354b07774036316046cb3acc10fa"),
        Map.entry("com/mojang/blaze3d/opengl/GlCommandEncoder.class","817def104e86530ea2ec7eb6d23eaf4ade18e8ffbb6f5a551db47cecceb890ed"),
        Map.entry("com/mojang/blaze3d/opengl/VertexArrayCache$Emulated.class","7eb1be22f3e9c014e2fac867d36d3e5c06ca0c9cdc08c72c03f1388947eac1e3"),
        Map.entry("com/mojang/blaze3d/opengl/VertexArrayCache$Separate.class","19f6b097b457563d7c837d0bc2178b2961beaa37aa6dc2cac16952749e690904"),
        Map.entry("net/irisshaders/iris/mixin/MixinRenderPipeline.class","b64627a9d80d21cf95a7ca4c6f5355137c8671412365812e89526ac07d50a89c") );
    private static Map<String,String> selectedPins(){var loader=BindingInputPins.class.getClassLoader();return inspectResources(loader,PINS).stream().allMatch(pin->Boolean.TRUE.equals(pin.get("matches")))?PINS:RELEASE_PINS;}
    private static final List<Map<String,Object>> OBSERVATIONS=inspectResources(BindingInputPins.class.getClassLoader(),selectedPins());
    private static final boolean MATCH=OBSERVATIONS.stream().allMatch(pin->Boolean.TRUE.equals(pin.get("matches")));
    private BindingInputPins() {}
    public static boolean match() { return MATCH; }
    public static List<Map<String,Object>> observations() { return OBSERVATIONS; }
    public static boolean resourcesMatch(ClassLoader loader) {
        return inspectResources(loader,PINS).stream().allMatch(pin->Boolean.TRUE.equals(pin.get("matches")))||inspectResources(loader,RELEASE_PINS).stream().allMatch(pin->Boolean.TRUE.equals(pin.get("matches")));
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
