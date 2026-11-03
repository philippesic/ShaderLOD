package com.pp.shaderlod.optimization.sildurpixel;
import java.util.*;import com.google.gson.*;import java.security.MessageDigest;
/** Exact current profile; default131 includes the single new private graph bit. Native AA/settings never normalized. */
public final class SildurPixelContract{
 public static final String ID="sildurs-vibrant-shaders",OPTION="SHADERLOD_SILDUR_PIXEL_GRAPH";private static final Map<String,String> BASE=load();private SildurPixelContract(){}
 private static Map<String,String> load(){try(var in=SildurPixelContract.class.getResourceAsStream("/assets/shaderlod/sildurpixel/options131.json")){if(in==null)return Map.of();byte[] b=in.readAllBytes();if(!"eb120fc76cbe7d2811b774b904c5bff17e3937d6d10a48e479630fee0e54f2fa".equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b))))return Map.of();var m=new TreeMap<String,String>();for(var e:JsonParser.parseString(new String(b,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);}catch(Exception|LinkageError failed){return Map.of();}}
 public static Map<String,String> generated(String graph){if(!Set.of("0","1").contains(graph)||BASE.size()!=131)return Map.of();var m=new TreeMap<>(BASE);m.put(OPTION,graph);return Map.copyOf(m);}
 public static boolean options(Map<String,String> values){if(values==null||values.size()!=131||!Set.of("0","1").contains(values.getOrDefault(OPTION,"invalid")))return false;var n=new TreeMap<>(values);for(String key:List.of("SHADERLOD_SILDUR_AO_HALF_RES","SHADERLOD_SILDUR_DEPTH_PRIMING","SHADERLOD_SILDUR_SPATIAL_REUSE","SHADERLOD_SILDUR_LIGHTING_RATE","SHADERLOD_SILDUR_SHADOW_QUERY_GRAPH")){String v=n.put(key,"0");if(!Set.of("0","1").contains(v==null?"invalid":v))return false;}return generated(values.get(OPTION)).equals(n);}
 public static boolean projection(float[] p){return com.pp.shaderlod.optimization.sildurao.SildurAoContract.projection(p);}
 public static boolean viewport(int[] v,int w,int h,boolean reduced){return v!=null&&Arrays.equals(v,new int[]{0,0,reduced?(int)(w*.85f):w,reduced?(int)(h*.85f):h});}
}
