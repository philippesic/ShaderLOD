package com.pp.shaderlod.optimization.bslprefix;
import com.google.gson.*;import java.util.*;import java.security.MessageDigest;
/** Exact source-only PBR profiles. Does not broaden BSL CPU/receiver eligibility. */
public final class BslPrefixProfile {
 private BslPrefixProfile(){} private static Map<String,String> load(String path,String sha){try(var in=BslPrefixProfile.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/"+path)){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)).equals(sha))return Map.of();var m=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);}catch(Exception unavailable){return Map.of();}}
 private static final Map<String,String> NATIVE=load("bsl-prefix-native-options.json","ab79d4f133eb26db379a2c3e9c50c60532a1a7f522973aa56c9af279d3d63e78"),GENERATED=load("bsl-prefix-generated-options.json","6719f876e0d30c955d3c110223f5b19376a391bf2409bda6a923d932a19fd217");
 public static Map<String,String> nativeOptions(){return NATIVE;}public static Map<String,String> generatedOptions(){return GENERATED;}
 public static boolean nativeSource(Map<String,String> values){return NATIVE.size()==384&&NATIVE.equals(values);}
 public static boolean accepts(Map<String,String> values){if(values==null||GENERATED.size()!=398)return false;var copy=new TreeMap<>(values);if(!GENERATED.containsKey("VOXY_PATCH")&&"false".equals(copy.get("VOXY_PATCH")))copy.remove("VOXY_PATCH");for(String key:java.util.List.of("SHADERLOD_BSL_AO_HALF_RES","SHADERLOD_BSL_DEAD_MIP_CHAIN","SHADERLOD_BSL_SHADOW_FIELD","SHADERLOD_BSL_RAW_QUERY","SHADERLOD_BSL_DEPTH_PRIMING")){if(!java.util.Set.of("0","1").contains(copy.getOrDefault(key,"invalid")))return false;copy.put(key,"0");}return GENERATED.equals(copy);}
 /** Replay exact original384 native choices into the operative loaded profile.
  * Private graph controls are excluded because they are generated-only work. */
 public static boolean storedBaseMatches(Map<String,String> base,Map<String,String> loaded){
  if(base==null||loaded==null)return false;boolean pbr=accepts(loaded);var expected=pbr?NATIVE:com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions();
  if(expected.size()!=384)return false;
  // Public state stores explicit personal choices; absent keys replay the exact
  // pinned native defaults rather than being assumed operative PBR values.
  var replay=new TreeMap<>(com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions());
  for(var entry:base.entrySet()){if(!replay.containsKey(entry.getKey())||!entry.getValue().equals(loaded.get(entry.getKey())))return false;replay.put(entry.getKey(),entry.getValue());}
  if(!replay.equals(expected))return false;
  for(var entry:expected.entrySet())if(!entry.getValue().equals(loaded.get(entry.getKey())))return false;
  return true;
 }
 public static Map<String,String> functions(){return load("bsl-prefix-functions.json",FUNCTION_SHA);}public static final String FUNCTION_SHA="ab96ca586af39a53b5372f9ee1dd67d99086950325cf61d34e9f27cc6413c483";
}
