package com.pp.shaderlod.optimization.mips;
import com.google.gson.JsonParser;
import com.pp.shaderlod.optimization.CompileOptionControl;
import java.util.*;
import java.security.MessageDigest;
/** Exact native1015/AA3 singleton; only owned adapter/mip controls differ. */
public final class AstraMipLeanGraph {
 private AstraMipLeanGraph(){}
 private static final Map<String,String> PROFILE=load();
 private static Map<String,String> load(){
  try(var in=AstraMipLeanGraph.class.getResourceAsStream("/assets/shaderlod/astramips/options1027.json")){
   if(in==null)return Map.of();byte[] bytes=in.readAllBytes();
   if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals("e4fe7e390da57e8db04b438e5f498679aa78e4d5938fc916244f8874a797acf3"))return Map.of();
   var result=new TreeMap<String,String>();JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->result.put(e.getKey(),e.getValue().getAsString()));
   return result.size()==1027&&"3".equals(result.get("AA"))?Map.copyOf(result):Map.of();
  }catch(Exception|LinkageError unavailable){return Map.of();}
 }
 public static boolean nativeProfile(Map<String,String> actual){
  if(PROFILE.isEmpty()||actual==null||actual.size()!=1015)return false;
  var expected=new TreeMap<>(PROFILE);expected.keySet().removeIf(k->k.startsWith("SHADERLOD_"));
  return expected.size()==1015&&expected.equals(actual);
 }
 public static boolean profile(Map<String,String> actual){return actual!=null&&"0".equals(actual.get(BslLeanGraph.ADAPTER))&&transitionProfile(actual);}
 public static boolean transitionProfile(Map<String,String> actual){
  if(PROFILE.isEmpty()||actual==null||actual.size()!=PROFILE.size()||!Set.of("0","1").contains(actual.getOrDefault(BslLeanGraph.ADAPTER,"invalid"))||!Set.of("0","1").contains(actual.getOrDefault(CompileOptionControl.ASTRALEX_DEAD_MIP,"invalid")))return false;
  var copy=new TreeMap<>(actual);copy.put(BslLeanGraph.ADAPTER,"0");copy.put(CompileOptionControl.ASTRALEX_DEAD_MIP,"0");return PROFILE.equals(copy);
 }
}
