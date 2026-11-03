package com.pp.shaderlod.optimization.makeupshadow;
import java.util.*;import com.pp.shaderlod.optimization.sourcecache.*;import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;import net.irisshaders.iris.gl.blending.AlphaTest;import net.irisshaders.iris.pipeline.programs.SodiumPrograms;import net.irisshaders.iris.shaderpack.programs.ProgramSource;import net.irisshaders.iris.Iris;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import net.irisshaders.iris.shadows.ShadowRenderTargets;import net.irisshaders.iris.platform.IrisPlatformHelpers;import net.irisshaders.iris.shaderpack.ShaderPack;import org.lwjgl.opengl.*;
import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle;import com.pp.shaderlod.optimization.makeupao.*;import com.pp.shaderlod.optimization.bsl.BslReceiverContract;
import com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView;
/** Exact owned native terrain programs; sampler permission uploaded only at native draw boundaries. */
public final class MakeUpShadowRuntime {
 private record Owner(Object pipeline,Object pack,long epoch){}private static final Map<Integer,Owner> OWNERS=new HashMap<>();private static long epoch;private static Object sourcePack;private static Map<String,String> sourceOptions=Map.of();private static boolean sourceReady;private static String failure="not consumed";
 private record Certified(Object pipeline,ShaderPack pack,long epoch,IrisCompiledSource.Certificate source,boolean colored,SodiumPrograms.Pass pass,boolean scopeProfile) {}
 private static final LinkedProgramLocations<GlProgram<?>,Certified> COMPILED=new LinkedProgramLocations<>(GlProgram::handle);
 private static long compiledProofDraws,legacyProofDraws,qualifiedDraws,qualifiedSolidDraws,qualifiedCutoutDraws;
 public static long epoch(){return epoch;}
 private static LinkedProgramLocations.Entry<GlProgram<?>,Certified> linkedOwner(int id){
  boolean present=COMPILED.contains(id);var linked=COMPILED.current(id);
  if(present&&linked==null)OWNERS.remove(id);
  return linked;
 }
 private static int location(int program,String name){var linked=linkedOwner(program);return linked==null?com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,name):linked.locations().getOrDefault(name,-1);}
 private static Map<String,Integer> locations(int program){
  var names=new HashSet<>(List.of("shaderlodMakeupShadowSamplerPermission","shaderlodMakeupColoredShadowBudgetPermission","shaderlodBridgeVersion","shaderlodEnabled","shaderlodOptimizationFlagsVersion","shaderlodPreset","shadowtex0","shadowtex1","shadowcolor0"));
  for(var id:SHADOW_CONTROLS)names.add(id.uniform());
  var locations=new HashMap<String,Integer>();for(var name:names)locations.put(name,com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,name));return Map.copyOf(locations);
 }
 /** Native return hooks can run in either order; only this same program object preserves proof. */
 public static void ownNative(IrisRenderingPipeline pipeline,ShaderPack pack,String role,GlProgram<?> program){
  if(program==null)return;int id=program.handle();COMPILED.registerNative(OWNERS,id,program,context(pipeline,pack,role,id));
 }
 public static void ownCompiled(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,GlProgram<?> program,AlphaTest alpha,IrisCompiledSource.Certificate proof){
  if(program==null)return;int id=program.handle();var pack=source==null||source.getParent()==null?null:source.getParent().getPack();
  COMPILED.registerUncertified(OWNERS,id,context(pipeline,pack,source==null?null:source.getName(),id));
  if(proof==null||source==null||source.getParent()==null||pipeline==null||!"gbuffers_terrain".equals(source.getName())
    ||!MakeUpShadowCompiledPrograms.acceptsPass(pass,alpha)||!MakeUpShadowPins.exact())return;
  if(proof.source()!=source||!proof.current(pack,Iris.getIrisConfig().getShaderPackName().orElse(null),epoch))return;
  var values=options(pack);boolean colored=coloredBudgetProfile(values);if(!colored&&!profile(values))return;
  COMPILED.put(id,program,new Certified(pipeline,pack,epoch,proof,colored,pass,MakeUpShadowSamplerPass.compiledProfile(proof.input().options().values())),locations(id));
 }
 private static final OptimizationId[] SHADOW_CONTROLS = {
     OptimizationId.SHADOW_RECEIVER_FOOTPRINT,
     OptimizationId.TEMPORAL_SHADOW_FILTER,
     OptimizationId.SHADOW_SAMPLE_BUDGET
 };
 private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
 private static final boolean COLORED_PROOF="1".equals(System.getenv("SHADERLOD_MAKEUP_COLORED_SHADOW_PROOF"));
 private static long proofAttempts,proofGrants,proofRevokes;
 private static long sourceLookups,sourceValidatorCalls;
 private static int proofProgram,proofGrantCurrent,proofRevokeCurrent,proofGrantColored=-1,proofGrantShared=-1,proofRevokeColored=-1,proofRevokeShared=-1;
 private static Map<String,Object> proofSamplers=Map.of();private static String proofRefusal="not reached";
 public static Map<String,Object> controlProof(){return Map.ofEntries(Map.entry("sampler_captures",MakeUpShadowSamplerPass.captures()),Map.entry("sampler_reuses",MakeUpShadowSamplerPass.reuses()),Map.entry("sampler_scope_active",MakeUpShadowSamplerPass.active()),Map.entry("sampler_scope_api",MakeUpShadowScopePins.status()),Map.entry("enabled",COLORED_PROOF),Map.entry("compiled_proof_draws",compiledProofDraws),Map.entry("legacy_proof_draws",legacyProofDraws),Map.entry("qualified_draws",qualifiedDraws),Map.entry("qualified_solid",qualifiedSolidDraws),Map.entry("qualified_cutout",qualifiedCutoutDraws),Map.entry("source_lookups",sourceLookups),Map.entry("source_validator_calls",sourceValidatorCalls),Map.entry("sampler_attempts",proofAttempts),Map.entry("grant_entries",proofGrants),Map.entry("revoke_entries",proofRevokes),Map.entry("last_program",proofProgram),Map.entry("grant_current_program",proofGrantCurrent),Map.entry("revoke_current_program",proofRevokeCurrent),Map.entry("grant_colored_permission",proofGrantColored),Map.entry("grant_shared_permission",proofGrantShared),Map.entry("revoke_colored_permission",proofRevokeColored),Map.entry("revoke_shared_permission",proofRevokeShared),Map.entry("samplers",proofSamplers),Map.entry("refusal",proofRefusal),Map.entry("missing_uniform_policy","-1 means inactive/missing, never measured zero"));}
 private static String samplerProof(MakeUpColoredShadowContract.Sampler sampler){return sampler==null?"unavailable":sampler.toString();}
 private MakeUpShadowRuntime(){}
 /** The sampler contract is shared, but none of its three consumers depends on another switch. */
 private static boolean requested(com.pp.shaderlod.CoreUniformFrame.Core core) {
     for (var id : SHADOW_CONTROLS)
         if (core.optimizations().enabled(id) && OptimizationConfig.requested(id)) return true;
     return false;
 }
 private static boolean uploaded(com.pp.shaderlod.CoreUniformFrame.Core core, int program) {
     for (var id : SHADOW_CONTROLS)
         if (core.optimizations().enabled(id) && OptimizationConfig.requested(id)
             && integer(program, id.uniform()) == 1) return true;
     return false;
 }
 public static void invalidate(){MakeUpShadowSamplerPass.invalidate();COMPILED.clear();MakeUpShadowCompiledPrograms.clear();SOURCE_CACHE.invalidate();MakeUpCloudRuntime.invalidate();MakeUpReflectionRuntime.invalidate();epoch++;OWNERS.clear();sourcePack=null;sourceOptions=Map.of();sourceReady=false;}
 public static void destroy(Object pipeline){MakeUpShadowSamplerPass.invalidate();COMPILED.removeIf(c->c.pipeline()==pipeline);MakeUpShadowCompiledPrograms.clear();SOURCE_CACHE.invalidate();MakeUpCloudRuntime.destroy(pipeline);MakeUpReflectionRuntime.destroy(pipeline);OWNERS.entrySet().removeIf(e->e.getValue().pipeline()==pipeline);frame();}
 public static void frame(){MakeUpCloudRuntime.frame();MakeUpReflectionRuntime.frame();sourcePack=null;sourceReady=false;sourceOptions=Map.of();}
 public static void own(IrisRenderingPipeline pipeline,ShaderPack pack,String role,int id){COMPILED.registerUncertified(OWNERS,id,context(pipeline,pack,role,id));}
 private static Owner context(IrisRenderingPipeline pipeline,ShaderPack pack,String role,int id){MakeUpCloudRuntime.own(pipeline,pack,role,id);MakeUpReflectionRuntime.own(pipeline,pack,role,id);return pipeline!=null&&pack!=null&&role!=null&&role.startsWith("gbuffers_terrain")&&id>0?new Owner(pipeline,pack,epoch):null;}
