package com.pp.shaderlod.optimization.sildurao;
import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs;
import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.optimization.sildurao.mixin.SildurAoPassView;import com.pp.shaderlod.optimization.sildurao.mixin.SildurAoTargetsView;
import java.nio.file.*;import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
import net.irisshaders.iris.Iris;import net.irisshaders.iris.platform.IrisPlatformHelpers;import net.irisshaders.iris.pipeline.*;import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;
/** Real source/setup, allocated resources, original draw return, same-frame latest-side and native recovery. */
public final class SildurAoRuntime {
 private static final SildurAoEpoch EPOCH=new SildurAoEpoch();private static boolean installed,recovering,pending,retryBlocked;
 private static final AoNativeFallback FALLBACK=new AoNativeFallback();
 private static final SildurAoFrameProof FRAME=new SildurAoFrameProof();private static long frameSerial;
 private static long sourceGeneration,blockedGeneration;private static Object pipeline,renderer,pack;private static RenderTargets targets;
 private static long frameBegins,qualifiedComposites,producerReadyDraws,producerReturns,consumerReadyDraws;
 private static IrisCompositeCompiledInputs.Pair compiledPair;
 private static SildurAoGlProof.Binding producerNormal;private static SildurAoGlProof.MaterialBinding producerMaterial;
 private static void clearMetadata(){producerNormal=null;producerMaterial=null;}private static boolean activeComposite;private static SildurAoPassView producer,consumer;private static SildurAoEpoch.Context context;private static SildurAoGlProof.Draw started;private static String failure="not yet rendered",admission="not queried";
 private SildurAoRuntime(){}
 public static void install(){if(installed)return;installed=true;CompileOptionControl.configureAdmission(SildurAoContract.ID,OptimizationId.AO_RENDER_SCALE,SildurAoRuntime::setupAdmitted);}
 public static AoNativeFallback.Receipt takeFallback(){return FALLBACK.take();}
 public static String lastFailure(){return failure;}
 public static String status(){return "sourceProofReplays="+sourceProofReplays+"; "+"installed="+installed+"; loadedHalf="+loadedHalf()+"; setup="+admission+"; recoveryPending="+pending+"; lastFailure="+failure+"; cumulative observed callbacks: frames="+frameBegins+", qualifiedComposites="+qualifiedComposites+", producerReady="+producerReadyDraws+", producerReturned="+producerReturns+", consumerReady="+consumerReadyDraws;}
 private static boolean refuseSetup(String why){SOURCE_CACHE.invalidate();admission="refused: "+why;return false;}
 private static Path selected(){String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return null;Path d=Iris.getShaderpacksDirectory().toAbsolutePath().normalize(),p=d.resolve(name).normalize();return p.startsWith(d)&&!p.equals(d)?p:null;}
 private static String loadedHalf(){var current=Iris.getCurrentPack().orElse(null);if(current==null)return null;var o=current.getShaderPackOptions();return SildurAoOptionGuard.declaredHalf(o.getOptionSet(),o.getOptionValues()).orElse(null);}
 private static Map<String,String> options(boolean verifiedSource){var current=Iris.getCurrentPack().orElse(null);if(current==null)return Map.of();var o=current.getShaderPackOptions();return SildurAoOptionGuard.snapshot(o.getOptionSet(),o.getOptionValues(),verifiedSource);}
 private static long sourceProofReplays,sourceCalls,sourceElapsedNanos;
 public static Map<String,Long> costCounters(){return Map.of("source_calls",sourceCalls,"source_elapsed_ns",sourceElapsedNanos,"frame_begins",frameBegins,"qualified_composites",qualifiedComposites,"producer_ready",producerReadyDraws,"producer_returns",producerReturns,"consumer_ready",consumerReadyDraws);}

 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 /** Loading owns the full source proof; rendering consumes its pipeline-bound receipt. */
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(SildurAoRuntime.class,"source"),()->{long at=System.nanoTime();sourceCalls++;try{return sourceLive();}finally{sourceElapsedNanos+=System.nanoTime()-at;}
  });
 }
 private static boolean sourceLive(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(SildurAoRuntime.class,"sourceLive"),()->{
  try{
   if(Iris.isFallback()||!SildurAoApiPins.exact()){SOURCE_CACHE.invalidate();return false;}
   var currentPack=Iris.getCurrentPack().orElse(null);var path=selected();
   if(currentPack==null||path==null){SOURCE_CACHE.invalidate();return false;}
   var shaderOptions=currentPack.getShaderPackOptions();var actual=options(true);
   var client=net.minecraft.client.Minecraft.getInstance();
   boolean accepted=SOURCE_CACHE.check(SildurAoRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),
    "sildurs-vibrant-shaders",actual,currentPack,shaderOptions.getOptionValues(),shaderOptions.getOptionSet(),
    Iris.getPipelineManager().getPipelineNullable(),client.level,sourceGeneration,
    Arrays.asList(OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue()),List.copyOf(client.getResourcePackRepository().getSelectedIds())),
    client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),
    ()->{sourceProofReplays++;return SildurAoSourceProof.verify(path,()->SildurAoRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/sildurs-vibrant-shaders.json"));});
   if(!accepted)SOURCE_CACHE.invalidate();return accepted;
  }catch(Exception|LinkageError unavailable){SOURCE_CACHE.invalidate();return false;}
  catch(Error failure){SOURCE_CACHE.invalidate();throw failure;}
 
  });
 }
 public static final String SOURCE_RECIPE_SHA=SildurAoSourceProof.RECIPE_SHA;
 private static boolean requested(){var core=OptimizationRuntimeContext.core();return core!=null&&core.settings().enabled()&&core.preset()!=LodPreset.QUALITY&&core.optimizations().enabled(OptimizationId.AO_RENDER_SCALE)&&OptimizationConfig.requested(OptimizationId.AO_RENDER_SCALE);}
 private static boolean environment(){var p=IrisPlatformHelpers.getInstance();return !Iris.isFallback()&&SildurAoApiPins.exact()&&!p.isModLoaded("distanthorizons")&&!p.isModLoaded("voxy");}
 private static SildurAoFrameProof.Key frameKey(){var p=selected();return targets==null?null:new SildurAoFrameProof.Key(Iris.getCurrentPack().orElse(null),pipeline,targets,frameSerial,sourceGeneration,OptimizationConfig.generation(),targets.getCurrentWidth(),targets.getCurrentHeight(),p==null?null:p.toString(),OptimizationRuntimeContext.core());}
 public static boolean setupAdmitted(){install();if(retryBlocked&&blockedGeneration==OptimizationConfig.generation())return refuseSetup("prior resource/source failure at current flag generation");if(retryBlocked){retryBlocked=false;EPOCH.requalified();}
  if(loadedHalf()==null)return refuseSetup("exact Sildur130 declarations unavailable");if(!environment())return refuseSetup("API/fallback/DH/VOXY environment");if(!SildurAoContract.metadataRoute(options(true)))return refuseSetup("AO metadata shifted spatial graph not qualified");if(!source())return refuseSetup("source/state/recipe proof");if(!SildurAoContract.options(options(true)))return refuseSetup("complete generated130 live options");admission="accepted source/profile/setup; per-frame target proof still required";return true;}
 public static long sourceGeneration(){return sourceGeneration;}
 public static void destroy(Object destroyed){IrisCompositeCompiledInputs.destroy(destroyed);SOURCE_CACHE.invalidate();if(pipeline==destroyed){compiledPair=null;FRAME.reset();EPOCH.invalidate();activeComposite=false;pipeline=renderer=pack=null;targets=null;producer=consumer=null;context=null;started=null;clearMetadata();}}
 public static void reloadHead(){IrisCompositeCompiledInputs.clear(IrisCompositeCompiledInputs.Family.SILDUR);compiledPair=null;SOURCE_CACHE.invalidate();if(!recovering)FALLBACK.clear();sourceGeneration++;FRAME.reset();EPOCH.invalidate();pipeline=renderer=pack=null;targets=null;producer=consumer=null;context=null;started=null;clearMetadata();}
 public static void begin(Object p,RenderTargets t,Object deferred){install();frameBegins++;frameSerial++;FRAME.reset();compiledPair=null;activeComposite=false;pipeline=p;targets=t;renderer=deferred;pack=Iris.getCurrentPack().orElse(null);context=null;started=null;clearMetadata();EPOCH.invalidate();}
 public static void endComposite(){compiledPair=null;activeComposite=false;context=null;started=null;clearMetadata();}
 public static void beginComposite(Object r,RenderTargets t,List<?> passes){if(r!=renderer||t!=targets||!"1".equals(loadedHalf()))return;activeComposite=true;producer=consumer=null;
  if(!requested()){disabled("AO request disabled; native fallback");return;}
  if((retryBlocked&&blockedGeneration==OptimizationConfig.generation())||!environment()){lose("AO active frame source/profile not qualified");return;}
  if(retryBlocked){retryBlocked=false;EPOCH.requalified();}
  try{var graph=options(true);boolean spatial=com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract.mode(graph)>0;if(!SildurAoContract.metadataRoute(graph)){lose("AO metadata shifted spatial graph not qualified");return;}String aorole=com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract.aoProducer(graph),lightrole=com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract.lighting(graph),reflrole=com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract.reflection(graph);SildurAoPassView reflection=null,field=null;for(Object pass:passes){var v=(SildurAoPassView)pass;if(v.shaderlod$name().equals(aorole))producer=v;else if(v.shaderlod$name().equals(lightrole))consumer=v;else if(v.shaderlod$name().equals(reflrole))reflection=v;else if(spatial&&v.shaderlod$name().equals("deferred"))field=v;else{lose("unexpected deferred graph stage");return;}}
   if(producer==null||consumer==null){lose("AO required programs absent");return;}
   var proof=IrisCompositeCompiledInputs.lookup(IrisCompositeCompiledInputs.Family.SILDUR,producer.shaderlod$program(),consumer.shaderlod$program(),r,pipeline,t);
   if(proof.state()!=IrisCompositeCompiledInputs.State.VALID){lose("AO compiled ownership/input currency refused");return;}
   compiledPair=proof.pair();
   if(producer==null||consumer==null||(passes.size()!=(spatial?3:2)&&passes.size()!=(spatial?4:3))||passes.get(spatial?1:0)!=producer||passes.get(spatial?2:1)!=consumer||(spatial&&(field==null||passes.get(0)!=field||!Arrays.equals(field.shaderlod$drawBuffers(),new int[]{12})||field.shaderlod$scale().scale()!=1f||!field.shaderlod$mips().isEmpty()))||!Arrays.equals(producer.shaderlod$drawBuffers(),new int[]{10})||!Arrays.equals(consumer.shaderlod$drawBuffers(),new int[]{4,6})||!producer.shaderlod$mips().isEmpty()||!consumer.shaderlod$mips().isEmpty()||producer.shaderlod$scale().scale()!=.5f||consumer.shaderlod$scale().scale()!=1f||producer.shaderlod$scale().viewportX()!=0||producer.shaderlod$scale().viewportY()!=0||consumer.shaderlod$scale().viewportX()!=0||consumer.shaderlod$scale().viewportY()!=0||(reflection!=null&&(!Arrays.equals(reflection.shaderlod$drawBuffers(),new int[]{4})||reflection.shaderlod$scale().scale()!=1f||!reflection.shaderlod$mips().isEmpty()))){lose("AO source graph layout differs");return;}

   var settings=((SildurAoTargetsView)targets).shaderlod$settings().get(10);
   if(settings==null||!settings.getInternalFormat().name().equals("RGBA32F")||!settings.shouldClear()||settings.getClearColor().isEmpty()||!settings.getClearColor().get().equals(1,0,0,-1)){lose("AO target clear/format directive differs");return;}
   if(!FRAME.qualify(frameKey(),true,true,()->compiledPair!=null?compiledPair.currentOptions():source()?options(true):Map.of())){lose("AO active frame source/profile not qualified");return;}
   context=new SildurAoEpoch.Context(pipeline,pack,targets,sourceGeneration,OptimizationConfig.generation(),targets.getCurrentWidth(),targets.getCurrentHeight(),producer.shaderlod$program().getProgramId(),consumer.shaderlod$program().getProgramId());EPOCH.begin(context);qualifiedComposites++;
  }catch(RuntimeException|LinkageError unavailable){lose("AO setup API unavailable");}}
 private static boolean current(){return context!=null&&pipeline!=null&&pack==Iris.getCurrentPack().orElse(null)&&context.flagGeneration()==OptimizationConfig.generation()&&context.sourceGeneration()==sourceGeneration&&requested()&&environment()&&FRAME.current(frameKey(),compiledPair==null?options(true):compiledPair.currentOptions());}
 public static void beforeDraw(){if(!activeComposite)return;int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p<=0||GL20.glGetUniformLocation(p,"shaderlodSildurAoTargetReady")<0)return;SildurAoGlProof.upload(p,false,0,0);started=null;
  try{if(!current()){if(!requested())disabled("AO request disabled; native fallback");else lose("AO request/source/epoch no longer qualified");return;}
   if(p==context.producerProgram()){clearMetadata();started=SildurAoGlProof.producer(targets,p);producerNormal=started.normal();producerMaterial=started.material();boolean ready=EPOCH.start(context,started.output(),started.qualified()&&producerMaterial!=null);if(!ready){lose("AO producer framebuffer/depth/projection unavailable");return;}SildurAoGlProof.upload(p,true,context.width(),context.height());producerReadyDraws++;}
   else if(p==context.consumerProgram()){var actual=SildurAoGlProof.consumer(targets,p);var binding=actual.field();var nativeNormal=actual.normal();boolean proof=nativeNormal!=null&&nativeNormal.equals(producerNormal)&&SildurAoGlProof.sameMaterial(producerMaterial,actual.material())&&binding!=null&&actual.qualified()&&EPOCH.consumer(context,binding.texture(),true);if(!proof){lose("AO consumer lacks current completed texture");return;}SildurAoGlProof.upload(p,true,context.width(),context.height());consumerReadyDraws++;}
   else if(!com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract.stage(0).equals(producer.shaderlod$name()) && p!=context.producerProgram() && p!=context.consumerProgram())return;else lose("unexpected AO-linked program");
  }catch(RuntimeException|LinkageError unavailable){lose("AO current GL/API proof failed");}}
 public static void afterDraw(){if(started==null||context==null)return;try{if(!current()){if(!requested())disabled("AO request disabled during producer");else lose("AO source/profile epoch changed during producer");return;}var actual=SildurAoGlProof.producer(targets,context.producerProgram());var normal=actual.normal();if(!EPOCH.returned(context,actual.output(),true,actual.qualified()&&actual.output().equals(started.output())&&normal!=null&&normal.equals(producerNormal)&&SildurAoGlProof.sameMaterial(producerMaterial,actual.material())))lose("AO producer changed bindings before return");else producerReturns++;}catch(RuntimeException|LinkageError unavailable){lose("AO producer return proof unavailable");}finally{started=null;}}
 private static void disabled(String reason){SOURCE_CACHE.invalidate();failure=reason;pending=true;EPOCH.revoke();FRAME.reset();compiledPair=null;context=null;started=null;clearMetadata();}
 private static void lose(String reason){SOURCE_CACHE.invalidate();failure=reason;pending=true;retryBlocked=true;blockedGeneration=OptimizationConfig.generation();EPOCH.revoke();FRAME.reset();compiledPair=null;context=null;started=null;clearMetadata();}
 /** Called only by pinned PipelineManager HEAD before caller captures its returned pipeline. */
 public static WorldRenderingPipeline beforePrepare(NamespacedId dim){install();if(!pending||recovering)return null;if(loadedHalf()==null){pending=false;EPOCH.revoke();return null;}recovering=true;
  try{
   var oldPack=Iris.getCurrentPack().orElse(null);var oldPipeline=Iris.getPipelineManager().getPipelineNullable();var oldSelected=Iris.getIrisConfig().getShaderPackName().orElse(null);
   var oldOptions=options(true);long generation=OptimizationConfig.generation();var world=net.minecraft.client.Minecraft.getInstance().level;
   boolean emptyQueue=Iris.getShaderPackOptionQueue().isEmpty();String reason=failure;FALLBACK.clear();
   SildurAoRecovery.nativeReload(Iris.getShaderPackOptionQueue(),Iris::reload);if(!"0".equals(loadedHalf()))throw new IllegalStateException("Sildur native graph0 recovery did not load");pending=false;
   var restored=Iris.getPipelineManager().preparePipeline(dim);
   if(generation==OptimizationConfig.generation()&&world==net.minecraft.client.Minecraft.getInstance().level
       &&SildurAoContract.options(oldOptions)&&SildurAoContract.options(options(true)))
    FALLBACK.publish(new AoNativeFallback.Receipt(SildurAoContract.ID,oldPack,Iris.getCurrentPack().orElse(null),oldPipeline,Iris.getPipelineManager().getPipelineNullable(),world,dim,generation,
       oldSelected,Iris.getIrisConfig().getShaderPackName().orElse(null),oldOptions,options(true),
       emptyQueue&&Iris.getShaderPackOptionQueue().isEmpty(),reason));
   return restored;
  }
  catch(Exception failure){throw new IllegalStateException("Could not restore Sildur native AO graph before pipeline capture",failure);}
  finally{recovering=false;}}

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}try{sourceLive();}catch(Exception|LinkageError refused){}}
}
