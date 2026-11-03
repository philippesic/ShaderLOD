package com.pp.shaderlod.optimization.blisspixel;
import java.util.*;import com.google.gson.*;import java.nio.charset.StandardCharsets;
public final class BlissPixelContract {
 public static final String ID="bliss-shader",OPTION="SHADERLOD_BLISS_PIXEL_GRAPH";
 private BlissPixelContract(){}
 private static final Map<String,String> BASE=load();
 private static Map<String,String> load(){try(var in=BlissPixelContract.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/bliss-pixel-options.json")){if(in==null)return Map.of();byte[] bytes=in.readAllBytes();if(!"4f76329838f36723336b070b78eebcbc29a33afcf063c3c6559ac4ae30535de9".equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))))return Map.of();var m=new TreeMap<String,String>();JsonParser.parseReader(new java.io.InputStreamReader(new java.io.ByteArrayInputStream(bytes),StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e->m.put(e.getKey(),e.getValue().getAsString()));return Map.copyOf(m);}catch(Exception e){return Map.of();}}
 public static Map<String,String> generated(String graph){if(!Set.of("0","1").contains(graph))return Map.of();var m=new TreeMap<>(BASE);m.put(OPTION,graph);return Map.copyOf(m);}
 public static boolean options(Map<String,String> actual){return !BASE.isEmpty()&&actual!=null&&Set.of("0","1").contains(actual.getOrDefault(OPTION,"invalid"))&&nativeOptions(actual);}
 private static boolean nativeOptions(Map<String,String> actual){var m=new TreeMap<>(actual);for(String key:List.of("SHADERLOD_BLISS_AO_HALF_RES","SHADERLOD_BLISS_SPATIAL_REUSE","SHADERLOD_BLISS_LIGHTING_RATE")){String bit=m.put(key,"0");if(!Set.of("0","1").contains(bit==null?"invalid":bit))return false;}return generated(actual.get(OPTION)).equals(m);}
 public static boolean spatial(Map<String,String> values){return values!=null&&"1".equals(values.get(OPTION))&&("1".equals(values.get("SHADERLOD_BLISS_SPATIAL_REUSE"))||"1".equals(values.get("SHADERLOD_BLISS_LIGHTING_RATE")));}
 public static String preparation(Map<String,String> values){return spatial(values)?"composite6":"composite5";}
 public static String reconstruction(Map<String,String> values){return spatial(values)?"composite7":"composite6";}
 public static boolean projection(float[] p){if(p==null||p.length!=16)return false;for(float x:p)if(!Float.isFinite(x)||Math.abs(x)>1e10f)return false;return p[2]==0&&p[6]==0&&p[3]==0&&p[7]==0&&p[11]==-1&&p[15]==0&&p[14]!=0;}
}
