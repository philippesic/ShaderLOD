package com.pp.shaderlod.fabric.dev;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.LodPreset;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.ShaderRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.util.*;
import java.security.MessageDigest;
/** Sparse original-only readback immediately BEFORE actual native GL draw, no renderer/source mutation. */
public final class PortBslDrawSourceReadback {
 private static final boolean REQUESTED=Boolean.getBoolean("shaderlod.dev.portBslOriginalSource");
 private static final String VERTEX="8465edbcde69327665725796434ee770e509e7d02107c17dfc9f33e7ec890785";
 private static final Map<String,String> FRAGMENTS=Map.of("SOLID","41115a395442482fce5772c63e9046f48bd45e4c1fcc8acd078207c87877b0c4","CUTOUT","97688f383cb7f8bbd7c4cac54572a2ce1a22955d6a4886ec922bb1b0dbb6d0c3");
 private static Object pipeline,pack,world;
 private static long generation;
 private static String epoch;
 private static JsonObject observations=new JsonObject();
 private static boolean failed;
 private PortBslDrawSourceReadback(){}
 public static void beforeActualGl(int commandCount){
  if(!REQUESTED||commandCount<=0||ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;
  var c=Minecraft.getInstance();Object current=Iris.getPipelineManager().getPipelineNullable(),currentPack=Iris.getCurrentPack().orElse(null);
  if(current!=pipeline||currentPack!=pack||c.level!=world||generation!=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation()){
   pipeline=current;pack=currentPack;world=c.level;generation=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();epoch=UUID.randomUUID().toString();observations=new JsonObject();failed=false;
  }
  if(failed||observations.has("SOLID")&&observations.has("CUTOUT"))return;
  JsonObject record=new JsonObject();int priorProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),priorUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),priorFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);int before=-1;String role=null;boolean captureStarted=false;
  try(var stack=MemoryStack.stackPush()){
   if(current==null||current.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class||currentPack==null||c.level==null)throw new IllegalStateException("Current native pipeline/pack/world unavailable");
   var map=((ShaderRenderingPipeline)current).getShaderMap();int solid=map.getShader(ShaderKey.SODIUM_TERRAIN_SOLID).getProgramId(),cutout=map.getShader(ShaderKey.SODIUM_TERRAIN_CUTOUT).getProgramId();
   if(priorProgram!=solid&&priorProgram!=cutout)return;
   if(solid==cutout||priorProgram<=0)throw new IllegalStateException("Ambiguous/invalid native terrain role program");
   role=priorProgram==solid?"SOLID":"CUTOUT";if(observations.has(role)){role=null;return;}captureStarted=true;before=GL11.glGetError();
   var cfg=FabricLodConfig.snapshot();
   if(!PortBslOriginalSourceChecks.propertiesAllow(System.getProperties())||!FabricLodConfig.loaded()||cfg.enabled()||cfg.debug()||cfg.preset()!=LodPreset.PERFORMANCE||!Iris.getIrisConfig().areDebugOptionsEnabled()||!Iris.getShaderPackOptionQueue().isEmpty()||Iris.isFallback())throw new IllegalStateException("Original-source flags/nativeOff/debugexports/currentqueue mismatch");
   var opts=currentPack==null?null:Iris.getCurrentPack().orElseThrow().getShaderPackOptions();var values=opts.getOptionValues();var actualOptions=new TreeMap<String,String>();opts.getOptionSet().getBooleanOptions().keySet().forEach(k->actualOptions.put(k,Boolean.toString(values.getBooleanValueOrDefault(k))));opts.getOptionSet().getStringOptions().keySet().forEach(k->actualOptions.put(k,values.getStringValueOrDefault(k)));
   if(!PortBslOriginalSourceChecks.sourceMatches(Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName()),actualOptions))throw new IllegalStateException("Actual draw native384/original BSL source certificate refused");
   com.pp.shaderlod.integration.RendererBackend.requireApplySupported();
   if((GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS)&8)!=0||before!=GL11.GL_NO_ERROR)throw new IllegalStateException("No-error/priorerror context cannot qualify source");
   record.addProperty("role",role);record.addProperty("programId",priorProgram);record.addProperty("actualGlFunctionBoundary","nglMultiDrawElementsBaseVertex BEFORE invocation after native setup");record.addProperty("nativeCommandCount",commandCount);record.addProperty("observedAtUtc",java.time.Instant.now().toString());record.addProperty("epoch",epoch);record.addProperty("pipelineIdentity",System.identityHashCode(pipeline));record.addProperty("packIdentity",System.identityHashCode(pack));record.addProperty("worldIdentity",System.identityHashCode(world));record.addProperty("lifecycleGeneration",generation);
   int count=GL20.glGetProgrami(priorProgram,GL20.GL_ATTACHED_SHADERS);if(count!=2)throw new IllegalStateException("Exactly two attached VSH/FSH required, count="+count);
   var ids=stack.mallocInt(count);var n=stack.mallocInt(1);GL20.glGetAttachedShaders(priorProgram,n,ids);if(n.get(0)!=2)throw new IllegalStateException("Attached shader count changed");
   var shaders=new JsonArray();Set<Integer> types=new HashSet<>();
   for(int i=0;i<2;i++){int id=ids.get(i),type=GL20.glGetShaderi(id,GL20.GL_SHADER_TYPE);if(!types.add(type)||type!=GL20.GL_VERTEX_SHADER&&type!=GL20.GL_FRAGMENT_SHADER)throw new IllegalStateException("Unknown/duplicate attached shader stage");
    String source=GL20.glGetShaderSource(id);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));String expected=type==GL20.GL_VERTEX_SHADER?VERTEX:FRAGMENTS.get(role);
    var shader=new JsonObject();shader.addProperty("shaderId",id);shader.addProperty("stage",type==GL20.GL_VERTEX_SHADER?"vertex":"fragment");shader.addProperty("actualSha256",hash);shader.addProperty("expectedCompilerSha256",expected);shader.addProperty("source",source);shader.addProperty("matches",hash.equals(expected));shaders.add(shader);
   }record.add("attachedShaders",shaders);
   for(var s:shaders)if(!s.getAsJsonObject().get("matches").getAsBoolean())throw new IllegalStateException("Attached source differs from actual062/063 compiler export; no normalization admitted");
   record.addProperty("sourceMatched",true);
  }catch(Exception|LinkageError e){failed=true;record.addProperty("sourceMatched",false);record.addProperty("failure",e.toString());}
  finally{if(captureStarted||failed){int after=GL11.glGetError();boolean unchanged=priorProgram==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)&&priorUnit==GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)&&priorFbo==GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);record.addProperty("glErrorBefore",before);record.addProperty("glErrorAfter",after);record.addProperty("programUnitFramebufferUnchanged",unchanged);if(after!=0||!unchanged){failed=true;record.addProperty("sourceMatched",false);}if(role!=null)observations.add(role,record);else if(failed)observations.add("failure",record);}}
 }
 public static JsonObject requireCurrentJoined(){
  var c=Minecraft.getInstance();if(failed||pipeline!=Iris.getPipelineManager().getPipelineNullable()||pack!=Iris.getCurrentPack().orElse(null)||world!=c.level||generation!=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation()||!observations.has("SOLID")||!observations.has("CUTOUT"))throw new IllegalStateException("Actual current native SOLID/CUTOUT draw-source readback missing/failed/stale");
  for(String role:FRAGMENTS.keySet())if(!observations.getAsJsonObject(role).get("sourceMatched").getAsBoolean())throw new IllegalStateException("Actual native attached-source join refused");
  return observations.deepCopy();
 }
 public static JsonObject observations(){return observations.deepCopy();}
}
