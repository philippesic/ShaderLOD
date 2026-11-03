package com.pp.shaderlod.optimization.blisspixel;
import com.google.gson.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;
/** Exact original/mapped class resources for ten saved native tuple APIs, with explicit draw ABI. */
public final class BlissPixelApiPins {
 private static final String BACKEND=check();private BlissPixelApiPins(){}
 public static boolean exact(){return BACKEND!=null;}public static String backend(){return BACKEND;}
 private static String check(){try(var in=BlissPixelApiPins.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/bliss-pixel-api.json")){
  if(in==null)return null;byte[] all=in.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all)).equals("d0f0bf75ae8ebb12179719c8d201030e7aaa30767d67529ce25c8845dd462161"))return null;for(var row:JsonParser.parseString(new String(all,StandardCharsets.UTF_8)).getAsJsonArray()){
   boolean match=true;var r=row.getAsJsonObject();for(var pin:r.getAsJsonObject("classes").entrySet())try(var b=BlissPixelApiPins.class.getClassLoader().getResourceAsStream(pin.getKey())){
    if(b==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.readAllBytes())).equals(pin.getValue().getAsString())){match=false;break;}
   }if(match)return r.get("backend").getAsString();
  }
 }catch(Exception|LinkageError unavailable){}return null;}
}
