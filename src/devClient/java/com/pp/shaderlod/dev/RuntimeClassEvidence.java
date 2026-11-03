package com.pp.shaderlod.dev;
import com.google.gson.*;
import java.util.*;
import java.security.*;
/** Untimed raw class-resource/source-location witness; no initialization or GL calls. */
final class RuntimeClassEvidence {
 static JsonObject read() {
  var out=new JsonObject();
  for(String name:List.of("com.pp.shaderlod.optimization.vrs.LwjglVrsDriver","com.pp.shaderlod.optimization.vrs.BloomVrsRuntime","com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime","com.pp.shaderlod.optimization.mips.DeadMipAdmission","com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationGl","com.pp.shaderlod.optimization.g07.G07SamplingAdmission","com.pp.shaderlod.optimization.g07.G07SamplingRuntime")) {
   var row=new JsonObject();
   try {
    Class<?> type=Class.forName(name,false,com.pp.shaderlod.ShaderLod.class.getClassLoader());
    var resource=type.getResource(type.getSimpleName()+".class");if(resource==null)throw new IllegalStateException("Classresource absent");
    try(var in=resource.openStream()){row.addProperty("raw_class_sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}
    row.addProperty("resource",resource.toString());var code=type.getProtectionDomain().getCodeSource();row.addProperty("code_source",code==null?"unavailable":code.getLocation().toString());
   }catch(Exception|LinkageError unavailable){row.addProperty("unavailable",unavailable.toString());}
   out.add(name,row);
  }
  return out;
 }
}
