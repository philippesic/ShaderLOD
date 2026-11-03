package com.pp.shaderlod.experiment.makeup;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.ShaderLodConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.irisshaders.iris.Iris;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
/** Private finite MakeUp experiment. All unavailable/unknown conditions preserve native sampling. */
public final class MakeupEarlyAlphaR1 {
 public static final Set<String> ARCHIVES=Set.of(
  "842608ef178e57d23a5eab5a58a9179624a1ddf6f62af5f7bd2fff2cdc246897",
  "e57635ccee2de8c9cd08dd3fdb234022f702800ab1690e54f0cc007587a87915");
 public static final String OPTIONS_SHA="0c11f31b3d5e51d9d1bff56a84ac13a3f1265472f7313997a493c74435a418bb";
 private static Object certifiedPack;
 private static String certifiedName;
 private static Map<String,String> expected;
 private static int sampledFrame=Integer.MIN_VALUE;
 private static boolean sampledMatch;
 private static Object interfacePack;
 private static boolean lastInterfaceHasAlphaTest;
 private MakeupEarlyAlphaR1(){}
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 public static boolean settingsEnabled(boolean loaded,boolean enabled,LodPreset preset){
  return loaded && enabled && preset==LodPreset.PERFORMANCE;
 }
 public static boolean optionsMatch(Map<String,String> actual,Map<String,String> wanted){
  return actual!=null && wanted!=null && wanted.size()==100 && actual.equals(wanted);
 }
 public static boolean packPinned(){
  if(!Boolean.getBoolean("shaderlod.dev.makeupEarlyAlphaR1") || Iris.isFallback())return false;
  try {
   if(!SharedConstants.getCurrentVersion().getName().equals("1.21.1") || !ModList.get().isLoaded("shaderlod") ||
      !ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253"))return false;
   for(String id:new String[]{"distanthorizons","voxy","create","flywheel"})if(ModList.get().isLoaded(id))return false;
   Object pack=Iris.getCurrentPack().orElseThrow();String name=Iris.getCurrentPackName();
   if(pack!=certifiedPack || !name.equals(certifiedName)) {
    certifiedPack=null;certifiedName=null;sampledFrame=Integer.MIN_VALUE;
    var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();
    if(!path.startsWith(dir) || path.equals(dir) || !ARCHIVES.contains(sha(Files.readAllBytes(path))))return false;
    var bytes=Files.readAllBytes(Path.of(System.getProperty("shaderlod.dev.makeupEarlyAlphaOptionsPin")));
    if(!OPTIONS_SHA.equals(sha(bytes)))return false;
    expected=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),new TypeToken<Map<String,String>>(){}.getType());
    if(expected==null || expected.size()!=100)return false;
    certifiedPack=pack;certifiedName=name;
   }
   int frame=net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.getAsInt();
   if(frame==sampledFrame)return sampledMatch;
   var options=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();var declared=options.getOptionSet();var values=options.getOptionValues();
   var actual=new TreeMap<String,String>();
   for(String key:declared.getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
   for(String key:declared.getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
   sampledFrame=frame;return sampledMatch=optionsMatch(actual,expected);
  }catch(Exception|LinkageError unavailable){return false;}
 }
 public static void clearInterface(){lastInterfaceHasAlphaTest=false;interfacePack=null;}
 public static void admitCurrentPack(boolean hasAlphaTest){
  interfacePack=Iris.getCurrentPack().orElse(null);lastInterfaceHasAlphaTest=hasAlphaTest;
 }
 /** Captured at the exact sequential native program build, after its own transform. */
 public static Object eligibleBuildPack(){return lastInterfaceHasAlphaTest && interfacePack==Iris.getCurrentPack().orElse(null)?interfacePack:null;}
 public static boolean enabled(Object builtPack){
  if(!ShaderLodConfig.SPEC.isLoaded())return false;
  var client=Minecraft.getInstance();
  return builtPack!=null && builtPack==Iris.getCurrentPack().orElse(null) && client.level!=null &&
    client.level.dimension()==Level.OVERWORLD && packPinned() &&
    settingsEnabled(ShaderLodConfig.SPEC.isLoaded(),ShaderLodConfig.snapshot().enabled(),ShaderLodConfig.PRESET.get());
 }
}
