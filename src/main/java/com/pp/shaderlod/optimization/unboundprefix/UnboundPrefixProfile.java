package com.pp.shaderlod.optimization.unboundprefix;import com.google.gson.*;import java.util.*;import java.security.MessageDigest;public final class UnboundPrefixProfile{private UnboundPrefixProfile(){}private static Map<String,String> load(String path,String sha){try(var in=UnboundPrefixProfile.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/"+path)){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)).equals(sha))return Map.of();var m=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);}catch(Exception e){return Map.of();}}private static final Map<String,String> NATIVE2=load("unbound-prefix-native-rp2.json","7392816d9d25fc83877b3b5770c96c6e9c13ed2640a24416694dde314ddd2337");private static final Map<String,String> PROFILE2=load("unbound-prefix-options-rp2.json","60e8c2a23aef862a4164ba08d26029de37971c9a03f339d87a68d35a128353da");private static final Map<String,String> FUNCTIONS2=load("unbound-prefix-functions-rp2-world0.json","fde084e35c437bcf566fa3c07e19c5ebe48831d01a4588c97032307aef0de40b");private static final Map<String,String> NATIVE3=load("unbound-prefix-native-rp3.json","54049754da7f8eeefbe638d52b6b851b77b2f32682fbc457d1614b9a5d3627bf");private static final Map<String,String> PROFILE3=load("unbound-prefix-options-rp3.json","916c8a9d928ee5ab3f73d4d651a900f5fcbed1536e97a9c1fd57c603207a7daa");private static final Map<String,String> FUNCTIONS3=load("unbound-prefix-functions-rp3-world0.json","fde084e35c437bcf566fa3c07e19c5ebe48831d01a4588c97032307aef0de40b");private static Map<String,String> canonical(Map<String,String>a){if(a==null||!Set.of("0","1").contains(a.getOrDefault("SHADERLOD_UNBOUND_DEPTH_PRIMING","invalid")))return Map.of();var c=new TreeMap<>(a);c.put("SHADERLOD_UNBOUND_DEPTH_PRIMING","0");if(!Set.of("0","1").contains(c.put("SHADERLOD_UNBOUND_AO_HALF_RES","0")))return Map.of();if(!Set.of("0","1").contains(c.put("SHADERLOD_UNBOUND_DEAD_MIP_CHAIN","0")))return Map.of();for(String key:List.of("SHADERLOD_UNBOUND_SPATIAL_REUSE","SHADERLOD_UNBOUND_SPATIAL_RATE","SHADERLOD_UNBOUND_PIXEL_GRAPH"))if(!Set.of("0","1").contains(java.util.Objects.toString(c.remove(key),"invalid")))return Map.of();return c;}public static boolean accepts(Map<String,String>a){a=canonical(a);return PROFILE2.size()==408&&PROFILE3.size()==408&&(PROFILE2.equals(a)||PROFILE3.equals(a));}public static boolean nativeAccepts(Map<String,String>a){return NATIVE2.size()==398&&NATIVE3.size()==398&&(NATIVE2.equals(a)||NATIVE3.equals(a));}public static Map<String,String> expected(int rp){return rp==2?PROFILE2:rp==3?PROFILE3:Map.of();}public static Map<String,String> nativeExpected(int rp){return rp==2?NATIVE2:rp==3?NATIVE3:Map.of();}public static Map<String,String> functions(Map<String,String>a){a=canonical(a);return PROFILE2.equals(a)?FUNCTIONS2:PROFILE3.equals(a)?FUNCTIONS3:Map.of();}
 /** Replay the selection's exact stored choices over pinned native defaults.
  * Profile membership never substitutes for stored/loaded equality. */
 public static boolean storedBaseMatches(Map<String,String> base,Map<String,String> loaded){
  if(base==null||loaded==null||PROFILE2.size()!=408||PROFILE3.size()!=408)return false;
  var defaults=com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions();
  if(defaults.size()!=398)return false;
  var baseline=new TreeMap<>(defaults);
  for(var entry:PROFILE2.entrySet())if(!defaults.containsKey(entry.getKey()))baseline.put(entry.getKey(),entry.getValue());
  if(!baseline.equals(canonical(loaded))&&!accepts(loaded))return false;
  var replay=new TreeMap<>(defaults);
  for(var entry:base.entrySet()){
   String key=entry.getKey(),value=entry.getValue();
   if(defaults.containsKey(key)){replay.put(key,value);continue;}
   // The sole adapter-owned override is set by public Apply. Legal original
   // private bridge choices remain recoverable without changing native choices.
   if(key.equals("SHADERLOD_REFINEMENT_ADAPTER")||key.equals("SHADERLOD_UNBOUND_DEPTH_PRIMING")||key.equals("SHADERLOD_UNBOUND_AO_HALF_RES")||key.equals("SHADERLOD_UNBOUND_DEAD_MIP_CHAIN")||key.equals("SHADERLOD_UNBOUND_SPATIAL_REUSE")||key.equals("SHADERLOD_UNBOUND_SPATIAL_RATE")||key.equals("SHADERLOD_UNBOUND_PIXEL_GRAPH")){
    if(!Set.of("0","1").contains(value))return false;continue;
   }
   if(baseline.containsKey(key)){if(!Objects.equals(value,loaded.get(key)))return false;continue;}
   // Unknown personal undeclared sidecars are retained; unknown private controls
   // cannot certify a generated semantic consumer. No loaded extra key is allowed.
   if(key.startsWith("SHADERLOD_"))return false;
  }
  for(var entry:replay.entrySet())if(!Objects.equals(entry.getValue(),loaded.get(entry.getKey())))return false;
  return true;
 }
}