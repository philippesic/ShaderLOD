package com.pp.shaderlod.optimization.solasprefix;import com.google.gson.*;import java.util.*;import java.security.MessageDigest;public final class SolasPrefixProfile{private SolasPrefixProfile(){}private static Map<String,String> load(String path,String sha){try(var in=SolasPrefixProfile.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/"+path)){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)).equals(sha))return Map.of();var m=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);}catch(Exception e){return Map.of();}}private static final Map<String,String> EXPECTED=load("solas-prefix-options.json","f418604fcab599adcc92d9dd037b219ce540212ffbc1c7de344ddd11c1e4e65a");private static final Map<String,String> FUNCTIONS=load("solas-prefix-functions.json","cd9c9aa1b650b0ff450a110d38df492721337ff69270a911c21f3091d1b7aa10");private static final Map<String,String> FULL_NORMAL_CACHE_FUNCTIONS=load("solas-prefix-functions-nf1211-full-query.json","05bca96c873f4aad8bfcb76464593e1a837fa0a7cf372bf92b0bdbe0179285aa");public static Map<String,String> fullNormalCacheFunctions(){return FULL_NORMAL_CACHE_FUNCTIONS;}public static boolean accepts(Map<String,String> actual){return EXPECTED.size()==435&&EXPECTED.equals(actual);}public static boolean acceptsNf1211(Map<String,String> actual){if(actual==null)return false;var n=new TreeMap<>(actual);String pixel=n.remove("SHADERLOD_SOLAS_PIXEL_GRAPH");if(pixel!=null&&!Set.of("0","1").contains(pixel))return false;String mip=n.remove("SHADERLOD_SOLAS_DEAD_MIP_CHAIN");if(mip!=null&&!Set.of("0","1").contains(mip))return false;String ao=n.remove("SHADERLOD_SOLAS_AO_HALF_RES");if(ao!=null&&!Set.of("0","1").contains(ao))return false;String depth=n.put("SHADERLOD_SOLAS_DEPTH_PRIMING","0");if(!"0".equals(depth)&&!"1".equals(depth))return false;return accepts(n);}public static Map<String,String> functions(){return FUNCTIONS;}public static Map<String,String> expected(){return EXPECTED;}
 /** Replay this public selection's exact personal native choices over pinned defaults.
  * An admitted profile is never a substitute for stored/live choice equality. */
 public static boolean storedBaseMatches(Map<String,String> base,Map<String,String> loaded){
  if(base==null||loaded==null||EXPECTED.size()!=435)return false;
  var defaults=com.pp.shaderlod.integration.solasquad.SolasQuadEligibility.nativeOptions();
  if(defaults.size()!=428)return false;
  var baseline=new TreeMap<>(defaults);
  for(var entry:EXPECTED.entrySet())if(!defaults.containsKey(entry.getKey()))baseline.put(entry.getKey(),entry.getValue());
  if(!baseline.equals(loaded)&&!EXPECTED.equals(loaded))return false;
  var replay=new TreeMap<>(defaults);
  for(var entry:base.entrySet()){
   String key=entry.getKey(),value=entry.getValue();
   if(defaults.containsKey(key)){replay.put(key,value);continue;}
   // Only the actual public Apply-owned adapter bridge may differ from a legal
   // original private0/1 choice. Generated helper declarations remain exact.
   if(key.equals("SHADERLOD_REFINEMENT_ADAPTER")){
    if(!Set.of("0","1").contains(value))return false;continue;
   }
   if(baseline.containsKey(key)){if(!Objects.equals(value,loaded.get(key)))return false;continue;}
   // Undeclared personal sidecars are preserved, never treated as native options.
   if(key.startsWith("SHADERLOD_"))return false;
  }
  for(var entry:replay.entrySet())if(!Objects.equals(entry.getValue(),loaded.get(entry.getKey())))return false;
  return true;
 }
}