package com.pp.shaderlod.optimization.makeupearlyalpha;
import java.util.*;import net.irisshaders.iris.Iris;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import net.irisshaders.iris.shaderpack.ShaderPack;import net.irisshaders.iris.shaderpack.programs.ProgramSource;import net.irisshaders.iris.gl.blending.AlphaTest;import net.irisshaders.iris.pipeline.programs.SodiumPrograms;import org.lwjgl.opengl.*;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.integration.makeupquad.*;import com.pp.shaderlod.optimization.makeupao.*;import com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView;
public final class MakeUpEarlyAlphaRuntime{private MakeUpEarlyAlphaRuntime(){}private record Owner(Object pipeline,ShaderPack pack,long epoch,MakeUpCompiledSource.Certificate certificate,net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> linkedProgram,Map<String,Integer> locations,GLCapabilities context,int maxTextureUnits,boolean shaderUniformGuard){}private static final Map<ProgramSource,Boolean> PREPARED=new WeakHashMap<>();private static final Map<Integer,Owner> OWNERS=new HashMap<>();private static long epoch;private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();private static boolean ready;private static String failure="not consumed";private static long observedDraws,requestedDraws,qualifiedDraws,compiledProofDraws,legacyProofDraws;public static long epoch(){return epoch;}public static String status(){return MakeUpPostSampleAlpha.status()+"; MakeUp native CUTOUT earlyalpha; owned="+OWNERS.size()+"; last="+failure+"; cumulative draws="+observedDraws+", requested="+requestedDraws+", qualified="+qualifiedDraws+", compiledProof="+compiledProofDraws+", legacyProof="+legacyProofDraws+"; compile="+MakeUpCompiledPrograms.status();}public static void invalidate(){MakeUpPostSampleAlphaWitness.invalidate();MakeUpPostSampleAlpha.invalidate();com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.clear();SOURCE_CACHE.invalidate();MakeUpCompiledPrograms.clear();epoch++;PREPARED.clear();OWNERS.clear();ready=false;}public static void destroy(Object p){MakeUpPostSampleAlphaWitness.destroy(p);MakeUpPostSampleAlpha.invalidate();com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.destroy(p);SOURCE_CACHE.invalidate();MakeUpCompiledPrograms.clear();OWNERS.entrySet().removeIf(e->e.getValue().pipeline()==p);ready=false;}public static void prepared(ProgramSource s,boolean valid){if(s!=null)PREPARED.put(s,valid);}public static boolean profile(Map<String,String> m){if(MakeUpEarlyAlphaLeanGraph.profile(m))return true;m=com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.withoutInactiveControl(m);if(m==null||m.size()!=111)return false;var n=new TreeMap<>(m);if(!Set.of("true","false").contains(m.get("COLORED_SHADOW")))return false;n.put("COLORED_SHADOW","true");return MakeUpAoContract.options(n);}public static boolean source(ShaderPack p){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(MakeUpEarlyAlphaRuntime.class,"source",p),()->{if(p==null){SOURCE_CACHE.invalidate();return false;}try{var o=p.getShaderPackOptions();if(!MakeUpAoOptionGuard.declaration(o.getOptionSet())){SOURCE_CACHE.invalidate();return false;}var set=o.getOptionSet();var v=o.getOptionValues();var options=new TreeMap<String,String>();for(var k:set.getBooleanOptions().keySet())options.put(k,Boolean.toString(v.getBooleanValueOrDefault(k)));for(var k:set.getStringOptions().keySet())options.put(k,v.getStringValueOrDefault(k));if(!profile(options)){SOURCE_CACHE.invalidate();return false;}var client=net.minecraft.client.Minecraft.getInstance();return SOURCE_CACHE.check(MakeUpEarlyAlphaRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),"makeup-ultra-fast-shaders",options,p,v,set,Iris.getPipelineManager().getPipelineNullable(),client.level,epoch,java.util.Arrays.asList(OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue())),client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),()->sourceUncached(p));}catch(Exception|LinkageError failure){SOURCE_CACHE.invalidate();return false;}
  });
 }
