package com.pp.shaderlod.optimization.blissao;
import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.optimization.makeupao.MakeUpAoEpoch;import com.pp.shaderlod.optimization.blisspixel.BlissPixelApiPins;import com.pp.shaderlod.optimization.blissao.mixin.*;
import java.nio.file.*;import java.util.*;import net.irisshaders.iris.Iris;import net.irisshaders.iris.platform.IrisPlatformHelpers;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.pipeline.WorldRenderingPipeline;import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;import org.lwjgl.opengl.*;
/** AO-only deferred draw, completed current depth1 field, full native consumer/fallback and native0 recovery. */
public final class BlissAoRuntime {
 private static final MakeUpAoEpoch EPOCH=new MakeUpAoEpoch();private static boolean installed,pending,recovering,retryBlocked;private static long sourceGeneration,blockedGeneration;private static Object pipeline,pack,deferred,composite,controls;private static Map<String,String> profile=Map.of();private static RenderTargets targets;private static BlissAoPassView producer,consumer;private static MakeUpAoEpoch.Context context;private static BlissAoGlProof.Draw started,completed;private static String failure="not rendered";private static long producerReturns,readyConsumers,nativeFallbacks;
 private static final AoNativeFallback FALLBACK=new AoNativeFallback();
 public static AoNativeFallback.Receipt takeFallback(){return FALLBACK.take();}
 private BlissAoRuntime(){}
 public static void install(){if(installed)return;installed=true;CompileOptionControl.configureAdmission(BlissAoContract.ID,OptimizationId.AO_RENDER_SCALE,BlissAoRuntime::setupAdmitted);}
 public static String status(){return "sourceProofReplays="+sourceProofReplays+"; "+"Bliss AO half; installed="+installed+"; last="+failure+"; producerReturns="+producerReturns+"; readyConsumers="+readyConsumers+"; nativeFallbacks="+nativeFallbacks+"; recoveryPending="+pending;}
 private static Path selected(){var n=Iris.getIrisConfig().getShaderPackName().orElse(null);if(n==null)return null;var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var p=dir.resolve(n).normalize();return p.startsWith(dir)&&!p.equals(dir)&&Path.of(n).getFileName().toString().equals(n)?p:null;}
 private static Map<String,String> options(){var p=Iris.getCurrentPack().orElse(null);if(p==null)return Map.of();var o=p.getShaderPackOptions();return BlissAoOptionGuard.snapshot(o.getOptionSet(),o.getOptionValues());}
 private static String loaded(){var p=Iris.getCurrentPack().orElse(null);if(p==null)return null;var o=p.getShaderPackOptions();return BlissAoOptionGuard.declaredHalf(o.getOptionSet(),o.getOptionValues()).orElse(null);}
 private static boolean environment(){var p=IrisPlatformHelpers.getInstance();return BlissPixelApiPins.exact()&&!Iris.isFallback()&&CompileOptionControl.overworld(Iris.getCurrentDimension())&&!p.isModLoaded("distanthorizons")&&!p.isModLoaded("voxy");}
 private static boolean requested(){var c=OptimizationRuntimeContext.core();return c!=null&&c.settings().enabled()&&c.preset()!=LodPreset.QUALITY&&c.optimizations().enabled(OptimizationId.AO_RENDER_SCALE)&&OptimizationConfig.requested(OptimizationId.AO_RENDER_SCALE);}
 private static long sourceProofReplays;
 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 /** Loading owns the full source proof; rendering consumes its pipeline-bound receipt. */
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(BlissAoRuntime.class,"source"),()->{
  try{
   var currentPack=Iris.getCurrentPack().orElse(null);var path=selected();
   if(currentPack==null||path==null){SOURCE_CACHE.invalidate();return false;}
   var shaderOptions=currentPack.getShaderPackOptions();var actual=options();
   var client=net.minecraft.client.Minecraft.getInstance();
   boolean accepted=SOURCE_CACHE.check(BlissAoRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),
    "bliss-shader",actual,currentPack,shaderOptions.getOptionValues(),shaderOptions.getOptionSet(),
    Iris.getPipelineManager().getPipelineNullable(),client.level,sourceGeneration,
    Arrays.asList(OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue()),List.copyOf(client.getResourcePackRepository().getSelectedIds())),
    client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),
    ()->{sourceProofReplays++;return BlissAoSourceProof.verify(path,()->BlissAoRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/bliss-shader.json"));});
   if(!accepted)SOURCE_CACHE.invalidate();return accepted;
  }catch(Exception|LinkageError unavailable){SOURCE_CACHE.invalidate();return false;}
  catch(Error failure){SOURCE_CACHE.invalidate();throw failure;}
 
  });
 }
 public static boolean setupAdmitted(){install();if(retryBlocked&&blockedGeneration==OptimizationConfig.generation()){SOURCE_CACHE.invalidate();return false;}if(!environment()||loaded()==null||!source()||!BlissAoContract.options(options())){SOURCE_CACHE.invalidate();return false;}retryBlocked=false;EPOCH.requalified();return true;}
 public static void invalidate(){SOURCE_CACHE.invalidate();if(!recovering)FALLBACK.clear();sourceGeneration++;EPOCH.invalidate();pipeline=pack=deferred=composite=controls=null;profile=Map.of();targets=null;producer=consumer=null;context=null;started=completed=null;}
 public static void begin(Object p,RenderTargets t,Object d,Object c){install();EPOCH.invalidate();pipeline=p;pack=Iris.getCurrentPack().orElse(null);targets=t;deferred=d;composite=c;controls=OptimizationRuntimeContext.core();profile=Map.copyOf(options());producer=consumer=null;context=null;started=completed=null;if(!"1".equals(loaded()))return;
  if(!requested()||!environment()||(retryBlocked&&blockedGeneration==OptimizationConfig.generation())||!BlissAoContract.options(options())||!source()){lose("current request/environment/profile/source refused");return;}
  try{for(Object row:((BlissAoCompositeView)d).shaderlod$passes()){var v=(BlissAoPassView)row;if(v.shaderlod$name().equals("deferred3"))producer=v;}
   for(Object row:((BlissAoCompositeView)c).shaderlod$passes()){var v=(BlissAoPassView)row;if(v.shaderlod$name().equals("composite1"))consumer=v;}
   if(producer==null||consumer==null||!Arrays.equals(producer.shaderlod$drawBuffers(),new int[]{12})||!Arrays.equals(consumer.shaderlod$drawBuffers(),new int[]{3,14,12})||!producer.shaderlod$mips().isEmpty()||producer.shaderlod$scale().scale()!=.5f||consumer.shaderlod$scale().scale()!=1f||producer.shaderlod$scale().viewportX()!=0||producer.shaderlod$scale().viewportY()!=0||consumer.shaderlod$scale().viewportX()!=0||consumer.shaderlod$scale().viewportY()!=0){lose("AO graph layout refused");return;}
   var rt=t.get(12);if(rt==null||!rt.getInternalFormat().name().equals("RGBA32F")){lose("AO scratch format refused");return;}
   context=new MakeUpAoEpoch.Context(p,pack,t,sourceGeneration,OptimizationConfig.generation(),t.getCurrentWidth(),t.getCurrentHeight(),producer.shaderlod$program().getProgramId(),consumer.shaderlod$program().getProgramId());EPOCH.requalified();EPOCH.begin(context);failure="current graph qualified; waiting for original producer return";
  }catch(RuntimeException|LinkageError unavailable){lose("AO normal graph API unavailable");}}
 private static boolean current(){return context!=null&&pack==Iris.getCurrentPack().orElse(null)&&context.sourceGeneration()==sourceGeneration&&context.flagGeneration()==OptimizationConfig.generation()&&context.width()==targets.getCurrentWidth()&&context.height()==targets.getCurrentHeight()&&requested()&&environment()&&Objects.equals(controls,OptimizationRuntimeContext.core())&&profile.equals(options());}
 public static void beforeDraw(){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p<=0||GL20.glGetUniformLocation(p,"shaderlodBlissAoTargetReady")<0)return;BlissAoGlProof.upload(p,false,0,0);started=null;
  try{if(!current()){nativeFallbacks++;return;}if(p==context.producerProgram()){started=BlissAoGlProof.producer(targets,p);if(!EPOCH.start(context,started.output(),started.qualified())){started=null;lose("AO actual producer target/depth/material/normal refused");return;}BlissAoGlProof.upload(p,true,context.width(),context.height());}
   else if(p==context.consumerProgram()){boolean ready=completed!=null&&BlissAoGlProof.consumer(targets,p,completed.output().texture(),completed.inputs())&&EPOCH.consumer(context,completed.output().texture(),true);if(ready){BlissAoGlProof.upload(p,true,context.width(),context.height());readyConsumers++;}else{nativeFallbacks++;lose("AO full native fallback: current completed field proof absent");}}
  }catch(RuntimeException|LinkageError unavailable){lose("AO actual GL proof unavailable; native SSAO retained");}}
 public static void afterDraw(){if(started==null||context==null)return;try{var actual=BlissAoGlProof.producer(targets,context.producerProgram());if(current()&&actual.qualified()&&Objects.equals(actual.inputs(),started.inputs())&&EPOCH.returned(context,actual.output(),true,actual.output().equals(started.output()))){completed=actual;producerReturns++;}else lose("AO producer changed before original draw return");}catch(RuntimeException|LinkageError unavailable){lose("AO producer return unavailable");}finally{started=null;}}
 private static void lose(String why){SOURCE_CACHE.invalidate();failure=why;pending=true;retryBlocked=true;blockedGeneration=OptimizationConfig.generation();EPOCH.revoke();context=null;started=completed=null;}
 public static WorldRenderingPipeline beforePrepare(NamespacedId dim){install();if(!pending||recovering)return null;if(!"1".equals(loaded())){pending=false;return null;}recovering=true;try{
   var oldPack=Iris.getCurrentPack().orElse(null);var oldPipeline=Iris.getPipelineManager().getPipelineNullable();var oldSelected=Iris.getIrisConfig().getShaderPackName().orElse(null);
   var oldOptions=options();long generation=OptimizationConfig.generation();var world=net.minecraft.client.Minecraft.getInstance().level;
   boolean emptyQueue=Iris.getShaderPackOptionQueue().isEmpty();String reason=failure;FALLBACK.clear();
   BlissAoRecovery.nativeReload(Iris.getShaderPackOptionQueue(),Iris::reload);if(!"0".equals(loaded()))throw new IllegalStateException("Bliss native graph0 recovery did not load");pending=false;
   var restored=Iris.getPipelineManager().preparePipeline(dim);
   if(generation==OptimizationConfig.generation()&&world==net.minecraft.client.Minecraft.getInstance().level
       &&BlissAoContract.options(oldOptions)&&BlissAoContract.options(options()))
    FALLBACK.publish(new AoNativeFallback.Receipt(BlissAoContract.ID,oldPack,Iris.getCurrentPack().orElse(null),oldPipeline,Iris.getPipelineManager().getPipelineNullable(),world,dim,generation,
       oldSelected,Iris.getIrisConfig().getShaderPackName().orElse(null),oldOptions,options(),
       emptyQueue&&Iris.getShaderPackOptionQueue().isEmpty(),reason));
   return restored;
  }catch(Exception failed){throw new IllegalStateException("Could not restore native Bliss AO before pipeline capture",failed);}finally{recovering=false;}}

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}}
}
