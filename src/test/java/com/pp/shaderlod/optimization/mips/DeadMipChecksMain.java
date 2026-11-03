package com.pp.shaderlod.optimization.mips;
import java.util.*;import java.nio.file.*;import com.google.gson.*;
public final class DeadMipChecksMain{
 private static int checks;private static void check(boolean pass,String reason){checks++;if(!pass)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  var proof=JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
  JsonObject requested=null,selected=null;for(var entry:proof.getAsJsonArray("api")){var row=entry.getAsJsonObject();if(row.get("tuple").getAsString().equals(args[0]))requested=row;if(row.get("tuple").getAsString().equals(DeadMipAdmission.apiTuple()))selected=row;}
  check(requested!=null&&selected!=null&&requested.getAsJsonObject("classes").equals(selected.getAsJsonObject("classes")),"actual API variant exact class bytes; identical Fabric/NeoForge native variants share identity");
  for(String family:List.of("bsl-shaders","photon")){
   var expected=new TreeMap<String,String>();proof.getAsJsonObject("families").getAsJsonObject(family).getAsJsonObject("options").entrySet().forEach(e->expected.put(e.getKey(),e.getValue().getAsString()));String option=family.equals("photon")?"SHADERLOD_PHOTON_DEAD_MIP_CHAIN":"SHADERLOD_BSL_DEAD_MIP_CHAIN";
   for(String value:List.of("0","1")){var actual=new TreeMap<>(expected);actual.put(option,value);check(DeadMipAdmission.expectedOptions(family,actual),"source-owner real full map permits controlled "+value+" "+family);actual.put(option,"2");check(!DeadMipAdmission.expectedOptions(family,actual),"unknown control refused");actual=new TreeMap<>(expected);actual.remove(option);check(!DeadMipAdmission.expectedOptions(family,actual),"missing control refused");actual=new TreeMap<>(expected);actual.put("inventedNativeKey","false");check(!DeadMipAdmission.expectedOptions(family,actual),"extra full-map key refused");actual=new TreeMap<>(expected);actual.put(family.equals("photon")?"ENVIRONMENT_REFLECTIONS":"TAA","false");check(!DeadMipAdmission.expectedOptions(family,actual),"native quality/map change refused");}
  }
  check(!DeadMipAdmission.expectedOptions("astralex",Map.of()),"no505/broad1022 map fabricated; real original ShaderPack consumer required");
  DeadMipAdmission.initialize();DeadMipAdmission.invalidate();check(DeadMipAdmission.status("astralex").equals("not inspected"),"reload invalidates diagnostic epoch; no cached setup permission");
  System.out.println("PASS "+checks+" actual API selector + authentic full-map controlled0/1/refusals; no GL/callback readiness claim");
 }
}