/** Derived registration caller first proves its own linked vertex and exact shared native fragment. Never copies readiness. */
public static boolean ownsDerived(Object pipeline,Object pack,int id){var o=OWNERS.get(id);return id>0&&pipeline!=null&&pack!=null&&o!=null&&o.pipeline()==pipeline&&o.pack()==pack&&o.epoch()==epoch;}
public static void forgetDerived(int id){COMPILED.forget(id);OWNERS.remove(id);}

 public static String status(){return "MakeUp independent shadow controls native draw; owned="+OWNERS.size()+"; last="+failure+"; qualified="+qualifiedDraws+", compiledProof="+compiledProofDraws+", legacyProof="+legacyProofDraws+"; compile="+MakeUpShadowCompiledPrograms.status()+(COLORED_PROOF?"; coloredProof="+controlProof():"");}
 private static Map<String,String> options(ShaderPack pack){var o=pack.getShaderPackOptions();if(!MakeUpAoOptionGuard.declaration(o.getOptionSet()))return Map.of();return MakeUpAoOptionGuard.snapshot(o.getOptionSet(),o.getOptionValues(),true);}
 public static boolean profile(Map<String,String> m){m=com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.withoutInactiveControl(m);if(m==null||m.size()!=111||!"false".equals(m.get("COLORED_SHADOW")))return false;var n=new TreeMap<>(m);n.put("COLORED_SHADOW","true");return MakeUpAoContract.options(n)&&"true".equals(m.get("SHADOW_CASTING"))&&"true".equals(m.get("FOLIAGE_V"))&&"1".equals(m.get("SHADOW_TYPE"))&&"0".equals(m.get("SHADOW_LOCK"))&&"2".equals(m.get("AA_TYPE"));}
 public static boolean coloredBudgetProfile(Map<String,String> m){m=com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.withoutInactiveControl(m);return m!=null&&m.size()==111&&"true".equals(m.get("COLORED_SHADOW"))&&MakeUpAoContract.options(m)&&"true".equals(m.get("SHADOW_CASTING"))&&"true".equals(m.get("FOLIAGE_V"))&&"1".equals(m.get("SHADOW_TYPE"))&&"0".equals(m.get("SHADOW_LOCK"))&&"2".equals(m.get("AA_TYPE"));}
 private static boolean source(ShaderPack pack,boolean colored){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(MakeUpShadowRuntime.class,"source",pack,colored),()->{sourcePack=null;
  var m=options(pack);if(!(colored?coloredBudgetProfile(m):profile(m))){SOURCE_CACHE.invalidate();return false;}
  if(sourcePack!=pack){sourcePack=pack;sourceOptions=m;sourceReady=false;
   try{var o=pack.getShaderPackOptions();var client=net.minecraft.client.Minecraft.getInstance();
    sourceLookups++;sourceReady=SOURCE_CACHE.check(MakeUpShadowRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),
     "makeup-ultra-fast-shaders",m,pack,o.getOptionValues(),o.getOptionSet(),Iris.getPipelineManager().getPipelineNullable(),client.level,epoch,
     java.util.Arrays.asList(colored?"colored-budget":"noncolored-shadow",OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue())),
     client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),()->sourceUncached(m));
    if(!sourceReady)SOURCE_CACHE.invalidate();
   }catch(Exception|LinkageError e){SOURCE_CACHE.invalidate();return false;}}
  if(!sourceOptions.equals(m)){SOURCE_CACHE.invalidate();return false;}return sourceReady;
 
  });
 }
 private static boolean sourceUncached(Map<String,String> m){
  sourceValidatorCalls++;try{try(var in=MakeUpShadowRuntime.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")){
   if(in==null||!MakeUpAoSourceProof.RECIPE_SHA.equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;}
   String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null)return false;
   var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var p=dir.resolve(name).normalize();if(!p.startsWith(dir)||p.equals(dir))return false;
   return "makeup-ultra-fast-shaders".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(p,m));
  }catch(Exception|LinkageError e){return false;}
 }

 private static int integer(int p,String n){int l=location(p,n);return l<0?-1:GL20.glGetUniformi(p,l);}private static int param(int s,int n){return s==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,n):GL33.glGetSamplerParameteri(s,n);}
 private static BslReceiverContract.Sampler depth(int p){int u=integer(p,"shadowtex1");if(u<0||u>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+u);int t=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),s=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var c=GL.getCapabilities();boolean anis=c.GL_EXT_texture_filter_anisotropic||c.GL_ARB_texture_filter_anisotropic;float a=anis?(s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(s,0x84FE)):1;return new BslReceiverContract.Sampler(t,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),param(s,0x884C),param(s,0x884D),param(s,GL11.GL_TEXTURE_MIN_FILTER),param(s,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),param(s,GL11.GL_TEXTURE_WRAP_S),param(s,GL11.GL_TEXTURE_WRAP_T),a);}finally{GL13.glActiveTexture(active);}}
 private static MakeUpShadowSamplerPass.Reading coloredSampler(int p,String name){int u=integer(p,name);if(u<0||u>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+u);int t=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),s=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var c=GL.getCapabilities();boolean anis=c.GL_EXT_texture_filter_anisotropic||c.GL_ARB_texture_filter_anisotropic;float a=anis?(s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(s,0x84FE)):1;return new MakeUpShadowSamplerPass.Reading(u,t,s,new MakeUpColoredShadowContract.Sampler(t,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x884A),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,0x8C16),param(s,0x884C),param(s,0x884D),param(s,GL11.GL_TEXTURE_MIN_FILTER),param(s,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,0x813C),param(s,GL11.GL_TEXTURE_WRAP_S),param(s,GL11.GL_TEXTURE_WRAP_T),s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x8501):GL33.glGetSamplerParameterf(s,0x8501),s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x813A):GL33.glGetSamplerParameterf(s,0x813A),s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x813B):GL33.glGetSamplerParameterf(s,0x813B),a));}finally{GL13.glActiveTexture(active);}}
 private static MakeUpColoredShadowContract.Sampler state(MakeUpShadowSamplerPass.Reading reading){return reading==null?null:reading.state();}
 private static MakeUpShadowSamplerPass.Observation inspectSamplers(int p,ShadowRenderTargets shadows){
  MakeUpShadowSamplerPass.captured();
  var one=coloredSampler(p,"shadowtex1");
  if(!sampler(depthSnapshot(state(one)),MakeUpShadowDepthId.current(shadows),shadows.getResolution()))return null;
  var zero=coloredSampler(p,"shadowtex0");
  if(!COLORED_PROOF&&!MakeUpColoredShadowContract.depth(state(zero),shadows.getDepthTexture().getTextureId()))return null;
  if(!COLORED_PROOF&&!MakeUpColoredShadowContract.depth(state(one),MakeUpShadowDepthId.current(shadows)))return null;
  var color=coloredSampler(p,"shadowcolor0");
  if(COLORED_PROOF){proofAttempts++;proofSamplers=Map.of("shadowtex0",samplerProof(state(zero)),"shadowtex1",samplerProof(state(one)),"shadowcolor0",samplerProof(state(color)),"owned_depth0",shadows.getDepthTexture().getTextureId(),"owned_depth1",MakeUpShadowDepthId.current(shadows),"owned_color0",shadows.getColorTextureId(0));}
  if(!MakeUpColoredShadowContract.depth(state(zero),shadows.getDepthTexture().getTextureId())){if(COLORED_PROOF)proofRefusal="depth0 sampler refused";return null;}
  if(!MakeUpColoredShadowContract.depth(state(one),MakeUpShadowDepthId.current(shadows))){if(COLORED_PROOF)proofRefusal="depth1 sampler refused";return null;}
  if(!MakeUpColoredShadowContract.color(state(color),shadows.getColorTextureId(0))){if(COLORED_PROOF)proofRefusal="color0 sampler refused (strict RGBA8 retained)";return null;}
  return new MakeUpShadowSamplerPass.Observation(zero,one,color);
 }
 private static void qualified(LinkedProgramLocations.Entry<GlProgram<?>,Certified> linked){qualifiedDraws++;if(linked!=null){if(linked.certificate().pass()==SodiumPrograms.Pass.TERRAIN)qualifiedSolidDraws++;else if(linked.certificate().pass()==SodiumPrograms.Pass.TERRAIN_CUTOUT)qualifiedCutoutDraws++;}}
 public static BslReceiverContract.Sampler depthSnapshot(MakeUpColoredShadowContract.Sampler s){return s==null?null:new BslReceiverContract.Sampler(s.texture(),s.width(),s.height(),s.format(),s.depthBits(),s.depthType(),s.compare(),s.function(),s.min(),s.mag(),s.base(),s.wrapS(),s.wrapT(),s.anisotropy());}
 public static boolean sampler(BslReceiverContract.Sampler s,int texture,int resolution){if(s==null||texture<=0||s.texture()!=texture||resolution!=840||s.width()!=resolution||s.height()!=resolution)return false;return ((s.format()==0x81A5&&s.depthBits()==16&&s.depthType()==0x8C17)||(s.format()==0x81A6&&s.depthBits()==24&&s.depthType()==0x8C17)||(s.format()==0x81A7&&s.depthBits()==32&&s.depthType()==0x8C17)||(s.format()==0x8CAC&&s.depthBits()==32&&s.depthType()==0x1406))&&s.compare()==0x884E&&s.compareFunction()==0x0203&&s.min()==0x2601&&s.mag()==0x2601&&s.base()==0&&s.wrapS()==0x812F&&s.wrapT()==0x812F&&s.anisotropy()==1;}
 public static void currentDraw(Runnable nativeDraw){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);MakeUpReflectionRuntime.scoped(MakeUpShadowRuntime::draw,nativeDraw,()->revoke(p));}
 public static void revoke(int p){if(p<=0)return;for(String name:List.of("shaderlodMakeupShadowSamplerPermission","shaderlodMakeupColoredShadowBudgetPermission")){int l=location(p,name);if(l>=0){if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==p)GL20.glUniform1i(l,0);else if(GL.getCapabilities().OpenGL41)GL41.glProgramUniform1i(p,l,0);}}if(COLORED_PROOF&&p==proofProgram&&proofGrants>proofRevokes){proofRevokeCurrent=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);proofRevokeColored=integer(p,"shaderlodMakeupColoredShadowBudgetPermission");proofRevokeShared=integer(p,"shaderlodMakeupShadowSamplerPermission");proofRevokes++;}MakeUpShadowSamplerPass.revoked();}
 public static void draw(){boolean drawQualified=false;try{MakeUpCloudRuntime.draw();int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(p<=0)return;
 int l=location(p,"shaderlodMakeupShadowSamplerPermission"),coloredLocation=location(p,"shaderlodMakeupColoredShadowBudgetPermission");
 if(l>=0)GL20.glUniform1i(l,0);if(coloredLocation>=0)GL20.glUniform1i(coloredLocation,0);if(l<0&&coloredLocation<0)return;failure="native/refused";if(COLORED_PROOF)proofRefusal="core/preset/request/API/source/owner or viewport guard";
 try{var core=OptimizationRuntimeContext.core();if(core==null||!core.settings().enabled()||core.preset()==LodPreset.QUALITY||!requested(core)||MakeUpQuadLifecycle.suspended()||!MakeUpShadowPins.exact()||Iris.isFallback())return;
 var platform=IrisPlatformHelpers.getInstance();if(platform.isModLoaded("distanthorizons")||platform.isModLoaded("voxy"))return;
 var pipe=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);Owner o=OWNERS.get(p);
 if(o==null||o.pipeline()!=pipe||o.pack()!=pack||o.epoch()!=epoch||!(pipe instanceof IrisRenderingPipeline)||pack==null)return;
 var linked=linkedOwner(p);boolean colored;
 if(linked!=null){var proof=linked.certificate();if(proof.pipeline()!=pipe||proof.pack()!=pack||proof.epoch()!=epoch||!proof.source().current(pack,Iris.getIrisConfig().getShaderPackName().orElse(null),epoch))return;colored=proof.colored();compiledProofDraws++;}
 else{legacyProofDraws++;colored=coloredBudgetProfile(options(pack));if(!source(pack,colored))return;}
 if(net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;
 if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE||integer(p,"shaderlodBridgeVersion")!=2||integer(p,"shaderlodEnabled")!=1||integer(p,"shaderlodOptimizationFlagsVersion")!=1||!Set.of(0,1,2).contains(integer(p,"shaderlodPreset")))return;
 if(colored){
  var client=net.minecraft.client.Minecraft.getInstance();
  if(coloredLocation<0||core.preset()!=LodPreset.PERFORMANCE||integer(p,"shaderlodPreset")!=1||!core.optimizations().enabled(OptimizationId.SHADOW_SAMPLE_BUDGET)||!OptimizationConfig.requested(OptimizationId.SHADOW_SAMPLE_BUDGET)||integer(p,"shaderlodOptShadowSampleBudget")!=1
   ||!Iris.getShaderPackOptionQueue().isEmpty()||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return;
  if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.loadedStackMatches()||!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds()))return;
 }else if(l<0||!uploaded(core,p))return;
 var view=(ShadowPipelineView)pipe;var targets=view.shadowlod$targets();var shadows=view.shadowlod$shadows();if(targets==null||shadows==null)return;
 int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);var window=net.minecraft.client.Minecraft.getInstance().getWindow();
 if(!Arrays.equals(v,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()})||window.getWidth()!=v[2]||window.getHeight()!=v[3])return;
 if(colored){
  MakeUpShadowSamplerPass.Key key=null;
  if(linked!=null&&linked.certificate().scopeProfile()&&MakeUpShadowSamplerPass.allowed(core)&&MakeUpShadowSamplerPass.matchesPass(linked.certificate().pass()))
   key=new MakeUpShadowSamplerPass.Key(linked.program(),pipe,pack,GL.getCapabilities(),targets,shadows,shadows.getDepthTexture(),shadows.getDepthTextureNoTranslucents(),shadows.get(0),shadows.getDepthTexture().getTextureId(),MakeUpShadowDepthId.current(shadows),shadows.getColorTextureId(0),shadows.getResolution(),epoch);
  if(key==null)MakeUpShadowSamplerPass.invalidate();
  var observation=key==null?null:MakeUpShadowSamplerPass.reuse(key);
  if(observation==null){observation=inspectSamplers(p,shadows);if(observation==null)return;if(key!=null)MakeUpShadowSamplerPass.remember(key,observation);}
  GL20.glUniform1i(coloredLocation,1);MakeUpShadowSamplerPass.granted(p);drawQualified=true;qualified(linked);
  if(COLORED_PROOF){proofProgram=p;proofGrantCurrent=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);proofGrantColored=GL20.glGetUniformi(p,coloredLocation);proofGrantShared=l<0?-1:GL20.glGetUniformi(p,l);proofGrants++;proofRefusal="qualified; inspect actual permission readbacks";}
  failure="qualified colored-budget-only native840 depth0/depth1/color0; shared permission0";
 }else{if(!sampler(depth(p),MakeUpShadowDepthId.current(shadows),shadows.getResolution()))return;GL20.glUniform1i(l,1);MakeUpShadowSamplerPass.granted(p);drawQualified=true;qualified(linked);failure="qualified actual bound native comparison sampler";}
 }catch(RuntimeException|LinkageError e){revoke(p);failure=e.getClass().getSimpleName();drawQualified=false;}}finally{if(!drawQualified)MakeUpShadowSamplerPass.invalidate();}}

public static void currentDraw(int mode,long counts,int type,long indices,int drawcount,long bases,com.pp.shaderlod.optimization.nativechain.NativeDraw nativeDraw){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);MakeUpReflectionRuntime.scoped(MakeUpShadowRuntime::draw,mode,counts,type,indices,drawcount,bases,nativeDraw,()->revoke(p));}

 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){source(pack,true);source(pack,false);}
}
