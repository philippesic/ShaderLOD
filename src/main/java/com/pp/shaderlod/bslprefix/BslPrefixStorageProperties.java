package com.pp.shaderlod.bslprefix;

import com.google.gson.*;
import org.lwjgl.opengl.*;

/** Private finite storage observation. No image reads, allocations or admission. */
public final class BslPrefixStorageProperties {
 public static boolean requested(){return "1".equals(System.getProperty("shaderlod.dev.g09StoragePropertiesOnly"));}
 public static void requireOptIns(){
  if(!"1".equals(System.getProperty("shaderlod.dev.g09PrefixAdmissionOnly")))throw new IllegalStateException("admission-only opt-in required");
  for(String key:System.getProperties().stringPropertyNames())if(key.contains("cameraQuality")||key.contains("pixelHistoryCapture")||key.contains("physicalResolution"))throw new IllegalStateException("competing property present: "+key);
  for(String key:new String[]{"gpuTiming","cpuTiming","gpuAttribution","gpuPassTiming","cpuLayerTiming"})if(!"false".equals(System.getProperty("shaderlod.dev."+key)))throw new IllegalStateException("profiler must be explicitly false: "+key);
 }
 public static JsonObject observe(int program,int normal,JsonObject receipt){
  int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);Throwable primary=null;
  try{
   JsonObject first=snapshot(program,normal);receipt.add("storage_first",first);
   BslPrefixGlErrorReceipt.checkpoint(receipt,"property-first",GL11::glGetError);
   JsonObject second=snapshot(program,normal);receipt.add("storage_requery",second);
   BslPrefixGlErrorReceipt.checkpoint(receipt,"property-requery",GL11::glGetError);
   if(!first.equals(second))throw new IllegalStateException("storage/sampler/program snapshot changed");
   receipt.addProperty("storage_edge",matches(first)?"PASS_STORAGE_ONLY":"REJECT_PROPOSED_STORAGE_TUPLE");
   return first;
  }catch(RuntimeException|Error failure){primary=failure;throw failure;}
  finally{
   GL13.glActiveTexture(active);
   try{receipt.addProperty("active_texture_restored",GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==active);
    if(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=active)throw new IllegalStateException("active texture restore failed");
    BslPrefixGlErrorReceipt.checkpoint(receipt,"property-state-restored",GL11::glGetError);
   }catch(RuntimeException|Error failure){if(primary!=null)primary.addSuppressed(failure);else throw failure;}
  }
 }
 private static JsonObject snapshot(int program,int normal){
  if(program<=0||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program||GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=1)throw new IllegalStateException("consuming program changed");
  int location=GL20.glGetUniformLocation(program,"normals");if(location<0)throw new IllegalStateException("normals absent");
  int unit=GL20.glGetUniformi(program,location);if(unit<0||unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))throw new IllegalStateException("normal unit invalid");
  GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
  int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
  if(texture<=0||texture!=normal)throw new IllegalStateException("consuming normal mismatch");
  var row=new JsonObject();row.addProperty("program",program);row.addProperty("unit",unit);row.addProperty("texture",texture);row.addProperty("sampler",sampler);
  int atlas=GL20.glGetUniformLocation(program,"atlasSize");if(atlas<0)throw new IllegalStateException("native atlasSize missing");int[] extent=new int[2];GL20.glGetUniformiv(program,atlas,extent);row.add("native_atlasSize",new Gson().toJsonTree(extent));
  String[] names={"width","height","internalFormat","redSize","greenSize","blueSize","alphaSize","redType","greenType","blueType","alphaType","compressed"};
  int[] queries={GL11.GL_TEXTURE_WIDTH,GL11.GL_TEXTURE_HEIGHT,GL11.GL_TEXTURE_INTERNAL_FORMAT,GL11.GL_TEXTURE_RED_SIZE,GL11.GL_TEXTURE_GREEN_SIZE,GL11.GL_TEXTURE_BLUE_SIZE,GL11.GL_TEXTURE_ALPHA_SIZE,GL30.GL_TEXTURE_RED_TYPE,GL30.GL_TEXTURE_GREEN_TYPE,GL30.GL_TEXTURE_BLUE_TYPE,GL30.GL_TEXTURE_ALPHA_TYPE,GL13.GL_TEXTURE_COMPRESSED};
  var levels=new JsonArray();for(int level=0;level<=4;level++){var value=new JsonObject();value.addProperty("level",level);for(int n=0;n<queries.length;n++)value.addProperty(names[n],GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,level,queries[n]));levels.add(value);}row.add("levels",levels);
  if(extent[0]!=levels.get(0).getAsJsonObject().get("width").getAsInt()||extent[1]!=levels.get(0).getAsJsonObject().get("height").getAsInt())throw new IllegalStateException("native atlas extent differs");
  row.addProperty("swizzleA",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL33.GL_TEXTURE_SWIZZLE_A));row.addProperty("base",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL));row.addProperty("max",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL));
  int[] params={GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER,GL14.GL_TEXTURE_COMPARE_MODE,GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T};String[] labels={"minFilter","magFilter","compareMode","wrapS","wrapT"};
  for(int n=0;n<params.length;n++)row.addProperty(labels[n],sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,params[n]):GL33.glGetSamplerParameteri(sampler,params[n]));
  var caps=GL.getCapabilities();row.addProperty("anisotropy",caps.GL_EXT_texture_filter_anisotropic||caps.GL_ARB_texture_filter_anisotropic?param(sampler,0x84FE):1f);
  row.addProperty("lodBias",param(sampler,GL14.GL_TEXTURE_LOD_BIAS));row.addProperty("minLod",param(sampler,GL12.GL_TEXTURE_MIN_LOD));row.addProperty("maxLod",param(sampler,GL12.GL_TEXTURE_MAX_LOD));return row;
 }
 private static float param(int sampler,int name){return sampler==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameterf(sampler,name);}
 public static boolean matches(JsonObject row){
  int ordinal=0;for(var entry:row.getAsJsonArray("levels")){var l=entry.getAsJsonObject();int n=l.get("level").getAsInt();if(n!=ordinal++)return false;if(l.get("width").getAsInt()!=(2048>>n)||l.get("height").getAsInt()!=(1024>>n)||l.get("internalFormat").getAsInt()!=GL11.GL_RGBA||l.get("compressed").getAsInt()!=0)return false;
   for(String channel:new String[]{"red","green","blue","alpha"})if(l.get(channel+"Size").getAsInt()!=8||l.get(channel+"Type").getAsInt()!=GL30.GL_UNSIGNED_NORMALIZED)return false;
  }
  if(row.getAsJsonArray("levels").size()!=5)return false;
  int[] values={0,4,GL11.GL_ALPHA,0,9986,9728,0,10497,10497};String[] keys={"base","max","swizzleA","sampler","minFilter","magFilter","compareMode","wrapS","wrapT"};
  for(int n=0;n<keys.length;n++)if(row.get(keys[n]).getAsInt()!=values[n])return false;
  return row.get("anisotropy").getAsFloat()==1&&row.get("lodBias").getAsFloat()==0&&row.get("minLod").getAsFloat()==0&&row.get("maxLod").getAsFloat()==4;
 }
 private BslPrefixStorageProperties(){}
}
