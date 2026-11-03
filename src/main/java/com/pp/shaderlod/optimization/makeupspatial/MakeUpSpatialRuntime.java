package com.pp.shaderlod.optimization.makeupspatial;
import java.util.*;import java.nio.file.*;import net.irisshaders.iris.Iris;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;import org.lwjgl.opengl.*;
import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.optimization.blissspatial.BlissSpatialGl;import com.pp.shaderlod.optimization.photon.PhotonSpatialEpoch;import com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView;
/** Same-frame native SOLID input/representative replay -> original forward consumer. No resolved-RGB factoring. */
public final class MakeUpSpatialRuntime{
 private static final String RECIPE=com.pp.shaderlod.optimization.makeupao.MakeUpAoSourceProof.RECIPE_SHA;private static boolean installed,blocked,pending,recovering;private static long blockedGeneration,inputReturns,rateReturns,returns,consumers,fallbacks,factoryVisits,linkedPrograms,drawVisits;private static String failure="not consumed";
 private static final MakeUpSpatialFallback FALLBACK=new MakeUpSpatialFallback();
 public static MakeUpSpatialFallback.Receipt takeFallback(){return FALLBACK.take();}
 private static long firstFailureGeneration=Long.MIN_VALUE;private static String firstFailure="none";
 private static final PhotonSpatialEpoch EPOCH=new PhotonSpatialEpoch();private static GlFramebuffer fbo,rateFbo;private static int[] fboImages;private static RenderTargets fboTargets;private static int rateCache;private static int activeNative;private static boolean rate,world;private static PhotonSpatialEpoch.Context context;private static Map<String,String> activeOptions;private static Map<String,BlissSpatialGl.Sample> activeShadow;private static BlissSpatialGl.Sample activeAtlas;private static Map<String,List<Integer>> activeUniforms;
 public static final int[] FIELDS={8,12,13,14,15,10};private static int[] fields(){return worldMode()?new int[]{8,12,13,14,15,10,9,11}:FIELDS;}private MakeUpSpatialRuntime(){}
 public static void install(){if(installed)return;installed=true;CompileOptionControl.configureAdmission(MakeUpSpatialContract.ID,OptimizationId.PIXEL_SCALE,MakeUpSpatialRuntime::setup);OptimizationRuntimeContext.configureImplementation(MakeUpSpatialContract.ID,OptimizationId.PIXEL_SCALE,MakeUpSpatialRuntime::worldStatus);CompileOptionControl.configureAdmission(MakeUpSpatialContract.ID,OptimizationId.SPATIAL_LIGHTING_RATE,MakeUpSpatialRuntime::setup);OptimizationRuntimeContext.configureImplementation(MakeUpSpatialContract.ID,OptimizationId.SPATIAL_LIGHTING_RATE,MakeUpSpatialRuntime::status);CompileOptionControl.configureAdmission(MakeUpSpatialContract.ID,OptimizationId.SPATIAL_LIGHTING_REUSE,MakeUpSpatialRuntime::setup);OptimizationRuntimeContext.configureImplementation(MakeUpSpatialContract.ID,OptimizationId.SPATIAL_LIGHTING_REUSE,MakeUpSpatialRuntime::status);}
 public static String status(){return "MakeUp forward native SOLID reuse/rate/worldRGB; factoryVisits="+factoryVisits+"; linkedPrograms="+linkedPrograms+"; drawVisits="+drawVisits+"; inputReturns="+inputReturns+"; rateReturns="+rateReturns+"; producerReturns="+returns+"; readyConsumers="+consumers+"; nativeFallbacks="+fallbacks+"; pending="+pending+"; firstFailure="+firstFailure+"; firstFailureGeneration="+firstFailureGeneration+"; last="+failure;}
 public static String worldStatus(){var m=options();String why="1".equals(m.get(MakeUpSpatialContract.KEY))?"inactive: native lighting reuse takes precedence":"1".equals(m.get(MakeUpSpatialContract.RATE))?"inactive: native lighting rate takes precedence":worldMode()?"ordinary SOLID sceneRGB85%; other world classes/combinations unfinished":"worldRGB graph inactive; native fallback";return why+"; "+status();}
 public static boolean api(){return MakeUpSpatialRateApi.exact()&&com.pp.shaderlod.optimization.makeupprime.mixin.DepthMixinPlugin.exactApi()&&com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowPins.exact()&&"Legacy".equals(com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowPins.kind())&&com.pp.shaderlod.optimization.makeupao.MakeUpAoApiPins.exact("neoforge1.21.1");}
 public static Map<String,String> options(){var pack=net.irisshaders.iris.Iris.getCurrentPack().orElse(null);return pack==null?Map.of():com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());}
 private static boolean environment(){if(!api()||Iris.isFallback())return false;var c=net.minecraft.client.Minecraft.getInstance();if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(c.getResourcePackRepository().getSelectedIds()))return false;var mods=com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.loadedStack();return com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(mods);}
 private static boolean source(){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(MakeUpSpatialRuntime.class,"source"),()->{return RECIPE.equals(com.pp.shaderlod.optimization.makeupao.MakeUpAoSourceProof.RECIPE_SHA)&&com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.source(Iris.getCurrentPack().orElse(null));
  });
 }
 /** Current native facts only; deliberately excludes the generation-blocked setup readiness bit. */
 public static boolean nativeEnvironment(){return environment();}
 public static boolean setup(){return !(blocked&&blockedGeneration==OptimizationConfig.generation())&&environment()&&MakeUpSpatialContract.profile(options())&&source();}
 public static boolean candidatePack(){return setup()&&("1".equals(options().get(MakeUpSpatialContract.KEY))||"1".equals(options().get(MakeUpSpatialContract.RATE))||worldMode());}
 public static boolean requested(){var c=OptimizationRuntimeContext.core();return c!=null&&c.settings().enabled()&&c.preset()==LodPreset.PERFORMANCE&&((c.optimizations().enabled(OptimizationId.SPATIAL_LIGHTING_REUSE)&&OptimizationConfig.requested(OptimizationId.SPATIAL_LIGHTING_REUSE))||(c.optimizations().enabled(OptimizationId.SPATIAL_LIGHTING_RATE)&&OptimizationConfig.requested(OptimizationId.SPATIAL_LIGHTING_RATE))||(c.optimizations().enabled(OptimizationId.PIXEL_SCALE)&&OptimizationConfig.requested(OptimizationId.PIXEL_SCALE)))&&!com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended();}
 public static boolean worldMode(){return "1".equals(options().get(MakeUpSpatialContract.WORLD));}
 public static boolean reducedMode(){return rateMode()||worldMode();}
 public static int[] reducedSize(int w,int h,boolean rgb){return rgb?new int[]{Math.max(1,w*85/100),Math.max(1,h*85/100)}:new int[]{w,h/2};}
 public static boolean rateMode(){return "1".equals(options().get(MakeUpSpatialContract.RATE));}
 public static void factory(boolean linked){if(linked)linkedPrograms++;else factoryVisits++;}
 public static void drawVisit(){drawVisits++;}
 private static Session declined(String why){refuse(why);return null;}
 public static void disable(){blocked=true;blockedGeneration=OptimizationConfig.generation();pending=true;EPOCH.revoke();}
 public static void refuse(String why){if(firstFailureGeneration!=OptimizationConfig.generation()||firstFailure.equals("none")){firstFailure=why;firstFailureGeneration=OptimizationConfig.generation();}failure=why;fallbacks++;disable();}
 public static void reset(){EPOCH.revoke();context=null;activeOptions=null;activeShadow=null;activeAtlas=null;activeUniforms=null;activeNative=0;}
 public static void destroy(){if(!recovering)FALLBACK.clear();reset();if(fbo!=null){fbo.destroy();fbo=null;fboImages=null;fboTargets=null;}if(rateFbo!=null){rateFbo.destroy();rateFbo=null;rateCache=0;}blocked=false;}
 private static int integer(int p,String n){return BlissSpatialGl.integer(p,n);}
 public static void ready(int p,boolean yes,int w,int h){int before=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);try{if(before!=p)GL20.glUseProgram(p);int location=GL20.glGetUniformLocation(p,"shaderlodMakeupSpatialReady");if(location>=0)GL20.glUniform1i(location,yes?1:0);location=GL20.glGetUniformLocation(p,"shaderlodMakeupSpatialSize");if(location>=0)GL20.glUniform2f(location,w,h);}finally{if(before!=p)GL20.glUseProgram(before);}}
 public static void finish(){int p=activeNative;reset();if(p<=0||!GL20.glIsProgram(p))return;int before=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);try{if(before!=p)GL20.glUseProgram(p);ready(p,false,0,0);}finally{if(before!=p)GL20.glUseProgram(before);}}
 private static boolean field(RenderTargets t,int index,BlissSpatialGl.Sample s){var rt=t.get(index);return s!=null&&rt!=null&&s.image().texture()>0&&(s.image().texture()==rt.getMainTexture()||s.image().texture()==rt.getAltTexture())&&s.image().width()==t.getCurrentWidth()&&s.image().height()==t.getCurrentHeight()&&s.image().format()==GL30.GL_RGBA32F&&s.image().base()==0&&s.image().compare()==GL11.GL_NONE&&s.swizzle().equals(List.of(GL11.GL_RED,GL11.GL_GREEN,GL11.GL_BLUE,GL11.GL_ALPHA));}
 public static Map<String,BlissSpatialGl.Sample> shadow(IrisRenderingPipeline pipe,int p,boolean colored){var rt=((ShadowPipelineView)pipe).shadowlod$shadows();if(rt==null)return null;var out=new TreeMap<String,BlissSpatialGl.Sample>();for(String n:colored?List.of("shadowtex0","shadowtex1","shadowcolor0"):List.of("shadowtex1")){var s=BlissSpatialGl.sample(p,n);if(!BlissSpatialGl.lodParity(s))return null;boolean depth=!n.equals("shadowcolor0");if(s.image().compare()!=(depth?GL30.GL_COMPARE_REF_TO_TEXTURE:GL11.GL_NONE))return null;if(depth&&s.compareFunction()!=GL11.GL_LEQUAL)return null;int expected=n.equals("shadowtex1")?rt.getDepthTextureNoTranslucents().getTextureId():n.equals("shadowtex0")?rt.getDepthTexture().getTextureId():rt.getColorTextureId(0);if(s.image().texture()!=expected||s.image().width()!=rt.getResolution()||s.image().height()!=rt.getResolution())return null;if(!depth&&(s.image().format()!=GL11.GL_RGBA8||!s.swizzle().equals(List.of(GL11.GL_RED,GL11.GL_GREEN,GL11.GL_BLUE,GL11.GL_ALPHA))))return null;out.put(n,s);}return Map.copyOf(out);}
 private static Map<String,BlissSpatialGl.Sample> stageInputs(IrisRenderingPipeline pipe,int program,boolean input){var base=shadow(pipe,program,"true".equals(activeOptions.get("COLORED_SHADOW")));if(world&&input){var fog=BlissSpatialGl.sample(program,"gaux4");if(!BlissSpatialGl.lodParity(fog)||activeShadow==null||!Objects.equals(fog,activeShadow.get("gaux4")))return null;if(base==null)base=Map.of();var map=new TreeMap<>(base);map.put("gaux4",fog);return Map.copyOf(map);}return base;}
 private static String stageInputFailure(int p,boolean geometry){
  if(world&&geometry){var fog=BlissSpatialGl.sample(p,"gaux4");return Objects.equals(activeShadow.get("gaux4"),fog)?null:"fog expected="+activeShadow.get("gaux4")+" actual="+fog;}
  var observed=shadow((IrisRenderingPipeline)context.pipeline(),p,"true".equals(activeOptions.get("COLORED_SHADOW")));
  Map<String,BlissSpatialGl.Sample> wanted=activeShadow;if(world){var copy=new TreeMap<>(activeShadow);copy.remove("gaux4");wanted=copy;}
  return Objects.equals(wanted,observed)?null:"expected="+wanted+" actual="+observed;
 }
 private static boolean sameStageInputs(int p,boolean geometry){return stageInputFailure(p,geometry)==null;}
 // RATE's native input-only closure removes all shadow lighting and its dither use.
 // EffectsVersion, selectedRate flag and distance/material policy remain used by SpatialEligible and stay strict.
 private static final Set<String> RATE_INPUT_UNUSED=Set.of("ditherShift","shaderlodOptShadowSampleBudget","shaderlodOptTemporalShadowFilter","shaderlodOptShadowReceiverFootprint");
 // WORLD also skips shadow lighting. Its fog fetch and captured vertex frogAdjust do not consume dither.
 private static final Set<String> WORLD_INPUT_UNUSED=Set.of("ditherShift","shaderlodOptShadowSampleBudget","shaderlodOptTemporalShadowFilter","shaderlodOptShadowReceiverFootprint");
 static List<Integer> stageUniformValue(int location,int bits){return List.of(location<0?0:1,location<0?0:bits);}
 static boolean rateInputUniformMatches(String name,boolean rate,boolean world,boolean sparse,List<Integer> expected,List<Integer> actual){
  return expected.equals(actual)||rate&&!sparse&&(world?WORLD_INPUT_UNUSED:RATE_INPUT_UNUSED).contains(name)&&actual.equals(List.of(0,0));
 }
 // RATE and WORLD reduced lighting consume already-eligible native input images; neither closure calls SpatialEligible/Distance.
 // The selected policy mode is still required by the input producer and original forward consumer.
 static boolean rateLightingUniformMatches(String name,boolean rate,boolean world,boolean sparse,List<Integer> expected,List<Integer> actual){
  return expected.equals(actual)||rate&&sparse&&name.equals("shaderlodOpaqueLightingMode")&&actual.equals(List.of(0,0));
 }
 private static Map<String,List<Integer>> uniforms(int p){
  var out=new TreeMap<String,List<Integer>>();
  for(String n:List.of("nightVision","blindness","darknessFactor","darknessLightFactor","ditherShift","rainStrength","pixelSizeX","pixelSizeY","viewWidth","viewHeight","shaderlodNear","shaderlodFar","shaderlodTransition","shaderlodOpaqueLightingNear","shaderlodOpaqueLightingFar","shaderlodOpaqueLightingTransition","shaderlodOpaqueLightingMediumWorkRatio","shaderlodOpaqueLightingFarWorkRatio")){
   int l=GL20.glGetUniformLocation(p,n);float f=l<0?0:GL20.glGetUniformf(p,l);
   if(l>=0&&(!Float.isFinite(f)||Math.abs(f)>1e10f))throw new IllegalStateException("native lighting/policy scalar finite guard refused: "+n);
   out.put(n,RATE_INPUT_UNUSED.contains(n)?stageUniformValue(l,Float.floatToRawIntBits(f)):List.of(l<0?Integer.MIN_VALUE:Float.floatToRawIntBits(f)));
  }
  for(String n:List.of("gbufferProjectionInverse","gbufferModelViewInverse")){
   int l=GL20.glGetUniformLocation(p,n);float[] v=new float[16];if(l>=0)GL20.glGetUniformfv(p,l,v);var bits=new ArrayList<Integer>();
   for(float f:v){if(!Float.isFinite(f)||Math.abs(f)>1e10f)throw new IllegalStateException("native projection/matrix finite guard refused");bits.add(Float.floatToRawIntBits(f));}
   if(l<0)bits=new ArrayList<>(List.of(Integer.MIN_VALUE));out.put(n,List.copyOf(bits));
  }
  for(String n:List.of("shaderlodOpaqueLightingMode","shaderlodEffectsVersion","shaderlodOptSpatialLightingReuse","shaderlodOptSpatialLightingRate","shaderlodOptPixelScale","shaderlodOptShadowSampleBudget","shaderlodOptTemporalShadowFilter","shaderlodOptShadowReceiverFootprint")){
   int l=GL20.glGetUniformLocation(p,n);int value=l<0?-1:GL20.glGetUniformi(p,l);
   out.put(n,RATE_INPUT_UNUSED.contains(n)||n.equals("shaderlodOpaqueLightingMode")?stageUniformValue(l,value):List.of(value));
  }
  return Map.copyOf(out);
 }
 private static String protocolFailure(int p){
  int value=integer(p,"shaderlodBridgeVersion");if(value!=2)return "shaderlodBridgeVersion="+value;
  value=integer(p,"shaderlodEnabled");if(value!=1)return "shaderlodEnabled="+value;
  value=integer(p,"shaderlodOptimizationFlagsVersion");if(value!=1)return "shaderlodOptimizationFlagsVersion="+value;
  value=integer(p,"shaderlodEffectsVersion");if(value!=2)return "shaderlodEffectsVersion="+value;
  value=integer(p,"shaderlodPreset");if(value!=1)return "shaderlodPreset="+value;
  String flag=worldMode()?"shaderlodOptPixelScale":rateMode()?"shaderlodOptSpatialLightingRate":"shaderlodOptSpatialLightingReuse";
  value=integer(p,flag);return value==1?null:flag+"="+value;
 }
 private static boolean protocols(int p){return protocolFailure(p)==null;}
 private static String uniformFailure(int program,boolean sparse){
  return uniformFailures(activeUniforms,uniforms(program),rate,world,sparse);
 }
 // The complete GL snapshot is already captured above. Accumulate independent faults only after the first failure.
 static String uniformFailures(Map<String,List<Integer>> expected,Map<String,List<Integer>> observed,boolean rate,boolean world,boolean sparse){
  StringBuilder failures=null;
  for(var e:expected.entrySet()){
   var now=observed.get(e.getKey());
   boolean matches;
   if(RATE_INPUT_UNUSED.contains(e.getKey())){
    matches=rateInputUniformMatches(e.getKey(),rate,world,sparse,e.getValue(),now);
   }else if(e.getKey().equals("shaderlodOpaqueLightingMode")){
    matches=rateLightingUniformMatches(e.getKey(),rate,world,sparse,e.getValue(),now);
   }else{
    boolean absent=List.of(Integer.MIN_VALUE).equals(now);
    matches=absent&&Set.of("nightVision","blindness","darknessFactor","darknessLightFactor").contains(e.getKey())&&(!world||(!sparse&&!e.getKey().equals("darknessFactor")))||
     absent&&sparse&&Set.of("gbufferProjectionInverse","gbufferModelViewInverse","pixelSizeX","pixelSizeY","shaderlodNear","shaderlodFar","shaderlodTransition","shaderlodOpaqueLightingNear","shaderlodOpaqueLightingFar","shaderlodOpaqueLightingTransition","shaderlodOpaqueLightingMediumWorkRatio","shaderlodOpaqueLightingFarWorkRatio").contains(e.getKey())||e.getValue().equals(now);
   }
   if(!matches){if(failures==null)failures=new StringBuilder();else failures.append("; ");failures.append(e.getKey()).append(" expected=").append(e.getValue()).append(" actual=").append(now);}
  }
  return failures==null?null:failures.toString();
 }
 private static boolean sameUniforms(int program,boolean sparse){return uniformFailure(program,sparse)==null;}
 public static Session begin(IrisRenderingPipeline pipe,int nativeProgram,int producerProgram,int nativeFbo,int nativeDepth){finish();if(!requested()||!candidatePack()||Iris.getPipelineManager().getPipelineNullable()!=pipe)return declined("requested/source/context changed before replay");var t=((ShadowPipelineView)pipe).shadowlod$targets();if(t==null||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=nativeFbo||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=nativeProgram||!protocols(nativeProgram))return declined("native output/program/protocol refused");int w=t.getCurrentWidth(),h=t.getCurrentHeight();int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);if(w<2||h<2||w>8192||h>8192||!Arrays.equals(viewport,new int[]{0,0,w,h})||nativeDepth!=t.getDepthTexture()||GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS)<fields().length)return declined("native fullviewport/depth/six MRT limits refused");int frame=integer(nativeProgram,"frameCounter");if(frame<0||frame>16777216)return declined("native frame stamp refused");
  world=worldMode();rate=reducedMode();int[] fields=fields();int[] images=new int[fields.length];for(int i=0;i<fields().length;i++){var sample=BlissSpatialGl.sample(nativeProgram,"colortex"+fields()[i]);if(!field(t,fields()[i],sample)||sample.image().texture()==nativeDepth)return declined("owned RGBA32F field unavailable: "+fields()[i]);images[i]=sample.image().texture();}if(Arrays.stream(images).distinct().count()!=images.length)return declined("private fields alias");
  var atlas=BlissSpatialGl.sample(nativeProgram,"tex");if(atlas==null||atlas.image().texture()<=0||atlas.image().width()<=0||atlas.image().height()<=0||atlas.image().compare()!=GL11.GL_NONE||atlas.image().texture()==nativeDepth)return declined("native atlas binding refused");for(int image:images)if(image==atlas.image().texture())return declined("private field aliases native atlas");
  var m=options();var shadows=shadow(pipe,nativeProgram,"true".equals(m.get("COLORED_SHADOW")));if(shadows==null)return declined("native shadow sampler LOD/ownership refused");if(world){var fog=BlissSpatialGl.sample(nativeProgram,"gaux4");var ownedFog=t.get(7);if(!BlissSpatialGl.lodParity(fog)||ownedFog==null||fog.image().compare()!=GL11.GL_NONE||(fog.image().texture()!=ownedFog.getMainTexture()&&fog.image().texture()!=ownedFog.getAltTexture()))return declined("native world fog sampler owner/LOD refused");for(int image:images)if(image==fog.image().texture())return declined("world fog feedback");for(String scalar:List.of("nightVision","blindness","darknessFactor","darknessLightFactor","rainStrength")){int location=GL20.glGetUniformLocation(nativeProgram,scalar);if(location<0)return declined("native world scalar absent: "+scalar);float value=GL20.glGetUniformf(nativeProgram,location);if(!(value>=0&&value<=1))return declined("native world scalar range: "+scalar);}shadows=new TreeMap<>(shadows);shadows.put("gaux4",fog);shadows=Map.copyOf(shadows);}
  for(var s:shadows.values())for(int image:images)if(image==s.image().texture())return declined("private field aliases native shadow input");
  int cache=images[5];if(rate){var target=t.get(10);images[5]=cache==target.getMainTexture()?target.getAltTexture():target.getMainTexture();if(images[5]<=0||images[5]==cache||images[5]==nativeDepth||images[5]==atlas.image().texture())return declined("native alternate gradient field unavailable/aliases input");for(var input:shadows.values())if(images[5]==input.image().texture())return declined("gradient output aliases native shadow input");}if(Arrays.stream(images).distinct().count()!=images.length)return declined("gradient field aliases another input output");
  for(int attachment=0;attachment<GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);attachment++){int slot=GL30.GL_COLOR_ATTACHMENT0+attachment;if(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL11.GL_TEXTURE)continue;int object=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,slot,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);if(object==cache)return declined("private cache aliases native forward attachment");for(int image:images)if(object==image)return declined("private input aliases native forward attachment");}
  if(fbo==null||fboTargets!=t||!Arrays.equals(fboImages,images)){if(fbo!=null)fbo.destroy();fbo=new GlFramebuffer();for(int i=0;i<images.length;i++)fbo.addColorAttachment(i,images[i]);fbo.addDepthAttachment(nativeDepth);fbo.drawBuffers(java.util.stream.IntStream.range(0,images.length).toArray());fbo.readBuffer(0);if(fbo.getStatus()!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("spatial export framebuffer incomplete");fboTargets=t;fboImages=images.clone();}
  activeNative=nativeProgram;activeOptions=m;activeShadow=shadows;activeAtlas=atlas;activeUniforms=uniforms(nativeProgram);context=new PhotonSpatialEpoch.Context(pipe,Iris.getCurrentPack().orElse(null),t,OptimizationConfig.generation(),frame,w,h,producerProgram,nativeProgram,m);EPOCH.begin(context);return new Session(pipe,t,images,nativeDepth,cache);
 }
 private static boolean outputs(int[] images,int depth){if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)!=depth)return false;for(int i=0;i<images.length;i++){int attachment=GL30.GL_COLOR_ATTACHMENT0+i;if(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i)!=attachment||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)!=GL11.GL_TEXTURE||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)!=0||GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)!=images[i])return false;}return true;}
 // Core GL has one polygon mode and accepts only FRONT_AND_BACK. The second query slot is not a second core face.
 static int[] polygonRestorePlan(boolean core,int front,int back){
  if(!Set.of(GL11.GL_POINT,GL11.GL_LINE,GL11.GL_FILL).contains(front)||
      !core&&!Set.of(GL11.GL_POINT,GL11.GL_LINE,GL11.GL_FILL).contains(back))throw new IllegalStateException("Native polygon mode snapshot is invalid");
  return core||front==back?new int[]{GL11.GL_FRONT_AND_BACK,front}:new int[]{GL11.GL_FRONT,front,GL11.GL_BACK,back};
 }
 static boolean polygonBackupPending(boolean quadEntered,boolean quadCompleted){return quadEntered&&!quadCompleted;}
 public static final class Session{final IrisRenderingPipeline pipe;final RenderTargets targets;final int[] images;final int depth,cache;private boolean returnedValid;private Session(IrisRenderingPipeline p,RenderTargets t,int[] i,int d,int c){pipe=p;targets=t;images=i;depth=d;cache=c;}
  public boolean prepare(int program){for(int field:fields())if(GL20.glGetUniformLocation(program,"colortex"+field)>=0){refuse("producer retained attached-field sampler");return false;}if(context==null){refuse("producer context absent");return false;}
   if(!current()){refuse("producer current world/source/options/owner mismatch");return false;}
   int bound=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(bound!=program){refuse("producer current program mismatch: expected="+program+" actual="+bound);return false;}
   String protocol=protocolFailure(program);if(protocol!=null){refuse("producer protocol mismatch: "+protocol);return false;}
   int frame=integer(program,"frameCounter");if(frame!=context.frame()){refuse("producer frame mismatch: expected="+context.frame()+" actual="+frame);return false;}
   String uniform=uniformFailure(program,false);if(uniform!=null){refuse("producer uniform mismatch: "+uniform);return false;}
   var atlas=BlissSpatialGl.sample(program,"tex");if(!Objects.equals(activeAtlas,atlas)){refuse("producer native atlas mismatch: expected="+activeAtlas+" actual="+atlas);return false;}
   if((!rate||world)&&!sameStageInputs(program,true)){refuse("producer native shadow/fog inputs mismatch");return false;}fbo.bind();if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=fbo.getId()||!outputs(images,depth)){refuse("owned producer output attachments changed");return false;}boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);try{if(scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);for(int i=0;i<images.length;i++)GL30.glClearBufferfv(GL11.GL_COLOR,i,new float[]{0,0,0,-1});}finally{if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);}ready(program,true,targets.getCurrentWidth(),targets.getCurrentHeight());return true;}
  public boolean returned(int draws){returnedValid=false;if(draws<=0){failure="no native SOLID submissions; full native retained";return false;}int producer=context.producer();boolean valid=current()&&integer(producer,"frameCounter")==context.frame()&&sameUniforms(producer,false)&&Objects.equals(activeAtlas,BlissSpatialGl.sample(producer,"tex"))&&GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==producer&&GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==fbo.getId()&&outputs(images,depth)&& ((rate&&!world)||sameStageInputs(producer,true));if(!valid){refuse("returned input/lighting replay changed owner/sampler/context");return false;}returnedValid=true;if(rate)inputReturns++;else returns++;return true;}
  public boolean renderRate(net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface> lighting){
   if(!rate)return true;
   if(!returnedValid){refuse("rate missing completed native input acknowledgment");returnedValid=false;return false;}
   if(!current()){refuse("rate completed input current owner/source/options/context changed");returnedValid=false;return false;}
   if(lighting==null){refuse("rate linked native lighting program absent");returnedValid=false;return false;}
   returnedValid=false;int p=lighting.handle(),w=context.width(),h=context.height();int[] reduced=reducedSize(w,h,world);
   if(!com.pp.shaderlod.optimization.blissspatial.BlissSpatialCull.allowed(GL11.glIsEnabled(GL11.GL_CULL_FACE),GL11.glGetInteger(GL11.GL_CULL_FACE_MODE),GL11.glGetInteger(GL11.GL_FRONT_FACE),com.pp.shaderlod.optimization.blissspatial.BlissSpatialCull.exact())){refuse("native rate fullscreen culling refused");return false;}
   if(rateFbo==null||rateCache!=cache){if(rateFbo!=null)rateFbo.destroy();rateFbo=new GlFramebuffer();rateFbo.addColorAttachment(0,cache);rateFbo.drawBuffers(new int[]{0});rateFbo.readBuffer(0);rateCache=cache;}if(rateFbo.getStatus()!=GL30.GL_FRAMEBUFFER_COMPLETE){refuse("native rate output framebuffer incomplete");return false;}
   int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);int[] polygon=new int[2];GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,polygon);boolean corePolygon=(GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)&GL32.GL_CONTEXT_CORE_PROFILE_BIT)!=0;int[] polygonRestore=polygonRestorePlan(corePolygon,polygon[0],polygon[1]);int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);boolean scissorBefore=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);boolean quad=false,quadDrawEntered=false,quadDrawCompleted=false;var shader=lighting.getInterface();lighting.bind();boolean setup=true;
   try{shader.setupState();
    int bound=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(bound!=p){refuse("rate lighting current program mismatch: expected="+p+" actual="+bound);return false;}
    int linked=GL20.glGetProgrami(p,GL20.GL_LINK_STATUS);if(linked!=GL11.GL_TRUE){refuse("rate lighting link refused: status="+linked);return false;}
    String protocol=protocolFailure(p);if(protocol!=null){refuse("rate lighting protocol mismatch: "+protocol);return false;}
    int frame=integer(p,"frameCounter");if(frame!=context.frame()){refuse("rate lighting frame mismatch: expected="+context.frame()+" actual="+frame);return false;}
    String uniform=uniformFailure(p,true);if(uniform!=null){refuse("rate lighting uniform mismatch: "+uniform);return false;}
    String stage=stageInputFailure(p,false);if(stage!=null){refuse("rate lighting shadow/source input mismatch: "+stage);return false;}
    for(int i=0;i<images.length;i++){if(i==5)continue;var image=BlissSpatialGl.sample(p,"colortex"+fields()[i]);if(!field(targets,fields()[i],image)||image.image().texture()!=images[i]){refuse("native rate input owner changed: "+fields()[i]);return false;}}
    int gradient=integer(p,"colortex10");if(gradient>=0){net.irisshaders.iris.gl.IrisRenderSystem.bindTextureToUnit(GL11.GL_TEXTURE_2D,gradient,images[5]);var value=BlissSpatialGl.sample(p,"colortex10");if(!field(targets,10,value)||value.image().texture()!=images[5]){refuse("native captured derivative input unavailable");return false;}}
    // Reuse the existing normal native sampler supplier before changing only this owned viewport.
    com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.draw();ready(p,true,w,h);rateFbo.bind();boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);try{if(scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0,0,-1});}finally{if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);}
    GL11.glViewport(0,0,reduced[0],reduced[1]);if(scissorBefore)GL11.glDisable(GL11.GL_SCISSOR_TEST);quad=true;net.irisshaders.iris.pathways.FullScreenQuadRenderer.INSTANCE.begin();quadDrawEntered=true;net.irisshaders.iris.pathways.FullScreenQuadRenderer.INSTANCE.renderQuad();quadDrawCompleted=true;com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.assertNoGlError("native half-height rate draw");
    int[] observed=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,observed);if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=p||!Arrays.equals(observed,new int[]{0,0,reduced[0],reduced[1]})||!current()||integer(p,"frameCounter")!=context.frame()||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=rateFbo.getId()||!outputs(new int[]{cache},0)||!sameStageInputs(p,false)){refuse("returned native rate output/epoch/viewport changed");return false;}returnedValid=true;rateReturns++;returns++;return true;
   }finally{try{if(quad)net.irisshaders.iris.pathways.FullScreenQuadRenderer.INSTANCE.end();}finally{try{try{if(polygonBackupPending(quadDrawEntered,quadDrawCompleted))net.irisshaders.iris.gl.IrisRenderSystem.restorePolygonMode();}finally{try{GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,array);}finally{try{for(int i=0;i<polygonRestore.length;i+=2)GL11.glPolygonMode(polygonRestore[i],polygonRestore[i+1]);}finally{GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);if(scissorBefore)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);ready(p,false,0,0);}}}}finally{if(setup)shader.resetState();}}}
  }
  public void commit(int draws){if(draws<=0||!returnedValid)return;if(!current()||integer(context.consumer(),"frameCounter")!=context.frame()||!activeUniforms.equals(uniforms(context.consumer()))||!Objects.equals(activeAtlas,BlissSpatialGl.sample(context.consumer(),"tex"))||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=context.consumer()||!(world?consumerInputs(context.consumer()):sameStageInputs(context.consumer(),false))){refuse("native forward consumer changed after restoration");return;}for(int i=0;i<fields().length;i++){var s=BlissSpatialGl.sample(context.consumer(),"colortex"+fields()[i]);if(!field(targets,fields()[i],s)||s.image().texture()!=(i==5?cache:images[i])){refuse("native forward input fields changed after restore");return;}}EPOCH.returned(context,new PhotonSpatialEpoch.Output(cache,cache,rate?rateFbo.getId():fbo.getId()),true,true);if(!EPOCH.consumer(context,cache,cache)){refuse("sameframe completion refused");return;}ready(context.consumer(),true,targets.getCurrentWidth(),targets.getCurrentHeight());consumers++;failure="native input+representative replay returned and restored; original forward consumer ready";}
 }
 private static boolean consumerInputs(int p){var observed=shadow((IrisRenderingPipeline)context.pipeline(),p,"true".equals(activeOptions.get("COLORED_SHADOW")));if(observed==null)return false;var all=new TreeMap<>(observed);all.put("gaux4",BlissSpatialGl.sample(p,"gaux4"));return activeShadow.equals(all);}
 private static boolean current(){return context!=null&&requested()&&environment()&&context.pipeline()==Iris.getPipelineManager().getPipelineNullable()&&context.pack()==Iris.getCurrentPack().orElse(null)&&context.generation()==OptimizationConfig.generation()&&context.options().equals(options())&&context.width()==fboTargets.getCurrentWidth()&&context.height()==fboTargets.getCurrentHeight();}
 public static net.irisshaders.iris.pipeline.WorldRenderingPipeline recover(net.irisshaders.iris.shaderpack.materialmap.NamespacedId dimension){
  if(!pending||recovering)return null;var m=options();
  if(!"1".equals(m.get(MakeUpSpatialContract.KEY))&&!"1".equals(m.get(MakeUpSpatialContract.RATE))&&!"1".equals(m.get(MakeUpSpatialContract.WORLD))){pending=false;FALLBACK.clear();return null;}
  recovering=true;
  try{
   var oldPack=Iris.getCurrentPack().orElse(null);var oldPipeline=Iris.getPipelineManager().getPipelineNullable();
   var client=net.minecraft.client.Minecraft.getInstance();var ownedWorld=client.level;long generation=OptimizationConfig.generation();
   String selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
   String reason=firstFailureGeneration==generation&&!firstFailure.equals("none")?firstFailure:failure;
   var queue=Iris.getShaderPackOptionQueue();var before=Map.copyOf(queue);FALLBACK.clear();
   queue.clear();queue.put(MakeUpSpatialContract.KEY,"0");queue.put(MakeUpSpatialContract.RATE,"0");queue.put(MakeUpSpatialContract.WORLD,"0");
   try{Iris.reload();}finally{queue.remove(MakeUpSpatialContract.KEY,"0");queue.remove(MakeUpSpatialContract.RATE,"0");queue.remove(MakeUpSpatialContract.WORLD,"0");before.forEach((k,v)->{if(!k.equals(MakeUpSpatialContract.KEY)&&!k.equals(MakeUpSpatialContract.RATE)&&!k.equals(MakeUpSpatialContract.WORLD))queue.putIfAbsent(k,v);});}
   var actual=options();if(!"0".equals(actual.get(MakeUpSpatialContract.KEY))||!"0".equals(actual.get(MakeUpSpatialContract.RATE))||!"0".equals(actual.get(MakeUpSpatialContract.WORLD)))throw new IllegalStateException("native MakeUp graph0 recovery refused");
   pending=false;var restored=Iris.getPipelineManager().preparePipeline(dimension);
   if(generation==OptimizationConfig.generation()&&ownedWorld==client.level&&MakeUpSpatialContract.profile(m)&&MakeUpSpatialContract.profile(actual))
    FALLBACK.publish(new MakeUpSpatialFallback.Receipt(oldPack,Iris.getCurrentPack().orElse(null),oldPipeline,restored,ownedWorld,dimension,generation,
       selected,Iris.getIrisConfig().getShaderPackName().orElse(null),m,actual,before.isEmpty()&&queue.isEmpty(),reason));
   return restored;
  }catch(Exception e){throw new IllegalStateException("native MakeUp spatial recovery failed",e);}finally{recovering=false;}
 }

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source();}catch(Exception|LinkageError refused){}}
}
