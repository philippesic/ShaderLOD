package com.pp.shaderlod.optimization.makeupao;
import com.google.gson.JsonParser;
import java.util.HexFormat;
import java.security.MessageDigest;
/** Current NeoForge1.21.1 composite compiler only; other tuples retain legacy proof. */
public final class MakeUpAoCompilationPins {
 private MakeUpAoCompilationPins(){}
 private static String status="not inspected";
 private static final boolean EXACT=check();
 public static boolean exact(){return EXACT;}
 public static String status(){return status;}
 private static boolean check(){
  if(!MakeUpAoApiPins.exact("neoforge1.21.1")||!com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin.exactApi())return false;
  try(var input=MakeUpAoCompilationPins.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/makeup-ao-compilation-api.json")){
   if(input==null)return false;byte[] bytes=input.readAllBytes();
   if(!sha(bytes).equals("9281310dbb34e4c9b2f3d3e031f3a16f6b51bc156836615eeda77fbd3a344ebf"))return false;
   for(var pin:JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
    try(var resource=MakeUpAoCompilationPins.class.getClassLoader().getResourceAsStream(pin.getKey())){
     if(resource==null||!sha(resource.readAllBytes()).equals(pin.getValue().getAsString())){status="compiler class refused: "+pin.getKey();return false;}
    }
   status="pinned NeoForge1.21.1 composite compilation";return true;
  }catch(Exception|LinkageError unavailable){status="compiler unavailable: "+unavailable.getClass().getSimpleName();return false;}
 }
 private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
