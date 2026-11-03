package com.pp.shaderlod.optimization.sildurspatial;
import java.util.*;import net.irisshaders.iris.Iris;import net.irisshaders.iris.platform.IrisPlatformHelpers;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.pipeline.WorldRenderingPipeline;import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;import org.lwjgl.opengl.*;
import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.optimization.sildurao.*;import com.pp.shaderlod.optimization.sildurao.mixin.SildurAoPassView;import com.pp.shaderlod.optimization.photon.*;import com.pp.shaderlod.optimization.blissspatial.BlissSpatialGl;
/** Actual native deferred producer-return/current-input consumer. Explicit composed AO/spatial graph. */
public final class SildurSpatialRuntime {
 private static final PhotonSpatialEpoch EPOCH=new PhotonSpatialEpoch();private static boolean installed,pending,recovering,retryBlocked,active;private static long blockedGeneration;private static Object pipeline,renderer,pack,controls;private static RenderTargets targets;private static SildurAoPassView producer,consumer;private static PhotonSpatialEpoch.Context context;private static SildurSpatialGl.Proof started,completed;private static Map<String,String> profile=Map.of();private static Map<String,Integer> uniforms=Map.of();private static int mode;private static int[] viewport;private static String last="not consumed",admission="not queried",sourceFailure="not checked",firstFailure="none";private static long firstFailureGeneration=Long.MIN_VALUE;
 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 private static long sourceGeneration,sourceLookups,sourceProofReplays;
 private static final SildurSpatialFallback FALLBACK=new SildurSpatialFallback();
 public static SildurSpatialFallback.Receipt takeFallback(){return FALLBACK.take();}
 private SildurSpatialRuntime(){}
 public static void install(){if(installed)return;installed=true;for(var id:List.of(OptimizationId.SPATIAL_LIGHTING_REUSE,OptimizationId.SPATIAL_LIGHTING_RATE))CompileOptionControl.configureAdmission(SildurSpatialContract.ID,id,SildurSpatialRuntime::setup);}
 public static String status(){return "Sildur current native-linear RGB spatial; mode="+mode+"; setup="+admission+"; pending="+pending+"; firstFailure="+firstFailure+"; last="+last+"; sourceLookups="+sourceLookups+" sourceProofReplays="+sourceProofReplays+"; cullCertificate="+com.pp.shaderlod.optimization.blissspatial.BlissSpatialCull.status();}
 private static Map<String,String> options(){var pack=Iris.getCurrentPack().orElse(null);if(pack==null)return Map.of();var o=pack.getShaderPackOptions();return SildurAoOptionGuard.snapshot(o.getOptionSet(),o.getOptionValues(),true);}
 /** Recovery reads only exact owned0/1 declarations, even when another native declaration/profile fails. */
 private static int loadedMode(){var pack=Iris.getCurrentPack().orElse(null);if(pack==null)return 0;var o=pack.getShaderPackOptions();var map=new TreeMap<String,String>();for(String key:List.of(SildurSpatialContract.REUSE,SildurSpatialContract.RATE)){var entry=o.getOptionSet().getStringOptions().get(key);if(entry==null||o.getOptionSet().getBooleanOptions().containsKey(key)||!entry.getOption().getDefaultValue().equals("0")||!Set.copyOf(entry.getOption().getAllowedValues()).equals(Set.of("0","1")))return 0;map.put(key,o.getOptionValues().getStringValueOrDefault(key));}return SildurSpatialContract.mode(map);}
 private static boolean environment(){var p=IrisPlatformHelpers.getInstance();return SildurAoApiPins.exact()&&!Iris.isFallback()&&CompileOptionControl.overworld(Iris.getCurrentDimension())&&!p.isModLoaded("distanthorizons")&&!p.isModLoaded("voxy");}
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(SildurSpatialRuntime.class,"source"),()->{
  sourceLookups++;
  try{
   var current=Iris.getCurrentPack().orElse(null);if(current==null){SOURCE_CACHE.invalidate();return false;}
   var shaderOptions=current.getShaderPackOptions();var actual=options();var client=net.minecraft.client.Minecraft.getInstance();
   boolean accepted=SOURCE_CACHE.check(SildurSpatialRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),
    SildurSpatialContract.ID,actual,current,shaderOptions.getOptionValues(),shaderOptions.getOptionSet(),
    Iris.getPipelineManager().getPipelineNullable(),client.level,sourceGeneration,
    Arrays.asList(OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue()),List.copyOf(client.getResourcePackRepository().getSelectedIds())),
    client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),
    ()->{sourceProofReplays++;return sourceUncached();});
   if(!accepted)SOURCE_CACHE.invalidate();return accepted;
  }catch(Exception|LinkageError refused){SOURCE_CACHE.invalidate();sourceFailure=refused.toString();return false;}
  catch(Error failure){SOURCE_CACHE.invalidate();throw failure;}
 
  });
 }
 private static boolean sourceUncached(){sourceFailure="packaged recipe";try(var in=SildurSpatialRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/sildurs-vibrant-shaders.json")){
  if(in==null||!SildurAoSourceProof.RECIPE_SHA.equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
  sourceFailure="selected generated archive/state/owned compile map";String n=Iris.getIrisConfig().getShaderPackName().orElse(null);if(n==null)return false;var d=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var p=d.resolve(n).normalize();return p.startsWith(d)&&!p.equals(d)&&SildurSpatialContract.ID.equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(p,options()));
 }catch(Exception|LinkageError refused){sourceFailure=refused.toString();return false;}}
 public static boolean samplerAllocation(net.irisshaders.iris.shaderpack.programs.ProgramSource source){return com.pp.shaderlod.optimization.blissspatial.BlissSpatialSamplerPins.exact()&&source!=null&&source.getParent()!=null&&source.getParent().getPack()==Iris.getCurrentPack().orElse(null)&&environment()&&SildurSpatialContract.mode(options())>0&&source.getName().equals(SildurSpatialContract.lighting(options()))&&SildurSpatialContract.options(options())&&source();}
 private static boolean refuseSetup(String why){SOURCE_CACHE.invalidate();admission="refused: "+why;return false;}
 public static boolean setup(){install();if(retryBlocked&&blockedGeneration==OptimizationConfig.generation())return refuseSetup("prior failure at unchanged configuration generation");if(!SildurAoApiPins.exact())return refuseSetup("API tuple");if(Iris.isFallback())return refuseSetup("Iris fallback");if(!CompileOptionControl.overworld(Iris.getCurrentDimension()))return refuseSetup("dimension "+Iris.getCurrentDimension());var platform=IrisPlatformHelpers.getInstance();if(platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy"))return refuseSetup("DH/VOXY installed");var current=Iris.getCurrentPack().orElse(null);if(current==null)return refuseSetup("no selected loaded pack");var o=current.getShaderPackOptions();if(!SildurAoOptionGuard.declaration(o.getOptionSet()))return refuseSetup("declaration count/type/default expected131, actual="+(o.getOptionSet().getBooleanOptions().size()+o.getOptionSet().getStringOptions().size()));if(!SildurSpatialContract.options(options()))return refuseSetup("complete native115/generated131 profile");if(!source())return refuseSetup("source "+sourceFailure);retryBlocked=false;admission="accepted exact native115/generated131 source; actual fields still required";return true;}
 private static boolean requested(int m){var c=OptimizationRuntimeContext.core();var id=m==1?OptimizationId.SPATIAL_LIGHTING_REUSE:OptimizationId.SPATIAL_LIGHTING_RATE;return m>0&&c!=null&&c.settings().enabled()&&c.preset()!=LodPreset.QUALITY&&c.optimizations().enabled(id)&&OptimizationConfig.requested(id);}
 public static void invalidate(){SOURCE_CACHE.invalidate();sourceGeneration++;if(!recovering)FALLBACK.clear();invalidateFrame();}
 private static void invalidateFrame(){EPOCH.revoke();pipeline=renderer=pack=controls=null;targets=null;producer=consumer=null;context=null;started=completed=null;profile=Map.of();uniforms=Map.of();mode=0;active=false;}
 public static void begin(Object p,RenderTargets t,Object r){invalidateFrame();install();pipeline=p;targets=t;renderer=r;pack=Iris.getCurrentPack().orElse(null);controls=OptimizationRuntimeContext.core();profile=Map.copyOf(options());mode=loadedMode();if(mode>0&&(!SildurSpatialContract.options(profile)||!requested(mode)||!environment()||!source()||(retryBlocked&&blockedGeneration==OptimizationConfig.generation())))lose("current source/native-profile/request unavailable");}
 public static void beginComposite(Object r,RenderTargets t,List<?> passes){if(r!=renderer||t!=targets||mode==0)return;active=true;try{for(Object row:passes){var v=(SildurAoPassView)row;if(v.shaderlod$name().equals("deferred"))producer=v;else if(v.shaderlod$name().equals(SildurSpatialContract.lighting(profile)))consumer=v;}
  int light=SildurSpatialContract.lightingIndex(profile);boolean half=SildurSpatialContract.half(profile);
  if(producer==null||consumer==null||(passes.size()!=light+1&&passes.size()!=light+2)||passes.get(0)!=producer||passes.get(light)!=consumer||!Arrays.equals(producer.shaderlod$drawBuffers(),new int[]{12})||!Arrays.equals(consumer.shaderlod$drawBuffers(),new int[]{4,6})||producer.shaderlod$scale().scale()!=1f||consumer.shaderlod$scale().scale()!=1f||producer.shaderlod$scale().viewportX()!=0||producer.shaderlod$scale().viewportY()!=0||consumer.shaderlod$scale().viewportX()!=0||consumer.shaderlod$scale().viewportY()!=0||!producer.shaderlod$mips().isEmpty()||!consumer.shaderlod$mips().isEmpty())lose("actual program metadata roles differ");
  if(half){var ao=(SildurAoPassView)passes.get(1);if(!ao.shaderlod$name().equals("deferred1")||!Arrays.equals(ao.shaderlod$drawBuffers(),new int[]{10})||ao.shaderlod$scale().scale()!=.5f||!ao.shaderlod$mips().isEmpty())lose("intervening AO-only pass differs");}
  if(passes.size()==light+2){var reflection=(SildurAoPassView)passes.get(light+1);if(!reflection.shaderlod$name().equals(SildurSpatialContract.reflection(profile))||!Arrays.equals(reflection.shaderlod$drawBuffers(),new int[]{4})||reflection.shaderlod$scale().scale()!=1f||!reflection.shaderlod$mips().isEmpty())lose("native reflection adjacency/layout differs");}

 }catch(RuntimeException|LinkageError refused){lose("actual graph unavailable");}}
 public static void endComposite(){active=false;}
 private static boolean current(){return active&&!pending&&!(retryBlocked&&blockedGeneration==OptimizationConfig.generation())&&targets!=null&&pack==Iris.getCurrentPack().orElse(null)&&Objects.equals(controls,OptimizationRuntimeContext.core())&&profile.equals(options())&&requested(mode)&&environment()&&producer!=null&&consumer!=null&&(context==null||context.generation()==OptimizationConfig.generation()&&context.width()==targets.getCurrentWidth()&&context.height()==targets.getCurrentHeight());}
 private static PhotonSpatialEpoch.Context context(int frame){return new PhotonSpatialEpoch.Context(pipeline,pack,targets,OptimizationConfig.generation(),frame,targets.getCurrentWidth(),targets.getCurrentHeight(),producer.shaderlod$program().getProgramId(),consumer.shaderlod$program().getProgramId(),profile);}
 public static void beforeDraw(){if(!active)return;int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p<=0||GL20.glGetUniformLocation(p,"shaderlodSildurSpatialReady")<0)return;SildurSpatialGl.upload(p,false,0,0);started=null;
  try{if(!current()){lose("current inputs/request changed; native fallback");return;}
   if(p==producer.shaderlod$program().getProgramId()){
    if(mode==2){int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);if(!Arrays.equals(v,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()})){lose("native full viewport unavailable");return;}viewport=v;GL11.glViewport(0,0,targets.getCurrentWidth()/2,targets.getCurrentHeight()/2);}
    com.pp.shaderlod.optimization.sildurreceiver.SildurReceiverRuntime.beforeDraw();started=SildurSpatialGl.producer(targets,p,mode==2);int frame=SildurSpatialGl.integer(p,"frameCounter");if(!started.valid()||frame<0||frame>=16777216){String why="producer-before: "+started.reason()+" frame="+frame;started=null;lose(why);return;}
    context=context(frame);EPOCH.begin(context);uniforms=SildurSpatialGl.controls(p);SildurSpatialGl.upload(p,true,context.width(),context.height());last="actual producer admitted";
   }else if(p==consumer.shaderlod$program().getProgramId()){
    var proof=SildurSpatialGl.consumer(targets,p);int frame=SildurSpatialGl.integer(p,"frameCounter");boolean inputsSame=completed!=null&&context!=null&&proof.valid()&&BlissSpatialGl.sameInputs(completed.inputs(),proof.inputs());boolean controlsSame=inputsSame&&SildurSpatialGl.sameControls(uniforms,SildurSpatialGl.controls(p));boolean ready=controlsSame&&EPOCH.consumer(context(frame),proof.output().cache(),proof.output().sky());
    if(ready){SildurSpatialGl.upload(p,true,context.width(),context.height());last="completed current native-linear field consumed";}else lose("consumer-before: completed="+(completed!=null)+" context="+(context!=null)+" proof="+proof.valid()+" inputsSame="+inputsSame+" controlsSame="+controlsSame+" reason="+proof.reason()+"; full native lighting retained");
   }
  }catch(RuntimeException|LinkageError refused){lose("actual GL/API proof refused");}}
 public static void afterDraw(){if(started==null||context==null)return;try{var after=SildurSpatialGl.producer(targets,context.producer(),mode==2);boolean valid=current()&&after.valid()&&Objects.equals(started.output(),after.output())&&Objects.equals(started.inputs(),after.inputs())&&uniforms.equals(SildurSpatialGl.controls(context.producer()));EPOCH.returned(context(SildurSpatialGl.integer(context.producer(),"frameCounter")),after.output(),true,valid);if(valid)completed=after;else lose("producer changed before actual return");}catch(RuntimeException|LinkageError refused){lose("actual producer return refused");}finally{started=null;}}
 public static void abortDraw(){lose("actual draw did not return");}
 public static void restoreDraw(){if(viewport!=null){var v=viewport;GL11.glViewport(v[0],v[1],v[2],v[3]);viewport=null;int[] now=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,now);if(!Arrays.equals(v,now))throw new IllegalStateException("Sildur native viewport restoration failed");}}
 private static void lose(String why){SOURCE_CACHE.invalidate();if(firstFailure.equals("none")||firstFailureGeneration!=OptimizationConfig.generation()){firstFailure=why;firstFailureGeneration=OptimizationConfig.generation();}last=why;pending=true;retryBlocked=true;blockedGeneration=OptimizationConfig.generation();EPOCH.revoke();context=null;started=completed=null;}
 public static WorldRenderingPipeline beforePrepare(NamespacedId dimension){install();int loaded=loadedMode();
  if(loaded>0&&(!SildurSpatialContract.options(options())||!requested(loaded)||!environment())){
   if(firstFailure.equals("none")||firstFailureGeneration!=OptimizationConfig.generation()){firstFailure="spatial request/profile/environment no longer qualified before capture";firstFailureGeneration=OptimizationConfig.generation();}
   pending=true;
  }
  if(!pending||recovering)return null;if(loaded==0){pending=false;return null;}recovering=true;
  try{
   var oldPack=Iris.getCurrentPack().orElse(null);var oldPipeline=Iris.getPipelineManager().getPipelineNullable();
   var world=net.minecraft.client.Minecraft.getInstance().level;long generation=OptimizationConfig.generation();
   var oldSelected=Iris.getIrisConfig().getShaderPackName().orElse(null);var before=Map.copyOf(options());
   boolean emptyQueue=Iris.getShaderPackOptionQueue().isEmpty();
   String reason=firstFailureGeneration==generation&&!firstFailure.equals("none")?firstFailure:last;FALLBACK.clear();
   SildurSpatialRecovery.nativeReload(Iris.getShaderPackOptionQueue(),Iris::reload);
   if(loadedMode()!=0)throw new IllegalStateException("native spatial graph0 did not load");
   var restored=Iris.getPipelineManager().preparePipeline(dimension);
   if(restored==null||restored!=Iris.getPipelineManager().getPipelineNullable())throw new IllegalStateException("native spatial pipeline was not prepared for capture");
   pending=false;var after=Map.copyOf(options());
   if(generation==OptimizationConfig.generation()&&world==net.minecraft.client.Minecraft.getInstance().level&&
       SildurSpatialContract.options(before)&&SildurSpatialContract.options(after))
    FALLBACK.publish(new SildurSpatialFallback.Receipt(oldPack,Iris.getCurrentPack().orElse(null),oldPipeline,restored,
     world,dimension,generation,oldSelected,Iris.getIrisConfig().getShaderPackName().orElse(null),before,after,
     emptyQueue&&Iris.getShaderPackOptionQueue().isEmpty(),reason));
   return restored;
  }catch(Exception failure){throw new IllegalStateException("Sildur native graph restoration before pipeline capture failed",failure);}
  finally{recovering=false;}}



 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}}
}
