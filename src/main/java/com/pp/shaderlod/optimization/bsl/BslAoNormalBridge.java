package com.pp.shaderlod.optimization.bsl;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.packs.SupportedPackCatalog;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;
import java.util.*;

/** Pinned BSL actual pass protocol. The recovery producer is an actual native
 * shader graph pass retained at full size, drawn only immediately before a
 * consumer whose completed half result cannot be qualified. */
public final class BslAoNormalBridge {
 private static final String SOURCE="30be445dc56f9725b946156a0a210942ee6288fb4689dd737288581959c85f4a";
 private enum Role {HALF,RECOVERY,CONSUMER}
 private static final class Graph {
  final Object pipeline;final RenderTargets targets;final BslAoEpoch epoch=new BslAoEpoch();
  final Map<Program,Role> roles=new IdentityHashMap<>(); Program half,recovery,consumer;
  BslAoEpoch.Context context;BslAoGlProof.Producer produced;int recoveryFbo,recoveryTexture;
  Graph(Object p,RenderTargets t){pipeline=p;targets=t;}
 }
 private static final Map<Object,Graph> graphs=new IdentityHashMap<>();
 private static boolean installed,revoked,recovering,sourceQualified,activeFrame;
 private static Object qualifiedPack;private static String qualifiedName;
 private static long halfStarts,halfQualifiedStarts,halfReturns,halfQualifiedReturns,skippedNativeRecovery,readyConsumers,nativeFallbacks,nativeFallbackReturns;
 private static String setupStatus="not queried",sourceStatus="not queried",lastFailure="none";
 /** Cached observations only: no source, option, API-resource or GL query is performed here. Counters are cumulative for this JVM, including warmup. */
 public static String status(){return "BSL half-AO; installed="+installed+"; cachedSetup="+setupStatus+"; cachedSource="+sourceStatus+"; revoked="+revoked+"; activeFrame="+activeFrame+"; graphs="+graphs.size()+"; lastFailure="+lastFailure+"; cumulative callbacks: halfStarts="+halfStarts+", halfQualifiedStarts="+halfQualifiedStarts+", halfReturns="+halfReturns+", halfQualifiedReturns="+halfQualifiedReturns+", skippedNativeRecovery="+skippedNativeRecovery+", readyConsumers="+readyConsumers+", nativeFallbacks="+nativeFallbacks+", nativeFallbackReturns="+nativeFallbackReturns;}
 private BslAoNormalBridge() {}
 public static void install() {
  if(installed)return;installed=true;
  CompileOptionControl.configureAdmission("bsl-shaders",OptimizationId.AO_RENDER_SCALE,BslAoNormalBridge::setup);
  OptimizationRuntimeContext.configureImplementation("bsl-shaders",OptimizationId.AO_DEPTH_REUSE,BslAoDepthRuntime::status);
 }
 /** Exact pinned shader consumer accepts only nonzero legacy presets1/2.
  * BALANCED0 must compile native0 and, if a retained half graph exists, recover
  * full native AO before reading colortex4. QUALITY3 is native as well. */
 public static boolean presetEligible(com.pp.shaderlod.CoreUniformFrame.Core core) {
  return core!=null&&core.settings().enabled()&&
   (core.preset()==com.pp.shaderlod.LodPreset.PERFORMANCE||core.preset()==com.pp.shaderlod.LodPreset.AGGRESSIVE);
 }
 public static boolean setup() {
  if(!presetEligible(OptimizationRuntimeContext.core())||revoked||!BslAoApiPins.exactApi()){setupStatus="preset/revocation/API refused; backend="+BslAoApiPins.backend();return false;}
  try {
   var caps=GL.getCapabilities();
   if(!caps.OpenGL33||GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS)<16||GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)<1){setupStatus="GL capabilities refused";return false;}
   boolean qualified=qualifySelectedSource();setupStatus="backend="+BslAoApiPins.backend()+"; selectedSource="+qualified;return qualified;
  } catch(Exception|LinkageError unavailable){setupStatus="unavailable: "+unavailable.getClass().getSimpleName();return false;}
 }
 /** Source qualification is independent of preset: a retained unsupported half
  * graph still needs its faithful recovery producer, but never admission1. */
 public static boolean qualifySelectedSource() {
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(BslAoNormalBridge.class,"qualifySelectedSource"),()->{qualifiedPack=null;
  try {
   var pack=Iris.getCurrentPack().orElse(null);var name=Iris.getIrisConfig().getShaderPackName().orElse(null);
   if(pack==null||name==null){sourceStatus="no selected pack/name";return false;}
   if(pack==qualifiedPack&&name.equals(qualifiedName))return sourceQualified;
   var directory=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=directory.resolve(name).normalize();
   if(path.equals(directory)||!path.startsWith(directory)){sourceStatus="unsafe selected source";return false;}
   var source=PackSourceReader.read(path);sourceQualified=SupportedPackCatalog.shaderSourceDigest(source.memberSha256()).equals(SOURCE);
   qualifiedPack=pack;qualifiedName=name;sourceStatus=sourceQualified?"exact pinned source":"source mismatch";return sourceQualified;
  } catch(Exception|LinkageError unavailable){sourceStatus="unavailable: "+unavailable.getClass().getSimpleName();return false;}
 
  });
 }
 public static void built(Object pipeline,RenderTargets targets,ProgramSource source,Program program) {
  BslAoDepthRuntime.built(pipeline,targets,source,program);
  String text=source.getFragmentSource().orElse("");Role role;
  if(source.getName().equals("deferred")&&text.contains("uniform int shaderlodBslAoProducerPass;"))role=Role.HALF;
  else if(source.getName().equals("deferred1")&&text.contains("uniform int shaderlodBslAoRecoveryPass;"))role=Role.RECOVERY;
  else if(source.getName().equals("deferred2")&&text.contains("uniform int shaderlodBslAoReady;"))role=Role.CONSUMER;
  else return;
  var loaded=Iris.getCurrentPack().orElse(null);if(loaded==null)return;
  var options=loaded.getShaderPackOptions();
  if(!BslAoOptionGuard.half(options.getOptionSet(),options.getOptionValues(),qualifySelectedSource()).orElse("0").equals("1"))return;
  if(role==Role.HALF)setup();
  var graph=graphs.computeIfAbsent(pipeline,ignored->new Graph(pipeline,targets));
  if(graph.targets!=targets)throw new IllegalStateException("BSL AO graph changed target identity");
  graph.roles.put(program,role);
  switch(role){case HALF->graph.half=program;case RECOVERY->graph.recovery=program;case CONSUMER->graph.consumer=program;}
 }
 private static BslAoEpoch.Context context(Graph graph) {
  return new BslAoEpoch.Context(graph.pipeline,Iris.getCurrentPack().orElse(null),graph.targets,
   1,OptimizationConfig.generation(),graph.targets.getCurrentWidth(),graph.targets.getCurrentHeight(),
   graph.half==null?0:graph.half.getProgramId(),graph.consumer==null?0:graph.consumer.getProgramId());
 }
 public static boolean owns(Object pipeline,Program program) {var graph=graphs.get(pipeline);return graph!=null&&graph.roles.containsKey(program);}
 public static void begin(Object pipeline) {
  var graph=graphs.get(pipeline);if(graph==null)return;
  graph.context=context(graph);graph.epoch.begin(graph.context);graph.produced=null;graph.recoveryFbo=graph.recoveryTexture=0;
 }
 private static boolean enabled() {
  var core=OptimizationRuntimeContext.core();return presetEligible(core)&&
   OptimizationConfig.snapshot().enabled(OptimizationId.AO_RENDER_SCALE)&&sourceQualified&&!revoked&&profile();
 }
 private static boolean profile() {
  var pack=Iris.getCurrentPack().orElse(null);if(pack==null)return false;
  var options=pack.getShaderPackOptions();var platform=net.irisshaders.iris.platform.IrisPlatformHelpers.getInstance();
  return BslAoOptionGuard.nativeProfile(options.getOptionSet(),options.getOptionValues(),qualifySelectedSource())&&
   CompileOptionControl.overworld(Iris.getCurrentDimension())&&!platform.isModLoaded("distanthorizons")&&!platform.isModLoaded("voxy");
 }
 private static void loss(Graph graph,String reason) {
  lastFailure=reason;revoked=true;graph.epoch.revoke();BslAoNativeRecovery.request(reason);
 }
 /** Runs immediately around the original actual fullscreen draw after native
  * Program.use/sampler uploads. Never reloads an active rendering pipeline. */
 public static void quad(Object pipeline,Program active,Runnable originalDraw) {
  BslAoDepthRuntime.beforeDraw(pipeline,active);
  var graph=graphs.get(pipeline);var role=graph==null?null:graph.roles.get(active);
  if(role==null){originalDraw.run();return;}
  switch(role) {
   case HALF -> {
    boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_CULL_FACE);
    try {
     var before=BslAoGlProof.producer(graph.targets,active.getProgramId());
     halfStarts++;boolean start=graph.epoch.start(context(graph),before.output(),enabled()&&before.qualified());
     if(start)halfQualifiedStarts++;originalDraw.run();halfReturns++;
     var after=BslAoGlProof.producer(graph.targets,active.getProgramId());
     boolean returned=graph.epoch.returned(context(graph),after.output(),true,start&&after.qualified()&&before.depth().equals(after.depth())&&before.noise().equals(after.noise()));
     if(returned)halfQualifiedReturns++;graph.produced=returned?after:null;
    } finally {if(cull)GL11.glEnable(GL11.GL_CULL_FACE);}
   }
   case RECOVERY -> {
    // Save the actual native graph framebuffer selected by Iris' buffer flipper.
    // This pass's full-resolution quad is skipped on every nominal half frame.
    skippedNativeRecovery++;graph.recoveryFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    graph.recoveryTexture=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
   }
   case CONSUMER -> {
    boolean ready=enabled()&&actualControls(active.getProgramId())&&graph.produced!=null&&graph.epoch.consumer(context(graph),graph.produced.output().texture(),
     BslAoGlProof.consumer(graph.targets,active.getProgramId(),graph.produced.output().texture()));
    int location=GL20.glGetUniformLocation(active.getProgramId(),"shaderlodBslAoReady");
    if(location<0)ready=false;
    if(!ready){nativeFallbacks++;loss(graph,"BSL current AO epoch/resource/profile proof lost");nativeProducer(graph,active,originalDraw);nativeFallbackReturns++;}
    // Stage locked: current sampler/program/target is checked after uploads,
    // then the uniform is written and the very next operation is the draw.
    if(location>=0)GL20.glUniform1i(location,ready?1:0);originalDraw.run();if(ready)readyConsumers++;
   }
  }
 }
 private static boolean actualControls(int program) {
  var core=OptimizationRuntimeContext.core();if(!presetEligible(core))return false;
  return integer(program,"shaderlodBridgeVersion")==2&&integer(program,"shaderlodEnabled")==1&&
   integer(program,"shaderlodOptimizationFlagsVersion")==1&&integer(program,"shaderlodOptAoRenderScale")==1&&
   integer(program,"shaderlodPreset")==core.preset().ordinal();
 }
 private static int integer(int program,String uniform){int location=GL20.glGetUniformLocation(program,uniform);return location<0?-1:GL20.glGetUniformi(program,location);}
 private static void nativeProducer(Graph graph,Program consumer,Runnable draw) {
  if(graph.recovery==null||graph.recoveryFbo<=0)throw new IllegalStateException("Native AO recovery pass unavailable");
  int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
  int[] rasterCaps={GL11.GL_BLEND,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL11.GL_DEPTH_TEST,GL11.GL_STENCIL_TEST,GL30.GL_RASTERIZER_DISCARD};
  boolean[] rasterEnabled=new boolean[rasterCaps.length];for(int i=0;i<rasterCaps.length;i++)rasterEnabled[i]=GL11.glIsEnabled(rasterCaps[i]);
  int[] colorMask=new int[4];GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK,colorMask);
  try {
   GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,graph.recoveryFbo);
   GL11.glViewport(0,0,graph.targets.getCurrentWidth(),graph.targets.getCurrentHeight());
   for(int cap:rasterCaps)GL11.glDisable(cap);GL11.glColorMask(true,true,true,true);
   graph.recovery.use();
   if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Native AO recovery FBO incomplete");
   if(!BslAoGlProof.nativeProducer(graph.targets,graph.recovery.getProgramId(),graph.recoveryTexture))throw new IllegalStateException("Native AO producer resources/viewport/source graph invalid");
   BslAoDrawScope.beforeNative(graph.recovery,consumer);BslAoDepthRuntime.beforeDraw(graph.pipeline,graph.recovery);draw.run();BslAoDrawScope.afterNative();
  } finally {
   GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
   for(int i=0;i<rasterCaps.length;i++){if(rasterEnabled[i])GL11.glEnable(rasterCaps[i]);else GL11.glDisable(rasterCaps[i]);}
   GL11.glColorMask(colorMask[0]!=0,colorMask[1]!=0,colorMask[2]!=0,colorMask[3]!=0);
   consumer.use();
  }
  if(!BslAoGlProof.nativeConsumer(graph.targets,consumer.getProgramId(),graph.recoveryTexture))
   throw new IllegalStateException("Native AO reconstruction does not sample actual recovered target4");
 }
 public static void frameStarted(){activeFrame=true;}
 public static void frameReturned(){activeFrame=false;}
 public static void destroyed(Object pipeline){BslAoDepthRuntime.destroy(pipeline);graphs.remove(pipeline);activeFrame=false;}
 public static void onReload(){if(activeFrame)throw new IllegalStateException("Cannot reload BSL AO while a world pipeline is retained");BslAoNativeRecovery.onReload();BslAoDepthRuntime.invalidate();graphs.clear();qualifiedPack=null;qualifiedName=null;sourceQualified=false;setupStatus="invalidated by reload";sourceStatus="invalidated by reload";}
 /** HEAD preparePipeline is outside every active retained LevelRenderer pipeline. */
 public static void beforeCapture(net.irisshaders.iris.shaderpack.materialmap.NamespacedId dimension) {
  install();if(recovering||!BslAoNativeRecovery.requested())return;
  if(!qualifySelectedSource()){BslAoNativeRecovery.foreignReplacement();revoked=false;sourceQualified=false;return;}recovering=true;
  try {if(BslAoNativeRecovery.beforePipelineCapture(activeFrame,revoked,dimension)){revoked=false;sourceQualified=false;}}
  catch(Exception failure){throw new IllegalStateException("Native BSL AO rollback failed",failure);}
  finally {recovering=false;}
 }

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{qualifySelectedSource();}catch(Exception|LinkageError refused){}}
}
