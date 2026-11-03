package com.pp.shaderlod.optimization.makeupao;
import com.google.gson.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;
/** Exact original/mapped class resources for ten saved native tuple APIs, with explicit draw ABI. */
public final class MakeUpAoApiPins {
 private static String STATUS="API not inspected";private static String TUPLE;private static final String BACKEND=check();private MakeUpAoApiPins(){}
 public static boolean exact(){return BACKEND!=null;}public static boolean exact(String tuple){return BACKEND!=null&&java.util.Objects.equals(TUPLE,tuple);}public static String backend(){return BACKEND;}public static String status(){return STATUS;}
 private static String check(){try(var in=MakeUpAoApiPins.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/makeup-ao-api.json")){
  if(in==null){STATUS="qualification resource missing";return null;}byte[] all=in.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all)).equals("2759255770565945b45b4387070d2aea6223968270ccb3922acd50d4acbd7243"))return null;for(var row:JsonParser.parseString(new String(all,StandardCharsets.UTF_8)).getAsJsonArray()){
   boolean match=true;var r=row.getAsJsonObject();for(var pin:r.getAsJsonObject("classes").entrySet())try(var b=MakeUpAoApiPins.class.getClassLoader().getResourceAsStream(pin.getKey())){
    if(b==null){STATUS="class resource missing: "+pin.getKey();match=false;break;}if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.readAllBytes())).equals(pin.getValue().getAsString())){STATUS="class SHA mismatch: "+pin.getKey();match=false;break;}
   }if(match){TUPLE=r.get("tuple").getAsString();STATUS="exact "+TUPLE+"/"+r.get("backend").getAsString();return r.get("backend").getAsString();}
  }
 }catch(Exception|LinkageError unavailable){STATUS="API resource unavailable: "+unavailable.getClass().getSimpleName();}return null;}
}
