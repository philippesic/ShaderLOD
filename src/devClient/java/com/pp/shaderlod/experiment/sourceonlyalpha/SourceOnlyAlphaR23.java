package com.pp.shaderlod.experiment.sourceonlyalpha;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.*;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.neoforged.fml.ModList;
/** Private source-only prototype. Admission runs at real Sodium program construction only. */
public final class SourceOnlyAlphaR23 {
 private SourceOnlyAlphaR23() {}
 public static final String PROPERTY="shaderlod.dev.sourceOnlyAlphaR23";
 private static final String ARCHIVE="be4171156c454460c7a2a077a17873397acef4d5acab1429dbe3fb75ad7024c1";
 private static final String GUARDED_ALU_ARCHIVE="2605b7845400a58b208cc3f347281040ef9c1e853018b67091980e17bb762ca5";
 private static final String ASAHI_ALU_ARCHIVE="e86463d58cb2b58911a7f20052ac8a3f6ca2fcc81773fab5e34dd5451bcc3567";
 private static final String ASAHI_LATE_KILL_ARCHIVE="05d8b5669946000355df8abe95e3028e893059864c752a3042ce176c14e43dbb";
 private static final Map<String,String> NATIVE=new Gson().fromJson("{\"AA_TYPE\":\"2\",\"ACERCADE\":\"0\",\"AO\":\"1\",\"AOSTEPS\":\"5\",\"AO_STRENGTH\":\"0.70\",\"AVOID_DARK_LEVEL\":\"0.030\",\"BLACK_ENTITY_FIX\":\"0\",\"BLOCKLIGHT_TEMP\":\"1\",\"BLOOM\":\"true\",\"BLOOM_SAMPLES\":\"3.0\",\"CAVEENTITY_V\":\"true\",\"CHROMA_ABER\":\"0\",\"CHROMA_ABER_STRENGTH\":\"0.04\",\"CLOUD_REFLECTION\":\"true\",\"CLOUD_SPEED\":\"0\",\"CLOUD_STEPS_AVG\":\"10\",\"CLOUD_VOL_STYLE\":\"0\",\"COLORED_SHADOW\":\"true\",\"COLOR_BLINDNESS\":\"false\",\"COLOR_BLIND_MODE\":\"0\",\"COLOR_SCHEME\":\"1\",\"DEBUG_MODE\":\"false\",\"DH_WATER\":\"true\",\"DOF\":\"false\",\"DOF_STRENGTH\":\"0.09\",\"DYN_HAND_LIGHT\":\"true\",\"EMMISIVE_V\":\"true\",\"END_CLOUDS\":\"true\",\"FOG_ACTIVE\":\"true\",\"FOG_ADJUST\":\"2.0\",\"FOLIAGE_V\":\"true\",\"GBUFFER_WEATHER\":\"true\",\"HORIZON_DAY_COLOR_B\":\"1.3\",\"HORIZON_DAY_COLOR_G\":\"0.91\",\"HORIZON_DAY_COLOR_R\":\"0.65\",\"HORIZON_NIGHT_COLOR_B\":\"0.039\",\"HORIZON_NIGHT_COLOR_G\":\"0.031\",\"HORIZON_NIGHT_COLOR_R\":\"0.021\",\"HORIZON_SUNSET_COLOR_B\":\"0.38\",\"HORIZON_SUNSET_COLOR_G\":\"0.65\",\"HORIZON_SUNSET_COLOR_R\":\"1.0\",\"IMAGE_SHARPENING\":\"false\",\"LIGHT_DAY_COLOR_B\":\"0.79\",\"LIGHT_DAY_COLOR_G\":\"0.84\",\"LIGHT_DAY_COLOR_R\":\"0.90\",\"LIGHT_NIGHT_COLOR_B\":\"0.06\",\"LIGHT_NIGHT_COLOR_G\":\"0.05\",\"LIGHT_NIGHT_COLOR_R\":\"0.05\",\"LIGHT_SUNSET_COLOR_B\":\"0.35\",\"LIGHT_SUNSET_COLOR_G\":\"0.59\",\"LIGHT_SUNSET_COLOR_R\":\"1\",\"MATERIAL_GLOSS\":\"true\",\"MOTION_BLUR\":\"false\",\"MOTION_BLUR_SAMPLES\":\"4\",\"MOTION_BLUR_STRENGTH\":\"1.0\",\"NETHER\":\"true\",\"NETHER_FOG_DISTANCE\":\"1\",\"NIGHT_BRIGHT\":\"0.60\",\"NO_SHADOWS\":\"true\",\"NV_COLOR_B\":\"1.0\",\"NV_COLOR_G\":\"0.8\",\"NV_COLOR_R\":\"0.5\",\"OMNI_TINT_CUSTOM\":\"0.3\",\"REFLECTION_SLIDER\":\"2\",\"REFRACTION\":\"1\",\"SHADER_BASIC\":\"true\",\"SHADOW_BLUR\":\"2.0\",\"SHADOW_CASTING\":\"true\",\"SHADOW_DISTANCE_SLIDER\":\"1\",\"SHADOW_LOCK\":\"0\",\"SHADOW_QTY_SLIDER\":\"2\",\"SHADOW_TYPE\":\"1\",\"SHARPENING\":\"0.5\",\"SUN_REFLECTION\":\"1\",\"THE_END\":\"true\",\"TINTED_WATER\":\"1\",\"UNKNOWN_DIM\":\"true\",\"USE_BASIC_SH\":\"true\",\"VANILLA_WATER\":\"false\",\"VOL_LIGHT\":\"1\",\"V_CLOUDS\":\"1\",\"WATER_ABSORPTION\":\"0.10\",\"WATER_COLOR_B\":\"0.11\",\"WATER_COLOR_G\":\"0.1\",\"WATER_COLOR_R\":\"0.05\",\"WATER_COLOR_SOURCE\":\"0\",\"WATER_F\":\"true\",\"WATER_TEXTURE\":\"0\",\"WATER_TURBULENCE\":\"0.9\",\"WAVING\":\"1\",\"ZENITH_DAY_COLOR_B\":\"0.36\",\"ZENITH_DAY_COLOR_G\":\"0.24\",\"ZENITH_DAY_COLOR_R\":\"0.14\",\"ZENITH_NIGHT_COLOR_B\":\"0.025\",\"ZENITH_NIGHT_COLOR_G\":\"0.019\",\"ZENITH_NIGHT_COLOR_R\":\"0.014\",\"ZENITH_SUNSET_COLOR_B\":\"0.36\",\"ZENITH_SUNSET_COLOR_G\":\"0.24\",\"ZENITH_SUNSET_COLOR_R\":\"0.14\",\"sunPathRotation\":\"-25.0\"}",new TypeToken<Map<String,String>>(){}.getType());
 private static Object certified;
 private static int exports;
 public static boolean exactApi(){return com.pp.shaderlod.experiment.makeup.mixin.MakeupAlphaMixinPlugin.exactApi();}
 private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 private static boolean pinned(ProgramSource source)throws Exception {
  if(!Boolean.getBoolean(PROPERTY)||!exactApi()||!"gbuffers_terrain".equals(source.getName())||Iris.isFallback())return false;
  var pack=Iris.getCurrentPack().orElse(null);if(pack==null||source.getParent().getPack()!=pack)return false;
  for(String id:List.of("distanthorizons","voxy","create","flywheel"))if(ModList.get().isLoaded(id))return false;
  if(pack!=certified){
   certified=null;
   var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var file=dir.resolve(Iris.getCurrentPackName()).normalize();
   if(!file.startsWith(dir)||Files.isSymbolicLink(file))return false;
   String archive=sha(Files.readAllBytes(file));
   if(!Set.of(ARCHIVE,GUARDED_ALU_ARCHIVE,ASAHI_ALU_ARCHIVE,ASAHI_LATE_KILL_ARCHIVE).contains(archive))return false;
   if(Set.of(ASAHI_ALU_ARCHIVE,ASAHI_LATE_KILL_ARCHIVE).contains(archive)) {
    // Private Asahi qualification only; older Mac source gates and public guards stay separate.
    if(!Boolean.getBoolean("shaderlod.dev.sourceOnlyAlphaAsahiR1")
      ||!"Linux".equals(System.getProperty("os.name"))||!"aarch64".equals(System.getProperty("os.arch"))
      ||!"Apple M1 (G13G B1)".equals(org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER))
      ||!"4.6 (Core Profile) Mesa 25.1.0-asahi20250221".equals(org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION))
      ||System.getProperty("shaderlod.dev.sourceOnlyNativeOpaqueR74")==null)
     throw new IllegalStateException("SOURCEONLY_ASAHI_TUPLE_REFUSED");
   }
   var opts=pack.getShaderPackOptions();var actual=new TreeMap<String,String>();
   opts.getOptionSet().getBooleanOptions().keySet().forEach(k->actual.put(k,Boolean.toString(opts.getOptionValues().getBooleanValueOrDefault(k))));
   opts.getOptionSet().getStringOptions().keySet().forEach(k->actual.put(k,opts.getOptionValues().getStringValueOrDefault(k)));
   if(!NATIVE.equals(actual))return false;
   certified=pack;
  }
  return true;
 }
 public static Map<PatchShaderType,String> adapt(ProgramSource source,AlphaTest alpha,Map<PatchShaderType,String> stages) {
  try {
   if(!pinned(source))return stages;
   String fragment=stages.get(PatchShaderType.FRAGMENT);
   if(fragment==null||!fragment.contains("shaderlodSourceOnlyFog"))return stages;
   if(alpha.function()!=AlphaTestFunction.ALWAYS && (alpha.function()!=AlphaTestFunction.GREATER||!Float.isFinite(alpha.reference())||alpha.reference()<0||alpha.reference()>1))throw new IllegalStateException("SOURCEONLY_NATIVE_ALPHA_UNSUPPORTED");
   String opaque=System.getProperty("shaderlod.dev.sourceOnlyNativeOpaqueR74");
   String adapted;
   if(alpha.function()==AlphaTestFunction.ALWAYS&&opaque!=null){
    // Private cost control: restore the COMPLETE actual native SOLID fragment,
    // rather than paying source reordering/ALU changes on an alpha-always pass.
    // The paired vertex and all native100 options are already pinned above.
    byte[] bytes=Files.readAllBytes(Path.of(opaque));
    if(!sha(bytes).equals("84c0033ff28de958c6c47ec2e6f170d9eb5693f27b88e2acb5ab1c270d400903"))throw new IllegalStateException("Native opaque stage pin mismatch");
    adapted=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
   }else adapted=com.pp.shaderlod.experiment.makeup.MakeupAlphaInterface.adapt(fragment,alpha);
   if(adapted==null)throw new IllegalStateException("SOURCEONLY_NATIVE_LATE_ALPHA_JOIN_MISSING");
   var copy=new EnumMap<PatchShaderType,String>(PatchShaderType.class);copy.putAll(stages);copy.put(PatchShaderType.FRAGMENT,adapted);
   String export=System.getProperty("shaderlod.dev.sourceOnlyAlphaExport");
   if(export!=null&&exports<12){
    Path dir=Path.of(export).toAbsolutePath();Files.createDirectories(dir);int serial=exports++;
    Files.writeString(dir.resolve(serial+"-"+alpha.function()+".fsh"),adapted);
    Files.writeString(dir.resolve(serial+"-"+alpha.function()+".json"),new Gson().toJson(Map.of("alpha_function",alpha.function().toString(),"alpha_reference",alpha.reference(),"archive",sha(Files.readAllBytes(Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName()))),"fragment_sha256",sha(adapted.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"stage","actual SodiumPrograms.transformShaders return after Iris native alpha injection","native_late_retained",true,"source_only",true)));
   }
   return copy;
  }catch(Exception failure){throw new IllegalStateException("Pinned source-only alpha refused",failure);}
 }
}
