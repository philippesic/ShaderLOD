package com.pp.shaderlod.optimization.solasao;
import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.optimization.solasao.mixin.SolasAoPassView;import com.pp.shaderlod.optimization.solasao.mixin.SolasAoTargetsView;
import java.nio.file.*;import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
import net.irisshaders.iris.Iris;import net.irisshaders.iris.platform.IrisPlatformHelpers;import net.irisshaders.iris.pipeline.*;import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;
/** Real source/setup, allocated resources, original draw return, same-frame latest-side and native recovery. */
public final class SolasAoRuntime {
 private static final SolasAoEpoch EPOCH=new SolasAoEpoch();private static boolean installed,recovering,pending,retryBlocked;
 private static final SolasAoFrameProof FRAME=new SolasAoFrameProof();private static long frameSerial;
 private static final AoNativeFallback FALLBACK=new AoNativeFallback();
 private static long sourceGeneration,blockedGeneration;private static Object pipeline,renderer,pack;private static RenderTargets targets;
 private static long frameBegins,qualifiedComposites,producerReadyDraws,producerReturns,consumerReadyDraws;
 private static String currentFailure="not observed";private static boolean currentFailureCaptured;
 private static boolean activeComposite;private static SolasAoPassView producer,consumer;private static SolasAoEpoch.Context context;private static SolasAoGlProof.Draw started;private static String failure="not yet rendered",admission="not queried";
 private SolasAoRuntime(){}
 public static void install(){if(installed)return;installed=true;CompileOptionControl.configureAdmission(SolasAoContract.ID,OptimizationId.AO_RENDER_SCALE,SolasAoRuntime::setupAdmitted);}
 public static String lastFailure(){return failure;}
 /** Destructive read: an unrelated later reload or a second caller cannot reuse this refusal. */
 public static AoNativeFallback.Receipt takeFallback(){return FALLBACK.take();}
 public static String status(){return "sourceProofReplays="+sourceProofReplays+"; "+"installed="+installed+"; loadedHalf="+loadedHalf()+"; setup="+admission+"; recoveryPending="+pending+"; lastFailure="+failure+"; lastCurrentFailure="+currentFailure+"; cumulative observed callbacks: frames="+frameBegins+", qualifiedComposites="+qualifiedComposites+", producerReady="+producerReadyDraws+", producerReturned="+producerReturns+", consumerReady="+consumerReadyDraws;}
 private static boolean refuseSetup(String why){SOURCE_CACHE.invalidate();admission="refused: "+why;return false;}
 private static Path selected(){String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return null;Path d=Iris.getShaderpacksDirectory().toAbsolutePath().normalize(),p=d.resolve(name).normalize();return p.startsWith(d)&&!p.equals(d)?p:null;}
 private static String loadedHalf(){var current=Iris.getCurrentPack().orElse(null);if(current==null)return null;var o=current.getShaderPackOptions();return SolasAoOptionGuard.declaredHalf(o.getOptionSet(),o.getOptionValues()).orElse(null);}
 private static Map<String,String> options(boolean verifiedSource){var current=Iris.getCurrentPack().orElse(null);if(current==null)return Map.of();var o=current.getShaderPackOptions();return SolasAoOptionGuard.snapshot(o.getOptionSet(),o.getOptionValues(),verifiedSource);}
 private static long sourceProofReplays;
 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 /** Loading owns the full source proof; rendering consumes its pipeline-bound receipt. */
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(SolasAoRuntime.class,"source"),()->{
  try{
   if(Iris.isFallback()||!SolasAoApiPins.exact()){SOURCE_CACHE.invalidate();return false;}
   var currentPack=Iris.getCurrentPack().orElse(null);var path=selected();
   if(currentPack==null||path==null){SOURCE_CACHE.invalidate();return false;}
   var shaderOptions=currentPack.getShaderPackOptions();var actual=options(true);
   var client=net.minecraft.client.Minecraft.getInstance();
   boolean accepted=SOURCE_CACHE.check(SolasAoRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),
    "solas-shader",actual,currentPack,shaderOptions.getOptionValues(),shaderOptions.getOptionSet(),
    Iris.getPipelineManager().getPipelineNullable(),client.level,sourceGeneration,
    Arrays.asList(OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue()),List.copyOf(client.getResourcePackRepository().getSelectedIds())),
    client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),
    ()->{sourceProofReplays++;return SolasAoSourceProof.verify(path,()->SolasAoRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/solas-shader.json"));});
   if(!accepted)SOURCE_CACHE.invalidate();return accepted;
  }catch(Exception|LinkageError unavailable){SOURCE_CACHE.invalidate();return false;}
  catch(Error failure){SOURCE_CACHE.invalidate();throw failure;}
 
  });
 }
 public static final String SOURCE_RECIPE_SHA=SolasAoSourceProof.RECIPE_SHA;
 private static boolean requested(){var core=OptimizationRuntimeContext.core();return core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&core.optimizations().enabled(OptimizationId.AO_RENDER_SCALE)&&OptimizationConfig.requested(OptimizationId.AO_RENDER_SCALE);}
 private static boolean environment(){var p=IrisPlatformHelpers.getInstance();return !Iris.isFallback()&&SolasAoApiPins.exact()&&!p.isModLoaded("distanthorizons")&&!p.isModLoaded("voxy");}
 private static SolasAoFrameProof.Key frameKey(){var p=selected();return targets==null?null:new SolasAoFrameProof.Key(Iris.getCurrentPack().orElse(null),pipeline,targets,frameSerial,sourceGeneration,OptimizationConfig.generation(),targets.getCurrentWidth(),targets.getCurrentHeight(),p==null?null:p.toString(),OptimizationRuntimeContext.core());}
 public static boolean setupAdmitted(){install();if(retryBlocked&&blockedGeneration==OptimizationConfig.generation())return refuseSetup("prior resource/source failure at current flag generation");if(retryBlocked){retryBlocked=false;EPOCH.requalified();}
  if(loadedHalf()==null)return refuseSetup("exact Solas436 declarations unavailable");if(!environment())return refuseSetup("API/fallback/DH/VOXY environment");if(!source())return refuseSetup("source/state/recipe proof");if(!SolasAoContract.options(options(true)))return refuseSetup("complete native436 live options");admission="accepted source/profile/setup; per-frame target proof still required";return true;}
 public static void reloadHead(){SOURCE_CACHE.invalidate();if(!recovering)FALLBACK.clear();sourceGeneration++;FRAME.reset();EPOCH.invalidate();pipeline=renderer=pack=null;targets=null;producer=consumer=null;context=null;started=null;}
 public static void begin(Object p,RenderTargets t,Object deferred){install();frameBegins++;frameSerial++;FRAME.reset();activeComposite=false;pipeline=p;targets=t;renderer=deferred;pack=Iris.getCurrentPack().orElse(null);context=null;started=null;EPOCH.invalidate();}
 public static void endComposite(){activeComposite=false;context=null;started=null;}
 public static void beginComposite(Object r,RenderTargets t,List<?> passes){if(r!=renderer||t!=targets||!"1".equals(loadedHalf()))return;activeComposite=true;producer=consumer=null;
  if(!requested()){disabled("AO request disabled; native fallback");return;}
  if((retryBlocked&&blockedGeneration==OptimizationConfig.generation())||!environment()||!FRAME.qualify(frameKey(),true,true,()->source()?options(true):Map.of())){lose("AO active frame source/profile not qualified");return;}
  if(retryBlocked){retryBlocked=false;EPOCH.requalified();}
  try{for(Object pass:passes){var v=(SolasAoPassView)pass;if(v.shaderlod$name().equals("deferred"))producer=v;else if(v.shaderlod$name().equals("deferred1"))consumer=v;else if(v.shaderlod$name().equals("deferred2")){if(!Arrays.equals(v.shaderlod$drawBuffers(),new int[]{0,4,5})||v.shaderlod$scale().scale()!=1f||v.shaderlod$scale().viewportX()!=0||v.shaderlod$scale().viewportY()!=0||!v.shaderlod$mips().equals(java.util.Set.of(0,6))){lose("native nonAO stage differs");return;}}else{lose("unexpected deferred graph stage");return;}}
   if(producer==null||consumer==null||passes.size()!=3||!Arrays.equals(producer.shaderlod$drawBuffers(),new int[]{10,11})||!Arrays.equals(consumer.shaderlod$drawBuffers(),new int[]{5})||!producer.shaderlod$mips().isEmpty()||!consumer.shaderlod$mips().isEmpty()||producer.shaderlod$scale().scale()!=.5f||consumer.shaderlod$scale().scale()!=1f||producer.shaderlod$scale().viewportX()!=0||producer.shaderlod$scale().viewportY()!=0||consumer.shaderlod$scale().viewportX()!=0||consumer.shaderlod$scale().viewportY()!=0){lose("AO source graph layout differs");return;}
   var settings=((SolasAoTargetsView)targets).shaderlod$settings().get(10);
   if(settings==null||!settings.getInternalFormat().name().equals("RGBA32F")||!settings.shouldClear()||settings.getClearColor().isEmpty()||!settings.getClearColor().get().equals(1,0,0,-1)){lose("AO target clear/format directive differs");return;}
   var normals=((SolasAoTargetsView)targets).shaderlod$settings().get(11);
   if(normals==null||!normals.getInternalFormat().name().equals("RGBA32F")||!normals.shouldClear()||normals.getClearColor().isEmpty()||!normals.getClearColor().get().equals(0,0,0,0)){lose("AO normal target clear/format differs");return;}
   context=new SolasAoEpoch.Context(pipeline,pack,targets,sourceGeneration,OptimizationConfig.generation(),targets.getCurrentWidth(),targets.getCurrentHeight(),producer.shaderlod$program().getProgramId(),consumer.shaderlod$program().getProgramId());EPOCH.begin(context);currentFailureCaptured=false;qualifiedComposites++;
  }catch(RuntimeException|LinkageError unavailable){lose("AO setup API unavailable");}}
 private static boolean notCurrent(String reason){if(!currentFailureCaptured){currentFailure=reason;currentFailureCaptured=true;}return false;}
 private static boolean current(){
  if(context==null)return notCurrent("context missing");
  if(pipeline==null)return notCurrent("pipeline missing");
  if(pack!=Iris.getCurrentPack().orElse(null))return notCurrent("pack identity");
  if(context.flagGeneration()!=OptimizationConfig.generation())return notCurrent("flag generation");
  if(context.sourceGeneration()!=sourceGeneration)return notCurrent("source generation");
  if(!requested())return notCurrent("request disabled");
  if(!environment())return notCurrent("environment");
  String reason=FRAME.currentFailure(frameKey(),options(true));
  return reason==null||notCurrent("frame proof: "+reason);
 }
 public static void beforeDraw(){if(!activeComposite)return;int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p<=0||GL20.glGetUniformLocation(p,"shaderlodSolasAoTargetReady")<0)return;SolasAoGlProof.upload(p,false,0,0);started=null;
  try{if(!current()){if(!requested())disabled("AO request disabled; native fallback");else lose("AO request/source/epoch no longer qualified; "+currentFailure);return;}
   if(p==context.producerProgram()){started=SolasAoGlProof.producer(targets,p);boolean ready=EPOCH.start(context,started.output(),started.qualified());if(!ready){lose(started.qualified()?"AO producer epoch refused despite valid GL proof":"AO producer proof refused: "+SolasAoGlProof.diagnostic());return;}SolasAoGlProof.upload(p,true,context.width(),context.height());producerReadyDraws++;}
   else if(p==context.consumerProgram()){var target=targets.get(10);int sampled=SolasAoGlProof.integer(p,"colortex10");var binding=SolasAoGlProof.binding(p,"colortex10");boolean proof=binding!=null&&SolasAoGlProof.consumer(targets,p,binding.texture())&&EPOCH.consumer(context,binding.texture(),SolasAoGlProof.binding(p,"colortex11").texture(),SolasAoGlProof.binding(p,"depthtex0"),true);if(!proof){lose("AO consumer lacks current completed texture");return;}SolasAoGlProof.upload(p,true,context.width(),context.height());consumerReadyDraws++;}
   else lose("unexpected AO-linked program");
  }catch(RuntimeException|LinkageError unavailable){lose("AO current GL/API proof failed");}}
 public static void afterDraw(){if(started==null||context==null)return;try{if(!current()){if(!requested())disabled("AO request disabled during producer");else lose("AO source/profile epoch changed during producer; "+currentFailure);return;}var actual=SolasAoGlProof.producer(targets,context.producerProgram());if(!EPOCH.returned(context,actual.output(),true,actual.qualified()&&actual.output().equals(started.output())))lose(actual.qualified()?"AO producer changed bindings before return":"AO producer return proof refused: "+SolasAoGlProof.diagnostic());else producerReturns++;}catch(RuntimeException|LinkageError unavailable){lose("AO producer return proof unavailable");}finally{started=null;}}
 private static void disabled(String reason){SOURCE_CACHE.invalidate();if(!pending)failure=reason;pending=true;EPOCH.revoke();FRAME.reset();context=null;started=null;}
 private static void lose(String reason){SOURCE_CACHE.invalidate();if(!pending)failure=reason;pending=true;retryBlocked=true;blockedGeneration=OptimizationConfig.generation();EPOCH.revoke();FRAME.reset();context=null;started=null;}
 /** Called only by pinned PipelineManager HEAD before caller captures its returned pipeline. */
 public static WorldRenderingPipeline beforePrepare(NamespacedId dim){install();if(!recovering&&"1".equals(loadedHalf())&&!new NamespacedId("minecraft","overworld").equals(dim)){if(!pending)failure="dimension transition restores native graph0 before capture";pending=true;}if(!pending||recovering)return null;if(loadedHalf()==null){pending=false;EPOCH.revoke();return null;}recovering=true;
  try{
   var oldPack=Iris.getCurrentPack().orElse(null);var oldPipeline=Iris.getPipelineManager().getPipelineNullable();var oldSelected=Iris.getIrisConfig().getShaderPackName().orElse(null);
   var oldOptions=options(true);long generation=OptimizationConfig.generation();var world=net.minecraft.client.Minecraft.getInstance().level;
   boolean emptyQueue=Iris.getShaderPackOptionQueue().isEmpty();String reason=failure;FALLBACK.clear();
   SolasAoRecovery.nativeReload(Iris.getShaderPackOptionQueue(),Iris::reload);if(!"0".equals(loadedHalf()))throw new IllegalStateException("Solas native graph0 recovery did not load");
   var restored=Iris.getPipelineManager().preparePipeline(dim);pending=false;
   if(generation==OptimizationConfig.generation()&&world==net.minecraft.client.Minecraft.getInstance().level
       &&SolasAoContract.options(oldOptions)&&SolasAoContract.options(options(true)))
    FALLBACK.publish(new AoNativeFallback.Receipt(SolasAoContract.ID,oldPack,Iris.getCurrentPack().orElse(null),oldPipeline,Iris.getPipelineManager().getPipelineNullable(),world,dim,generation,
       oldSelected,Iris.getIrisConfig().getShaderPackName().orElse(null),oldOptions,options(true),
       emptyQueue&&Iris.getShaderPackOptionQueue().isEmpty(),reason));
   return restored;
  }
  catch(Exception failure){throw new IllegalStateException("Could not restore Solas native AO graph before pipeline capture",failure);}
  finally{recovering=false;}}

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}}
}
