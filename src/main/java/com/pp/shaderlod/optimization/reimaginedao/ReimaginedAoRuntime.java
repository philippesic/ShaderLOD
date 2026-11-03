package com.pp.shaderlod.optimization.reimaginedao;
import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.optimization.reimaginedao.mixin.ReimaginedAoPassView;import com.pp.shaderlod.optimization.reimaginedao.mixin.ReimaginedAoTargetsView;
import java.nio.file.*;import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
import net.irisshaders.iris.Iris;import net.irisshaders.iris.platform.IrisPlatformHelpers;import net.irisshaders.iris.pipeline.*;import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;
/** Real source/setup, allocated resources, original draw return, same-frame latest-side and native recovery. */
public final class ReimaginedAoRuntime {
 private static final ReimaginedAoEpoch EPOCH=new ReimaginedAoEpoch();private static boolean installed,recovering,pending,retryBlocked;
 private static final AoNativeFallback FALLBACK=new AoNativeFallback();
 private static final ReimaginedAoFrameProof FRAME=new ReimaginedAoFrameProof();private static long frameSerial;
 private static long sourceGeneration,blockedGeneration;private static Object pipeline,renderer,pack;private static RenderTargets targets;
 private static long frameBegins,qualifiedComposites,producerReadyDraws,producerReturns,consumerReadyDraws;
 private static com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.Pair compiledPair;
 private static boolean activeComposite;private static ReimaginedAoPassView producer,consumer;private static ReimaginedAoEpoch.Context context;private static ReimaginedAoGlProof.Draw started;private static String failure="not yet rendered",admission="not queried";
 private ReimaginedAoRuntime(){}
 public static void install(){if(installed)return;installed=true;CompileOptionControl.configureAdmission(ReimaginedAoContract.ID,OptimizationId.AO_RENDER_SCALE,ReimaginedAoRuntime::setupAdmitted);}
 public static AoNativeFallback.Receipt takeFallback(){return FALLBACK.take();}
 public static String lastFailure(){return failure;}
 public static String status(){return "sourceProofReplays="+sourceProofReplays+"; "+"installed="+installed+"; loadedHalf="+loadedHalf()+"; setup="+admission+"; recoveryPending="+pending+"; lastFailure="+failure+"; cumulative observed callbacks: frames="+frameBegins+", qualifiedComposites="+qualifiedComposites+", producerReady="+producerReadyDraws+", producerReturned="+producerReturns+", consumerReady="+consumerReadyDraws;}
 private static boolean refuseSetup(String why){SOURCE_CACHE.invalidate();admission="refused: "+why;return false;}
 private static Path selected(){String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return null;Path d=Iris.getShaderpacksDirectory().toAbsolutePath().normalize(),p=d.resolve(name).normalize();return p.startsWith(d)&&!p.equals(d)?p:null;}
 private static String loadedHalf(){var current=Iris.getCurrentPack().orElse(null);if(current==null)return null;var o=current.getShaderPackOptions();return ReimaginedAoOptionGuard.declaredHalf(o.getOptionSet(),o.getOptionValues()).orElse(null);}
 private static Map<String,String> options(boolean verifiedSource){var current=Iris.getCurrentPack().orElse(null);if(current==null)return Map.of();var o=current.getShaderPackOptions();return ReimaginedAoOptionGuard.snapshot(o.getOptionSet(),o.getOptionValues(),verifiedSource);}
 private static long sourceProofReplays,sourceCalls;
 public static Map<String,Long> costCounters(){return Map.of("source_calls",sourceCalls,"qualified_composites",qualifiedComposites,"producer_ready",producerReadyDraws,"producer_returns",producerReturns,"consumer_ready",consumerReadyDraws);}
 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 /** Loading owns the full source proof; rendering consumes its pipeline-bound receipt. */
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(ReimaginedAoRuntime.class,"source"),()->{sourceCalls++;
  try{
   if(Iris.isFallback()||!ReimaginedAoApiPins.exact()){SOURCE_CACHE.invalidate();return false;}
   var currentPack=Iris.getCurrentPack().orElse(null);var path=selected();
   if(currentPack==null||path==null){SOURCE_CACHE.invalidate();return false;}
   var shaderOptions=currentPack.getShaderPackOptions();var actual=options(true);
   var client=net.minecraft.client.Minecraft.getInstance();
   boolean accepted=SOURCE_CACHE.check(ReimaginedAoRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),
    "complementary-reimagined",actual,currentPack,shaderOptions.getOptionValues(),shaderOptions.getOptionSet(),
    Iris.getPipelineManager().getPipelineNullable(),client.level,sourceGeneration,
    Arrays.asList(OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue()),List.copyOf(client.getResourcePackRepository().getSelectedIds())),
    client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),
    ()->{sourceProofReplays++;return ReimaginedAoSourceProof.verify(path,()->ReimaginedAoRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/complementary-reimagined.json"));});
   if(!accepted)SOURCE_CACHE.invalidate();return accepted;
  }catch(Exception|LinkageError unavailable){SOURCE_CACHE.invalidate();return false;}
  catch(Error failure){SOURCE_CACHE.invalidate();throw failure;}
 
  });
 }
 public static final String SOURCE_RECIPE_SHA=ReimaginedAoSourceProof.RECIPE_SHA;
 private static boolean requested(){var core=OptimizationRuntimeContext.core();return core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&core.optimizations().enabled(OptimizationId.AO_RENDER_SCALE)&&OptimizationConfig.requested(OptimizationId.AO_RENDER_SCALE);}
 private static boolean environment(){var p=IrisPlatformHelpers.getInstance();return !Iris.isFallback()&&ReimaginedAoApiPins.exact()&&!p.isModLoaded("distanthorizons")&&!p.isModLoaded("voxy");}
 private static ReimaginedAoFrameProof.Key frameKey(){var p=selected();return targets==null?null:new ReimaginedAoFrameProof.Key(Iris.getCurrentPack().orElse(null),pipeline,targets,frameSerial,sourceGeneration,OptimizationConfig.generation(),targets.getCurrentWidth(),targets.getCurrentHeight(),p==null?null:p.toString(),OptimizationRuntimeContext.core());}
 public static boolean setupAdmitted(){install();if(retryBlocked&&blockedGeneration==OptimizationConfig.generation())return refuseSetup("prior resource/source failure at current flag generation");if(retryBlocked){retryBlocked=false;EPOCH.requalified();}
  if(loadedHalf()==null)return refuseSetup("exact Reimagined408 declarations unavailable");if(!environment())return refuseSetup("API/fallback/DH/VOXY environment");if(!source())return refuseSetup("source/state/recipe proof");if(!ReimaginedAoContract.options(options(true)))return refuseSetup("complete native408 live options");admission="accepted source/profile/setup; per-frame target proof still required";return true;}
 public static long sourceGeneration(){return sourceGeneration;}
 public static void destroy(Object destroyed){com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.destroy(destroyed);SOURCE_CACHE.invalidate();if(pipeline==destroyed){compiledPair=null;FRAME.reset();EPOCH.invalidate();activeComposite=false;pipeline=renderer=pack=null;targets=null;producer=consumer=null;context=null;started=null;}}
 public static void reloadHead(){com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.clear(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.Family.REIMAGINED);compiledPair=null;SOURCE_CACHE.invalidate();if(!recovering)FALLBACK.clear();sourceGeneration++;FRAME.reset();EPOCH.invalidate();pipeline=renderer=pack=null;targets=null;producer=consumer=null;context=null;started=null;}
 public static void begin(Object p,RenderTargets t,Object deferred){install();frameBegins++;frameSerial++;FRAME.reset();compiledPair=null;activeComposite=false;pipeline=p;targets=t;renderer=deferred;pack=Iris.getCurrentPack().orElse(null);context=null;started=null;EPOCH.invalidate();}
 public static void endComposite(){compiledPair=null;activeComposite=false;context=null;started=null;}
 public static void beginComposite(Object r,RenderTargets t,List<?> passes){if(r!=renderer||t!=targets||!"1".equals(loadedHalf()))return;activeComposite=true;producer=consumer=null;
  if(!requested()){disabled("AO request disabled; native fallback");return;}
  if((retryBlocked&&blockedGeneration==OptimizationConfig.generation())||!environment()){lose("AO active frame source/profile not qualified");return;}
  if(retryBlocked){retryBlocked=false;EPOCH.requalified();}
  try{for(Object pass:passes){var v=(ReimaginedAoPassView)pass;if(v.shaderlod$name().equals("deferred"))producer=v;else if(v.shaderlod$name().equals("deferred1"))consumer=v;else{lose("unexpected deferred graph stage");return;}}
   if(producer==null||consumer==null){lose("AO required programs absent");return;}
   var proof=com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.lookup(com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.Family.REIMAGINED,producer.shaderlod$program(),consumer.shaderlod$program(),r,pipeline,t);
   if(proof.state()==com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.State.STALE){lose("AO compiled ownership/input currency refused");return;}
   compiledPair=proof.pair();
   if(producer==null||consumer==null||passes.size()!=2||!Arrays.equals(producer.shaderlod$drawBuffers(),new int[]{10})||!Arrays.equals(consumer.shaderlod$drawBuffers(),new int[]{0,5,4})||!producer.shaderlod$mips().isEmpty()||!consumer.shaderlod$mips().equals(ReimaginedAoContract.consumerMips(compiledPair==null?options(true):compiledPair.currentOptions()))||producer.shaderlod$scale().scale()!=.5f||consumer.shaderlod$scale().scale()!=1f||producer.shaderlod$scale().viewportX()!=0||producer.shaderlod$scale().viewportY()!=0||consumer.shaderlod$scale().viewportX()!=0||consumer.shaderlod$scale().viewportY()!=0){lose("AO source graph layout differs");return;}
   var settings=((ReimaginedAoTargetsView)targets).shaderlod$settings().get(10);
   if(settings==null||!settings.getInternalFormat().name().equals("RGBA32F")||!settings.shouldClear()||settings.getClearColor().isEmpty()||!settings.getClearColor().get().equals(1,0,0,-1)){lose("AO target clear/format directive differs");return;}
   if(!FRAME.qualify(frameKey(),true,true,()->compiledPair!=null?compiledPair.currentOptions():source()?options(true):Map.of())){lose("AO active frame source/profile not qualified");return;}
   context=new ReimaginedAoEpoch.Context(pipeline,pack,targets,sourceGeneration,OptimizationConfig.generation(),targets.getCurrentWidth(),targets.getCurrentHeight(),producer.shaderlod$program().getProgramId(),consumer.shaderlod$program().getProgramId());EPOCH.begin(context);qualifiedComposites++;
  }catch(RuntimeException|LinkageError unavailable){lose("AO setup API unavailable");}}
 private static boolean current(){return context!=null&&pipeline!=null&&pack==Iris.getCurrentPack().orElse(null)&&context.flagGeneration()==OptimizationConfig.generation()&&context.sourceGeneration()==sourceGeneration&&requested()&&environment()&&FRAME.current(frameKey(),compiledPair==null?options(true):compiledPair.currentOptions());}
 public static void beforeDraw(){if(!activeComposite)return;int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p<=0||GL20.glGetUniformLocation(p,"shaderlodReimaginedAoTargetReady")<0)return;ReimaginedAoGlProof.upload(p,false,0,0);started=null;
  try{if(!current()){if(!requested())disabled("AO request disabled; native fallback");else lose("AO request/source/epoch no longer qualified");return;}
   if(p==context.producerProgram()){started=ReimaginedAoGlProof.producer(targets,p);boolean ready=EPOCH.start(context,started.output(),started.qualified());if(!ready){lose("AO producer framebuffer/depth/projection unavailable");return;}ReimaginedAoGlProof.upload(p,true,context.width(),context.height());producerReadyDraws++;}
   else if(p==context.consumerProgram()){var target=targets.get(10);int sampled=ReimaginedAoGlProof.integer(p,"colortex10");var binding=ReimaginedAoGlProof.binding(p,"colortex10");boolean proof=binding!=null&&ReimaginedAoGlProof.consumer(targets,p,binding.texture())&&EPOCH.consumer(context,binding.texture(),true);if(!proof){lose("AO consumer lacks current completed texture");return;}ReimaginedAoGlProof.upload(p,true,context.width(),context.height());consumerReadyDraws++;}
   else lose("unexpected AO-linked program");
  }catch(RuntimeException|LinkageError unavailable){lose("AO current GL/API proof failed");}}
 public static void afterDraw(){if(started==null||context==null)return;try{if(!current()){if(!requested())disabled("AO request disabled during producer");else lose("AO source/profile epoch changed during producer");return;}var actual=ReimaginedAoGlProof.producer(targets,context.producerProgram());if(!EPOCH.returned(context,actual.output(),true,actual.qualified()&&actual.output().equals(started.output())))lose("AO producer changed bindings before return");else producerReturns++;}catch(RuntimeException|LinkageError unavailable){lose("AO producer return proof unavailable");}finally{started=null;}}
 private static void disabled(String reason){SOURCE_CACHE.invalidate();failure=reason;pending=true;EPOCH.revoke();FRAME.reset();compiledPair=null;context=null;started=null;}
 private static void lose(String reason){SOURCE_CACHE.invalidate();failure=reason;pending=true;retryBlocked=true;blockedGeneration=OptimizationConfig.generation();EPOCH.revoke();FRAME.reset();compiledPair=null;context=null;started=null;}
 /** Called only by pinned PipelineManager HEAD before caller captures its returned pipeline. */
 public static WorldRenderingPipeline beforePrepare(NamespacedId dim){install();if(!pending||recovering)return null;if(loadedHalf()==null){pending=false;EPOCH.revoke();return null;}recovering=true;
  try{
   var oldPack=Iris.getCurrentPack().orElse(null);var oldPipeline=Iris.getPipelineManager().getPipelineNullable();var oldSelected=Iris.getIrisConfig().getShaderPackName().orElse(null);
   var oldOptions=options(true);long generation=OptimizationConfig.generation();var world=net.minecraft.client.Minecraft.getInstance().level;
   boolean emptyQueue=Iris.getShaderPackOptionQueue().isEmpty();String reason=failure;FALLBACK.clear();
   ReimaginedAoRecovery.nativeReload(Iris.getShaderPackOptionQueue(),Iris::reload);if(!"0".equals(loadedHalf()))throw new IllegalStateException("Reimagined native graph0 recovery did not load");pending=false;
   var restored=Iris.getPipelineManager().preparePipeline(dim);
   if(generation==OptimizationConfig.generation()&&world==net.minecraft.client.Minecraft.getInstance().level
       &&ReimaginedAoContract.options(oldOptions)&&ReimaginedAoContract.options(options(true)))
    FALLBACK.publish(new AoNativeFallback.Receipt(ReimaginedAoContract.ID,oldPack,Iris.getCurrentPack().orElse(null),oldPipeline,Iris.getPipelineManager().getPipelineNullable(),world,dim,generation,
       oldSelected,Iris.getIrisConfig().getShaderPackName().orElse(null),oldOptions,options(true),
       emptyQueue&&Iris.getShaderPackOptionQueue().isEmpty(),reason));
   return restored;
  }
  catch(Exception failure){throw new IllegalStateException("Could not restore Reimagined native AO graph before pipeline capture",failure);}
  finally{recovering=false;}}

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}}
}
