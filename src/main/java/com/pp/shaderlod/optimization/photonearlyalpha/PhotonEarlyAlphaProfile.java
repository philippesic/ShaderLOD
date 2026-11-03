package com.pp.shaderlod.optimization.photonearlyalpha;import java.util.*;import com.google.gson.*;public final class PhotonEarlyAlphaProfile{private PhotonEarlyAlphaProfile(){}private static final Map<String,String> EXPECTED=load();private static Map<String,String> load(){try(var in=PhotonEarlyAlphaProfile.class.getResourceAsStream("/assets/shaderlod/photonearlyalpha/optional394.json")){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b)).equals("0ba649f93425bd223b46ee0de06dbc138bc074b30d901e58e679051509dc554b"))return Map.of();var m=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return m.size()==395?Map.copyOf(m):Map.of();}catch(Exception|LinkageError e){return Map.of();}}private static boolean normalize(Map<String,String> m){
 if(m==null||!m.keySet().equals(EXPECTED.keySet()))return false;
 var n=new TreeMap<>(m);
 // Only mod-owned graph switches vary; every native material/AA/quality key stays exact.
 for(String key:List.of("SHADERLOD_PHOTON_DEPTH_PRIMING","SHADERLOD_PHOTON_DEAD_MIP_CHAIN")){
  if(!Set.of("0","1").contains(m.get(key)))return false;n.put(key,EXPECTED.get(key));
 }
 for(String key:List.of("SHADERLOD_SHARED_SPATIAL","SHADERLOD_LIGHTING_RATE_PROBE")){
  if(!Set.of("false","true").contains(m.get(key)))return false;n.put(key,EXPECTED.get(key));
 }
 return EXPECTED.equals(n);
}public static boolean accepts(Map<String,String> m){return EXPECTED.size()==395&&normalize(m);}}
