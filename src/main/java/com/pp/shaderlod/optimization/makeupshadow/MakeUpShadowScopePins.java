package com.pp.shaderlod.optimization.makeupshadow;
import java.util.*;
/** Exact closed native-loop APIs and frozen experimental-bootstrap refusal. */
public final class MakeUpShadowScopePins {
 private MakeUpShadowScopePins(){}
 private static final boolean BOOTSTRAP_SAFE=safeProperties(System.getProperties());
 private static String status="not checked";
 private static Boolean pinned;
 public static boolean safeProperties(Properties properties){
  var ordinary=Set.of("shaderlod.dev.compare","shaderlod.dev.performanceStack","shaderlod.dev.benchmarkTunerProbe","shaderlod.dev.comparisonFullscreen","shaderlod.dev.disableGpuQueries","shaderlod.dev.disableShadowSamplerReuse");
  for(var name:properties.stringPropertyNames())if(name.startsWith("shaderlod.dev.")&&!ordinary.contains(name)){
   var value=properties.getProperty(name,"").trim();if(!Set.of("","false","off","0").contains(value))return false;
  }
  return !Boolean.parseBoolean(properties.getProperty("shaderlod.experimentalRendererResearch","false"));
 }
 public static boolean bootstrapSafe(){return BOOTSTRAP_SAFE;}
 public static String status(){return status+"; bootstrapSafe="+BOOTSTRAP_SAFE;}
 public static boolean exact(){
  if(pinned!=null)return pinned;
  if(!"Legacy".equals(MakeUpShadowPins.kind())){status="unsupported shadow backend";return pinned=false;}
  try(var input=MakeUpShadowScopePins.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/makeup-shadow-scope-api.json")){
   if(input==null){status="missing scope pins";return pinned=false;}var bytes=input.readAllBytes();
   if(!"390e72e11791b71a6f232a582105e902dce388189205810901a3b722d9cf0c3c".equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))){status="scope pin resource mismatch";return pinned=false;}
   for(var pin:com.google.gson.JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
    try(var actual=MakeUpShadowScopePins.class.getClassLoader().getResourceAsStream(pin.getKey())){
     if(actual==null||!pin.getValue().getAsString().equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(actual.readAllBytes())))){status="scope API refused "+pin.getKey();return pinned=false;}
    }
   status="pinned native sampler scope";return pinned=true;
  }catch(Exception|LinkageError unavailable){status="scope API "+unavailable.getClass().getSimpleName();return pinned=false;}
 }
}
