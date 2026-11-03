package com.pp.shaderlod.optimization.reimaginedprefix;import com.google.gson.*;import java.util.*;import java.security.MessageDigest;public final class ReimaginedPrefixProfile{private ReimaginedPrefixProfile(){}private static Map<String,String> load(String path,String sha){try(var in=ReimaginedPrefixProfile.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/"+path)){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)).equals(sha))return Map.of();var m=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);}catch(Exception e){return Map.of();}}private static final Map<String,String> NATIVE2=load("reimagined-prefix-native-rp2.json","b49f771d9d507d9741824a06b63f7a1d00114f9d8b4b4ab4cc9ca0f7eb617bcc");private static final Map<String,String> PROFILE2=load("reimagined-prefix-options-rp2.json","f7a792548b4a1891910639cf43a3435c8fa53deb06768f4022582225b8d26dfd");private static final Map<String,String> FUNCTIONS2=load("reimagined-prefix-functions-rp2-world0.json","d63df89f3f48328ecfaf5a08fa6384ef8baaf663716cd8c5d4680e33408813e7");private static final Map<String,String> NATIVE3=load("reimagined-prefix-native-rp3.json","e134075d9f81e840e86cb0e7a894e8efc2a57a2b3d447a04d0e68934776ef88e");private static final Map<String,String> PROFILE3=load("reimagined-prefix-options-rp3.json","566159450d74311e8a1a47ec201e881f3eea42f1be56aa79bb9067f2dadcbf1c");private static final Map<String,String> FUNCTIONS3=load("reimagined-prefix-functions-rp3-world0.json","d63df89f3f48328ecfaf5a08fa6384ef8baaf663716cd8c5d4680e33408813e7");private static Map<String,String> normalized(Map<String,String>a){if(a==null)return Map.of();var n=new TreeMap<>(a);if(!Set.of("0","1").contains(n.put("SHADERLOD_REIMAGINED_AO_HALF_RES","0")))return Map.of();if(!Set.of("0","1").contains(n.put("SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN","0")))return Map.of();for(String k:List.of("SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH")){String v=n.remove(k);if(v==null||!Set.of("0","1").contains(v))return Map.of();}return n;}public static boolean accepts(Map<String,String>a){a=normalized(a);return PROFILE2.size()==408&&PROFILE3.size()==408&&(PROFILE2.equals(a)||PROFILE3.equals(a));}public static boolean nativeAccepts(Map<String,String>a){return NATIVE2.size()==398&&NATIVE3.size()==398&&(NATIVE2.equals(a)||NATIVE3.equals(a));}public static Map<String,String> expected(int rp){return rp==2?PROFILE2:rp==3?PROFILE3:Map.of();}public static Map<String,String> nativeExpected(int rp){return rp==2?NATIVE2:rp==3?NATIVE3:Map.of();}public static Map<String,String> functions(Map<String,String>a){a=normalized(a);return PROFILE2.equals(a)?FUNCTIONS2:PROFILE3.equals(a)?FUNCTIONS3:Map.of();}
 /** Replay the selection's exact stored choices over pinned native defaults.
  * Profile membership never substitutes for stored/loaded equality. */
 public static boolean storedBaseMatches(Map<String,String> base,Map<String,String> loaded){
  if(base==null||loaded==null||PROFILE2.size()!=408||PROFILE3.size()!=408)return false;
  String depth=loaded.get("SHADERLOD_REIMAGINED_DEPTH_PRIMING");if(!Set.of("0","1").contains(depth))return false;
  var normalized=new TreeMap<>(loaded);normalized.put("SHADERLOD_REIMAGINED_DEPTH_PRIMING","0");if(!Set.of("0","1").contains(normalized.put("SHADERLOD_REIMAGINED_AO_HALF_RES","0")))return false;if(!Set.of("0","1").contains(normalized.put("SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN","0")))return false;for(String k:List.of("SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH")){String v=normalized.remove(k);if(v==null||!Set.of("0","1").contains(v))return false;}loaded=normalized;
  var defaults=com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions();
  if(defaults.size()!=398)return false;
  var baseline=new TreeMap<>(defaults);
  for(var entry:PROFILE2.entrySet())if(!defaults.containsKey(entry.getKey()))baseline.put(entry.getKey(),entry.getValue());
  if(!baseline.equals(loaded)&&!accepts(loaded))return false;
  var replay=new TreeMap<>(defaults);
  for(var entry:base.entrySet()){
   String key=entry.getKey(),value=entry.getValue();
   if(defaults.containsKey(key)){replay.put(key,value);continue;}
   // The sole adapter-owned override is set by public Apply. Legal original
   // private bridge choices remain recoverable without changing native choices.
   if(Set.of("SHADERLOD_REFINEMENT_ADAPTER","SHADERLOD_REIMAGINED_DEPTH_PRIMING","SHADERLOD_REIMAGINED_AO_HALF_RES","SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN","SHADERLOD_REIMAGINED_SPATIAL_REUSE","SHADERLOD_REIMAGINED_SPATIAL_RATE","SHADERLOD_REIMAGINED_PIXEL_GRAPH").contains(key)){
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