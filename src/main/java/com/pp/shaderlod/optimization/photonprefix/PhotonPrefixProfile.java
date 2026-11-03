package com.pp.shaderlod.optimization.photonprefix;
import java.util.*;import java.security.MessageDigest;import com.google.gson.*;
/** Exact optional native Photon POM profile; no native quality option relaxation. */
public final class PhotonPrefixProfile {
 private PhotonPrefixProfile(){}
 private static final Map<String,String> FUNCTIONS=load();
 private static Map<String,String> load(){try(var in=PhotonPrefixProfile.class.getResourceAsStream("/assets/shaderlod/photonprefix/functions.json")){
  if(in==null)return Map.of();byte[] bytes=in.readAllBytes();
  if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals("a2ef446e461fcb82e081adc74dd3d3ff78d720b89992f303b484cfd34777f963"))return Map.of();
  var result=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())result.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(result);
 }catch(Exception|LinkageError failure){return Map.of();}}
 public static boolean accepts(Map<String,String> actual){return FUNCTIONS.size()==7&&com.pp.shaderlod.optimization.photonearlyalpha.PhotonEarlyAlphaProfile.accepts(actual);}
 public static Map<String,String> functions(){return FUNCTIONS;}
}