private static boolean sourceUncached(ShaderPack p){if(p==null)return false;var o=p.getShaderPackOptions();if(!MakeUpAoOptionGuard.declaration(o.getOptionSet()))return false;var m=MakeUpAoOptionGuard.snapshot(o.getOptionSet(),o.getOptionValues(),true);if(!profile(m))return false;try{String n=Iris.getIrisConfig().getShaderPackName().orElse(null);var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();if(n==null||!java.nio.file.Path.of(n).getFileName().toString().equals(n))return false;try(var in=MakeUpEarlyAlphaRuntime.class.getClassLoader().getResourceAsStream("assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")){if(in==null||!MakeUpAoSourceProof.RECIPE_SHA.equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;}return "makeup-ultra-fast-shaders".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(n),m));}catch(Exception|LinkageError e){return false;}}
public static void own(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,int id){own(p,pass,s,id,null);}
public static void own(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,int id,MakeUpCompiledSource.Certificate certificate){ownChecked(p,pass,s,id,certificate,null);}
/** Native Sodium builder returns this object only after a successful link. Its
 * handle has no relink setter and is invalidated on delete. Keep the actual
 * object, not just a recyclable GL integer, for the exact singleton route. */
public static void ownLinked(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> program,MakeUpCompiledSource.Certificate certificate){
 if(program!=null)ownChecked(p,pass,s,program.handle(),certificate,program);
}
private static void ownChecked(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,int id,MakeUpCompiledSource.Certificate certificate,net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> linkedProgram){
 if(id>0)OWNERS.remove(id);
 if(p!=null&&s!=null&&s.getParent()!=null&&MakeUpEarlyAlphaCompilation.enabled(s,s.getParent())&&"gbuffers_terrain".equals(s.getName())&&pass==SodiumPrograms.Pass.TERRAIN_CUTOUT&&Boolean.TRUE.equals(PREPARED.get(s))&&id>0)
  { // This implementation limit is immutable within its owning GL context.
   var context=GL.getCapabilities();int units=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
   var checked=certificate!=null&&certificate.source()==s&&certificate.epoch()==epoch?certificate:null;
   boolean shaderGuard=checked!=null&&MakeUpEarlyAlphaLeanGraph.profile(checked.input().options().values());
   net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> immutableLink=null;
   if(shaderGuard&&linkedProgram!=null&&linkedProgram.getClass()==net.caffeinemc.mods.sodium.client.gl.shader.GlProgram.class){
    if(linkedProgram.handle()!=id||GL20.glGetProgrami(id,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)return;
    immutableLink=linkedProgram;
   }
   if(units>0)OWNERS.put(id,new Owner(p,s.getParent().getPack(),epoch,checked,immutableLink,locations(id),context,units,shaderGuard));
  }
}
private static boolean sourceForDraw(Owner owner,ShaderPack pack){
 var certificate=owner.certificate();
 if(certificate!=null&&!certificate.current(pack,Iris.getIrisConfig().getShaderPackName().orElse(null),epoch))return false;
 if(certificate!=null){compiledProofDraws++;return true;}
 legacyProofDraws++;return source(pack);
}
/** Derived registration caller first proves its own linked vertex and exact shared native fragment. Never copies readiness. */
public static boolean ownsDerived(Object pipeline,Object pack,int id){var o=OWNERS.get(id);return id>0&&pipeline!=null&&pack!=null&&o!=null&&o.pipeline()==pipeline&&o.pack()==pack&&o.epoch()==epoch;}
public static void forgetDerived(int id){OWNERS.remove(id);}

/** Uniform locations belong to one successful link; owner removal also removes these locations. */
private static Map<String,Integer> locations(int program){
 var values=new HashMap<String,Integer>();
 for(String name:List.of("shaderlodMakeupEarlyAlphaPermission","shaderlodBridgeVersion","shaderlodEnabled","shaderlodOptimizationFlagsVersion","shaderlodOptEarlyAlpha","shaderlodPreset","iris_currentAlphaTest","shaderlodMakeupEarlyAlphaReference","shadowtex1","shadowtex0","shadowcolor0","gaux4"))
  values.put(name,com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,name));
 return Map.copyOf(values);
}
private static int location(int program,String name){
 var owner=OWNERS.get(program);var cached=owner==null?null:owner.locations().get(name);
 return cached==null?com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,name):cached;
}
private static int integer(int p,String n){int l=location(p,n);return l<0?-1:GL20.glGetUniformi(p,l);}private static int param(int s,int n){return s==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,n):GL33.glGetSamplerParameteri(s,n);}private static long samplerCalls,samplerElapsedNanos,linkedLifetimeDraws;
private static final boolean FULL_COST=Boolean.getBoolean("shaderlod.dev.earlyAlphaCpuCost");
private static long fullDrawCalls,fullDrawElapsedNanos;
public static Map<String,Long> costCounters(){return Map.of("full_draw_calls",fullDrawCalls,"full_draw_elapsed_ns",fullDrawElapsedNanos,"sampler_calls",samplerCalls,"sampler_elapsed_ns",samplerElapsedNanos,"qualified_draws",qualifiedDraws,"compiled_proof_draws",compiledProofDraws,"legacy_proof_draws",legacyProofDraws,"linked_lifetime_draws",linkedLifetimeDraws);}
private static MakeUpEarlyAlphaContract.Sampler sample(int p,String n,int maxUnits){if(!FULL_COST)return sampleLive(p,n,maxUnits);long at=System.nanoTime();samplerCalls++;try{return sampleLive(p,n,maxUnits);}finally{samplerElapsedNanos+=System.nanoTime()-at;}}
private static MakeUpEarlyAlphaContract.Sampler sampleLive(int p,String n,int maxUnits){int u=integer(p,n);if(u<0||u>=maxUnits)return null;GL13.glActiveTexture(GL13.GL_TEXTURE0+u);int t=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),s=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var c=GL.getCapabilities();float a=(c.GL_EXT_texture_filter_anisotropic||c.GL_ARB_texture_filter_anisotropic)?(s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(s,0x84FE)):1;float bias=s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_LOD_BIAS):GL33.glGetSamplerParameterf(s,GL14.GL_TEXTURE_LOD_BIAS);return new MakeUpEarlyAlphaContract.Sampler(t,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),param(s,GL11.GL_TEXTURE_MIN_FILTER),param(s,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),param(s,GL14.GL_TEXTURE_COMPARE_MODE),param(s,GL14.GL_TEXTURE_COMPARE_FUNC),bias,s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MIN_LOD):GL33.glGetSamplerParameterf(s,GL12.GL_TEXTURE_MIN_LOD),s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD):GL33.glGetSamplerParameterf(s,GL12.GL_TEXTURE_MAX_LOD),a);}
public static boolean inactive(){return com.pp.shaderlod.optimization.sourcecache.InactiveDrawElision.elide(OWNERS.isEmpty()&&!MakeUpPostSampleAlphaWitness.observing());}
public static void revoke(){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p>0){int l=location(p,"shaderlodMakeupEarlyAlphaPermission");if(l>=0)GL20.glUniform1i(l,0);}}public static void draw() {
 if(!FULL_COST){drawChecked();return;}
 long at=System.nanoTime();try{drawChecked();}finally{fullDrawCalls++;fullDrawElapsedNanos+=System.nanoTime()-at;}
}
private static void drawChecked() {
 int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
 if(program<=0)return;
 int permission=location(program,"shaderlodMakeupEarlyAlphaPermission");
 if(permission<0)return;
 GL20.glUniform1i(permission,0);
 observedDraws++;
 try {
  var client=net.minecraft.client.Minecraft.getInstance();
  var core=OptimizationRuntimeContext.core();
  if(core==null){failure="core unavailable";return;}
  if(!core.settings().enabled()){failure="settings disabled";return;}
  if(core.preset()==LodPreset.QUALITY){failure="native Quality";return;}
  if(!core.optimizations().enabled(OptimizationId.EARLY_ALPHA)||!OptimizationConfig.requested(OptimizationId.EARLY_ALPHA)){failure="request Off";return;}
  requestedDraws++;
  if(MakeUpQuadLifecycle.suspended()){failure="settings Apply suspended";return;}
  if(!com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin.exactApi()){failure="API pin mismatch";return;}
  if(Iris.isFallback()){failure="Iris fallback";return;}
  if(!Iris.getShaderPackOptionQueue().isEmpty()){failure="native option queue pending";return;}
  if(client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD){failure="non-Overworld scene";return;}
  if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()){failure="not render thread";return;}
  var pipeline=Iris.getPipelineManager().getPipelineNullable();
  var pack=Iris.getCurrentPack().orElse(null);
  var owner=OWNERS.get(program);
  if(owner==null||owner.pipeline()!=pipeline||owner.pack()!=pack||owner.epoch()!=epoch||owner.context()!=GL.getCapabilities()||!(pipeline instanceof IrisRenderingPipeline)){failure="linked program owner mismatch";return;}
  
  if(!MakeUpQuadRuntime.loadedStackMatches()){failure="runtime stack mismatch";return;}
  if(!MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds())){failure="resource pack mismatch";return;}
  if(net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()){failure="shadow pass";return;}
  ready=sourceForDraw(owner,pack);
  if(!ready){failure="generated source/profile proof refused";return;}
  if(owner.linkedProgram()!=null){
   if(owner.linkedProgram().handle()!=program){failure="linked program object retired";return;}
   linkedLifetimeDraws++;
  }else if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE){failure="program link unavailable";return;}
  // The exact certified singleton shader gates every discard and explicit-LOD
  // query on these same five uniforms. Re-reading them on the CPU cannot make
  // that shader gate safer. Keep native alpha/sampler/coverage/owner checks live.
  // Legacy graphs retain their previous CPU validation.
  if(!owner.shaderUniformGuard()) {
   int bridge=integer(program,"shaderlodBridgeVersion"),enabled=integer(program,"shaderlodEnabled"),version=integer(program,"shaderlodOptimizationFlagsVersion"),flag=integer(program,"shaderlodOptEarlyAlpha"),preset=integer(program,"shaderlodPreset");
   if(bridge!=2||enabled!=1||version!=1||flag!=1||!Set.of(0,1).contains(preset)){failure="linked uniform contract: bridge="+bridge+", enabled="+enabled+", flagsVersion="+version+", earlyAlpha="+flag+", preset="+preset;return;}
  }
  int alpha=location(program,"iris_currentAlphaTest"),reference=location(program,"shaderlodMakeupEarlyAlphaReference");
  if(alpha<0||reference<0){failure="native/private alpha reference missing";return;}
  float nativeReference=GL20.glGetUniformf(program,alpha);
  if(!MakeUpEarlyAlphaContract.alpha(nativeReference)){failure="native alpha reference invalid";return;}
  if(GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE)||GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_ONE)||GL11.glIsEnabled(GL13.GL_SAMPLE_COVERAGE)||GL11.glIsEnabled(GL32.GL_SAMPLE_MASK)){failure="sample coverage active";return;}
  var view=(ShadowPipelineView)pipeline;
  var shadows=view.shadowlod$shadows();var targets=view.shadowlod$targets();
  if(shadows==null||targets==null){failure="native shadow/render targets unavailable";return;}
  int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
  if(!Arrays.equals(viewport,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()})){failure="native viewport mismatch: "+Arrays.toString(viewport)+" target="+targets.getCurrentWidth()+"x"+targets.getCurrentHeight();return;}
  // One synchronous validation batch; no state survives a draw or context mutation.
  int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
  int maxUnits=owner.maxTextureUnits();
  try {
  var shadow1=sample(program,"shadowtex1",maxUnits);
  if(!MakeUpEarlyAlphaContract.sampler(shadow1,shadows.getDepthTextureNoTranslucents().getTextureId(),true)){failure="shadowtex1 sampler refused: "+shadow1;return;}
  var declared=pack.getShaderPackOptions();boolean coloredShadow=declared.getOptionValues().getBooleanValueOrDefault("COLORED_SHADOW");
  int color=integer(program,"shadowcolor0");
  if(coloredShadow&&color<0){failure="colored shadow sampler absent";return;}
  if(color>=0) {
   var shadow0=sample(program,"shadowtex0",maxUnits);var shadowColor=sample(program,"shadowcolor0",maxUnits);
   if(!MakeUpEarlyAlphaContract.sampler(shadow0,shadows.getDepthTexture().getTextureId(),true)||!MakeUpEarlyAlphaContract.sampler(shadowColor,shadows.getColorTextureId(0),false)){failure="colored shadow sampler refused: depth="+shadow0+", color="+shadowColor;return;}
  }
  int fog=integer(program,"gaux4");
  if(fog<0){failure="native fog sampler absent";return;}
  var fogTarget=targets.get(7);var fogSampler=sample(program,"gaux4",maxUnits);
  if(fogTarget==null||fogSampler==null||(!MakeUpEarlyAlphaContract.sampler(fogSampler,fogTarget.getMainTexture(),false)&&!MakeUpEarlyAlphaContract.sampler(fogSampler,fogTarget.getAltTexture(),false))){failure="native fog sampler refused: "+fogSampler;return;}
  } finally { GL13.glActiveTexture(activeTexture); }
  GL20.glUniform1f(reference,nativeReference);GL20.glUniform1i(permission,1);
  qualifiedDraws++;failure="qualified ownedCUTOUT/native alphaRef/samplers";
 }catch(RuntimeException|LinkageError rejected){GL20.glUniform1i(permission,0);failure="runtime exception: "+rejected.getClass().getSimpleName();}
}

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{source(pack);}catch(Exception|LinkageError refused){}}
}
