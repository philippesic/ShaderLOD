package com.pp.shaderlod.optimization.reimaginedearlyalpha;
import java.util.*;import com.pp.shaderlod.optimization.sourcecache.*;import net.irisshaders.iris.Iris;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import net.irisshaders.iris.shaderpack.ShaderPack;import net.irisshaders.iris.shaderpack.programs.ProgramSource;import net.irisshaders.iris.gl.blending.AlphaTest;import net.irisshaders.iris.pipeline.programs.SodiumPrograms;import org.lwjgl.opengl.*;import com.pp.shaderlod.optimization.*;import com.pp.shaderlod.LodPreset;import com.pp.shaderlod.integration.makeupquad.*;import com.pp.shaderlod.optimization.makeupao.*;import com.pp.shaderlod.optimization.makeupshadow.mixin.ShadowPipelineView;
import com.pp.shaderlod.optimization.complementarysampler.ComplementarySamplerPass;
public final class ReimaginedEarlyAlphaRuntime{private ReimaginedEarlyAlphaRuntime(){}
// Untimed developer control proof; normal launches perform no additional GL queries.
private static final boolean CONTROL_PROOF="1".equals(System.getenv("SHADERLOD_REIMAGINED_ALPHA_CONTROL_PROOF"));
private static long proofGrants,proofRevokes;private static int proofProgram,proofGrantPermission,proofRevokePermission;private static float proofNativeReference,proofPrivateReference;
public static Map<String,Object> controlProof(){return Map.ofEntries(Map.entry("enabled",CONTROL_PROOF),Map.entry("grants",proofGrants),Map.entry("revokes",proofRevokes),Map.entry("last_program",proofProgram),Map.entry("last_grant_permission",proofGrantPermission),Map.entry("last_revoke_permission",proofRevokePermission),Map.entry("last_native_reference",proofNativeReference),Map.entry("last_private_reference",proofPrivateReference));}
private static final ComplementarySamplerPass.Lane<ReimaginedEarlyAlphaContract.Sampler> RECEIVER_SAMPLERS=ComplementarySamplerPass.REIMAGINED_RECEIVER;
private static final ComplementarySamplerPass.Lane<ReimaginedEarlyAlphaContract.Sampler> TEMPORAL_SAMPLERS=ComplementarySamplerPass.REIMAGINED_TEMPORAL;
private static void samplerCounters(Map<String,Long> result){
 RECEIVER_SAMPLERS.counters().forEach((k,v)->result.put("receiver_sampler_"+k,v));
 TEMPORAL_SAMPLERS.counters().forEach((k,v)->result.put("temporal_sampler_"+k,v));
}
private enum SourceReader { ORDINARY, TEMPORAL, RECEIVER }
private enum SourcePhase { COMPILE, DRAW, CONTROL }
private static final long[][] sourceCalls=new long[3][3],sourceNanos=new long[3][3];
private static boolean observedSource(ShaderPack pack,SourceReader reader,SourcePhase phase){
 int r=reader.ordinal(),p=phase.ordinal();sourceCalls[r][p]++;long start=System.nanoTime();
 try{return switch(reader){case ORDINARY->cachedSource(pack);case TEMPORAL->cachedTemporalSource(pack);case RECEIVER->cachedReceiverSource(pack);};}
 finally{sourceNanos[r][p]+=System.nanoTime()-start;}
}
public static boolean source(ShaderPack pack){return observedSource(pack,SourceReader.ORDINARY,SourcePhase.CONTROL);}
public static boolean temporalSource(ShaderPack pack){return observedSource(pack,SourceReader.TEMPORAL,SourcePhase.CONTROL);}
public static boolean receiverSource(ShaderPack pack){return observedSource(pack,SourceReader.RECEIVER,SourcePhase.CONTROL);}
public static boolean compileSource(ShaderPack pack){return observedSource(pack,SourceReader.ORDINARY,SourcePhase.COMPILE);}
public static boolean compileTemporalSource(ShaderPack pack){return observedSource(pack,SourceReader.TEMPORAL,SourcePhase.COMPILE);}
private static boolean drawSource(ShaderPack pack){return observedSource(pack,SourceReader.ORDINARY,SourcePhase.DRAW);}
private static boolean drawTemporalSource(ShaderPack pack){return observedSource(pack,SourceReader.TEMPORAL,SourcePhase.DRAW);}
private static boolean drawReceiverSource(ShaderPack pack){return observedSource(pack,SourceReader.RECEIVER,SourcePhase.DRAW);}
/** Elapsed source-proof cost includes every cache call, including hits and refusals. */
public static Map<String,Long> costCounters(){
 var result=new TreeMap<String,Long>();String[] names={"alpha","temporal","receiver"};
 for(var reader:SourceReader.values())for(var phase:SourcePhase.values()){
  String key=names[reader.ordinal()]+"_"+phase.name().toLowerCase(Locale.ROOT)+"_source_";
  result.put(key+"calls",sourceCalls[reader.ordinal()][phase.ordinal()]);result.put(key+"ns",sourceNanos[reader.ordinal()][phase.ordinal()]);
 }
 samplerCounters(result);result.putAll(receiverCostCounters());result.put("alpha_qualified",qualifiedDraws);result.put("receiver_qualified",receiverQualifiedEntries);result.put("temporal_qualified",temporalQualifiedEntries);return Map.copyOf(result);
}
private record Owner(Object pipeline,ShaderPack pack,long epoch,IrisCompiledSource.Certificate certificate,Map<String,Integer> locations,
 net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> nativeProgram,SodiumPrograms.Pass nativePass){
 Owner(Object pipeline,ShaderPack pack,long epoch){this(pipeline,pack,epoch,null,Map.of(),null,null);}
 Owner(Object pipeline,ShaderPack pack,long epoch,IrisCompiledSource.Certificate certificate,Map<String,Integer> locations){this(pipeline,pack,epoch,certificate,locations,null,null);}
}private static final Map<ProgramSource,Boolean> PREPARED=new WeakHashMap<>();private static final Map<Integer,Owner> OWNERS=new HashMap<>();private static final Map<Integer,Owner> TEMPORAL_OWNERS=new HashMap<>();private static long receiverQualifiedEntries;private static String receiverFailure="not consumed";private static long temporalQualifiedEntries;private static String temporalFailure="not consumed";private static long epoch;
private static final LinkedCompilationProof COMPILATION=new LinkedCompilationProof("complementary-reimagined","gbuffers_terrain");
private static long qualifiedDraws,compiledProofDraws,legacyProofDraws;
private static final TemporalProgramReceipts<net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?>,IrisCompiledSource.Certificate> TEMPORAL_RECEIPTS=new TemporalProgramReceipts<>(p->p.handle());
public static Map<String,Long> temporalProofCounters(){return TEMPORAL_RECEIPTS.counters();}
public static long epoch(){return epoch;}
public static Map<String,Object> diagnosticCounters(){return Map.of("qualified_draws",qualifiedDraws,"compiled_proof_draws",compiledProofDraws,"legacy_proof_draws",legacyProofDraws,"owned_programs",OWNERS.size(),"source_ready",ready,"epoch",epoch);}
public static LinkedCompilationProof compilation(){return COMPILATION;}
private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache TEMPORAL_SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache RECEIVER_SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();private static boolean ready;private static String failure="not consumed";public static String status(){return "Reimagined native CUTOUT earlyalpha; owned="+OWNERS.size()+"; last="+failure+"; qualified="+qualifiedDraws+", compiledProof="+compiledProofDraws+", legacyProof="+legacyProofDraws+"; compile="+COMPILATION.status();}public static void invalidate(){receiverGrant=null;RECEIVER_SAMPLERS.invalidate();TEMPORAL_SAMPLERS.invalidate();ReimaginedTemporalCompiledPrograms.clear();TEMPORAL_RECEIPTS.clear();ReimaginedReceiverCompiledPrograms.clear();SOURCE_CACHE.invalidate();COMPILATION.clear();TEMPORAL_SOURCE_CACHE.invalidate();RECEIVER_SOURCE_CACHE.invalidate();epoch++;PREPARED.clear();OWNERS.clear();TEMPORAL_OWNERS.clear();ready=false;}public static void destroy(Object p){receiverGrant=null;RECEIVER_SAMPLERS.invalidate();TEMPORAL_SAMPLERS.invalidate();ReimaginedTemporalCompiledPrograms.clear();TEMPORAL_OWNERS.forEach((id,o)->{if(o.pipeline()==p)TEMPORAL_RECEIPTS.forget(id);});ReimaginedReceiverCompiledPrograms.destroy(p);SOURCE_CACHE.invalidate();COMPILATION.clear();TEMPORAL_SOURCE_CACHE.invalidate();RECEIVER_SOURCE_CACHE.invalidate();OWNERS.entrySet().removeIf(e->e.getValue().pipeline()==p);TEMPORAL_OWNERS.entrySet().removeIf(e->e.getValue().pipeline()==p);ready=false;}public static void prepared(ProgramSource s,boolean valid){if(s!=null)PREPARED.put(s,valid);}public static boolean profile(Map<String,String> m){return com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("complementary-reimagined",m);} private static boolean cachedSource(ShaderPack p){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(ReimaginedEarlyAlphaRuntime.class,"cachedSource",p),()->{if(p==null){SOURCE_CACHE.invalidate();return false;}try{var o=p.getShaderPackOptions();var set=o.getOptionSet();var v=o.getOptionValues();var options=new TreeMap<String,String>();for(var k:set.getBooleanOptions().keySet())options.put(k,Boolean.toString(v.getBooleanValueOrDefault(k)));for(var k:set.getStringOptions().keySet())options.put(k,v.getStringValueOrDefault(k));if(!profile(options)){SOURCE_CACHE.invalidate();return false;}var client=net.minecraft.client.Minecraft.getInstance();return SOURCE_CACHE.check(ReimaginedEarlyAlphaRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),"complementary-reimagined",options,p,v,set,Iris.getPipelineManager().getPipelineNullable(),client.level,epoch,java.util.Arrays.asList(SourceReader.ORDINARY,OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue())),client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),()->sourceUncached(p));}catch(Exception|LinkageError failure){SOURCE_CACHE.invalidate();return false;}
  });
 } private static boolean cachedTemporalSource(ShaderPack p){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(ReimaginedEarlyAlphaRuntime.class,"cachedTemporalSource",p),()->{if(p==null){TEMPORAL_SOURCE_CACHE.invalidate();return false;}try{var o=p.getShaderPackOptions();var set=o.getOptionSet();var v=o.getOptionValues();var options=new TreeMap<String,String>();for(var k:set.getBooleanOptions().keySet())options.put(k,Boolean.toString(v.getBooleanValueOrDefault(k)));for(var k:set.getStringOptions().keySet())options.put(k,v.getStringValueOrDefault(k));if(!temporalProfile(options)){TEMPORAL_SOURCE_CACHE.invalidate();return false;}var client=net.minecraft.client.Minecraft.getInstance();return TEMPORAL_SOURCE_CACHE.check(ReimaginedEarlyAlphaRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),"complementary-reimagined",options,p,v,set,Iris.getPipelineManager().getPipelineNullable(),client.level,epoch,java.util.Arrays.asList(SourceReader.TEMPORAL,OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue())),client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),()->sourceUncached(p,SourceReader.TEMPORAL));}catch(Exception|LinkageError failure){TEMPORAL_SOURCE_CACHE.invalidate();return false;}
  });
 } private static boolean cachedReceiverSource(ShaderPack p){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(ReimaginedEarlyAlphaRuntime.class,"cachedReceiverSource",p),()->{if(p==null){RECEIVER_SOURCE_CACHE.invalidate();return false;}try{var o=p.getShaderPackOptions();var set=o.getOptionSet();var v=o.getOptionValues();var options=new TreeMap<String,String>();for(var k:set.getBooleanOptions().keySet())options.put(k,Boolean.toString(v.getBooleanValueOrDefault(k)));for(var k:set.getStringOptions().keySet())options.put(k,v.getStringValueOrDefault(k));if(!temporalProfile(options)){RECEIVER_SOURCE_CACHE.invalidate();return false;}var client=net.minecraft.client.Minecraft.getInstance();return RECEIVER_SOURCE_CACHE.check(ReimaginedEarlyAlphaRuntime.class,Iris.getShaderpacksDirectory(),Iris.getIrisConfig().getShaderPackName().orElse(null),"complementary-reimagined",options,p,v,set,Iris.getPipelineManager().getPipelineNullable(),client.level,epoch,java.util.Arrays.asList(SourceReader.RECEIVER,OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue())),client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList(),()->sourceUncached(p,SourceReader.RECEIVER));}catch(Exception|LinkageError failure){RECEIVER_SOURCE_CACHE.invalidate();return false;}
  });
 }
private static boolean sourceUncached(ShaderPack p){return sourceUncached(p,SourceReader.ORDINARY);}
// All three purpose-specific readers adopt reviewed current bytes; their memos and guards remain separate.
private static boolean sourceUncached(ShaderPack p,SourceReader reader){boolean receiver=reader==SourceReader.RECEIVER;if(p==null)return false;try{String n=Iris.getIrisConfig().getShaderPackName().orElse(null);var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();if(n==null||!java.nio.file.Path.of(n).getFileName().toString().equals(n))return false;var path=dir.resolve(n);var input=com.pp.shaderlod.integration.PackSourceReader.read(path);if(!com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(input.memberSha256()).equals(receiver?"2d2467ef372ab37e303f3736179fed301dead8d2132c0eae486e3cb0dee617e9":"2d2467ef372ab37e303f3736179fed301dead8d2132c0eae486e3cb0dee617e9"))return false;var o=p.getShaderPackOptions();var set=o.getOptionSet();var values=o.getOptionValues();var m=new TreeMap<String,String>();for(var k:set.getBooleanOptions().keySet())m.put(k,Boolean.toString(values.getBooleanValueOrDefault(k)));for(var k:set.getStringOptions().keySet())m.put(k,values.getStringValueOrDefault(k));if(!(reader!=SourceReader.ORDINARY?temporalProfile(m):profile(m)))return false;try(var in=ReimaginedEarlyAlphaRuntime.class.getClassLoader().getResourceAsStream("assets/shaderlod/adapters/patches/adapters/complementary-reimagined.json")){if(in==null||!HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(receiver?"7eb35919b4c5781ebd2a6bf4dac58b5b7b40b2e9979ec6afa9b7529e371bd158":"7eb35919b4c5781ebd2a6bf4dac58b5b7b40b2e9979ec6afa9b7529e371bd158"))return false;}return "complementary-reimagined".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(path,m));}catch(Exception|LinkageError e){return false;}}
public static void own(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,int id){own(p,pass,s,id,null);}
/** Actual native hook cannot erase independent receiver evidence through integer registration. */
public static void ownNative(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> program,IrisCompiledSource.Certificate certificate){
 own(p,pass,s,program.handle(),certificate,true);
}
public static void own(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,int id,IrisCompiledSource.Certificate certificate){
 own(p,pass,s,id,certificate,false);
}
private static void own(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,int id,IrisCompiledSource.Certificate certificate,boolean nativeProgram){
 if(!nativeProgram){ReimaginedReceiverCompiledPrograms.legacy(id);ownTemporal(p,pass,s,id);}
 if(id>0)OWNERS.remove(id);
 if(p!=null&&s!=null&&s.getParent()!=null&&"gbuffers_terrain".equals(s.getName())&&pass==SodiumPrograms.Pass.TERRAIN_CUTOUT&&Boolean.TRUE.equals(PREPARED.get(s))&&id>0)
  OWNERS.put(id,new Owner(p,s.getParent().getPack(),epoch,certificate!=null&&certificate.source()==s&&certificate.epoch()==epoch?certificate:null,locations(id)));
}
private static boolean sourceForDraw(Owner owner,ShaderPack pack){
 var certificate=owner.certificate();
 if(certificate!=null&&!certificate.current(pack,Iris.getIrisConfig().getShaderPackName().orElse(null),epoch))return false;
 if(certificate!=null){compiledProofDraws++;return true;}
 legacyProofDraws++;return drawSource(pack);
}
/** Derived registration caller first proves its own linked vertex and exact shared native fragment. Never copies readiness. */
public static boolean ownsDerived(Object pipeline,Object pack,int id){var o=OWNERS.get(id);return id>0&&pipeline!=null&&pack!=null&&o!=null&&o.pipeline()==pipeline&&o.pack()==pack&&o.epoch()==epoch;}
public static void forgetDerived(int id){receiverGrant=null;RECEIVER_SAMPLERS.invalidate();TEMPORAL_SAMPLERS.invalidate();TEMPORAL_RECEIPTS.forget(id);ReimaginedReceiverCompiledPrograms.forget(id);OWNERS.remove(id);TEMPORAL_OWNERS.remove(id);}

// A live grant is distinct from a source certificate and from an integer boundary marker.
// Only the peer's already-issued native grant may suppress this runtime's conservative preclear.
private record ReceiverGrant(Object context, Object receipt, Object core, long generation) {}
private static ReceiverGrant receiverGrant;
public static boolean retainsReceiverGrant(int program) {
 try {
  var grant=receiverGrant;
  if(grant==null || program<=0 || GL.getCapabilities()!=grant.context()
      || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program
      || !Objects.equals(OptimizationRuntimeContext.core(),grant.core())
      || OptimizationConfig.generation()!=grant.generation()
      || MakeUpQuadLifecycle.suspended() || Iris.isFallback()
      || !Iris.getShaderPackOptionQueue().isEmpty())return false;
  // current() checks the retained GlProgram handle, pipeline, pack, epoch and source lifetime.
  var current=ReimaginedReceiverCompiledPrograms.current(program,Iris.getPipelineManager().getPipelineNullable(),
      Iris.getCurrentPack().orElse(null),epoch);
  return current!=null && current==grant.receipt();
 } catch(RuntimeException|LinkageError unavailable) { return false; }
}
private static long receiverCompiledDraws,receiverLegacyDraws,receiverStaleDraws,receiverSolidGrants,receiverCutoutGrants;
public static Map<String,Long> receiverCostCounters(){return Map.of(
 "receiver_native_compiled",receiverCompiledDraws,"receiver_legacy",receiverLegacyDraws,"receiver_stale_or_missing",receiverStaleDraws,
 "receiver_solid_grants",receiverSolidGrants,"receiver_cutout_grants",receiverCutoutGrants,
 "receiver_compile_source_calls",ReimaginedReceiverCompiledPrograms.compileCalls(),"receiver_compile_source_ns",ReimaginedReceiverCompiledPrograms.compileNanos());}
private static boolean receiverSourceForDraw(int program,Object pipeline,ShaderPack pack){
 if(ReimaginedReceiverCompiledPrograms.requiresNative(program,pack)){
  if(ReimaginedReceiverCompiledPrograms.current(program,pipeline,pack,epoch)==null){receiverStaleDraws++;receiverFailure="native receiver receipt missing/stale/rejected";return false;}
  receiverCompiledDraws++;return true;
 }
 var owner=TEMPORAL_OWNERS.get(program);
 if(owner==null||owner.pipeline()!=pipeline||owner.pack()!=pack||owner.epoch()!=epoch)return false;
 receiverLegacyDraws++;return drawReceiverSource(pack);
}
public static void ownReceiverCompiled(IrisRenderingPipeline pipeline,SodiumPrograms.Pass pass,ProgramSource source,
 net.irisshaders.iris.gl.blending.AlphaTest alpha,Map<?,?> shaders,net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> program){
 ReimaginedReceiverCompiledPrograms.linked(pipeline,pass,source,alpha,shaders,program,epoch,locations(program.handle()));
}

public static String receiverStatus(){return "samplers="+RECEIVER_SAMPLERS.counters()+"; "+"native full-rate complementary receiver4-to2 complete-pair preview; temporal takes priority; spatial/world inactive; qualified="+receiverQualifiedEntries+"; last="+receiverFailure+"; receipts="+receiverCostCounters()+"; compile="+ReimaginedReceiverCompiledPrograms.status();}
public static String temporalStatus(){return "samplers="+TEMPORAL_SAMPLERS.counters()+"; "+"temporal shadow sampler owners="+TEMPORAL_OWNERS.size()+"; cumulative qualified draw entries="+temporalQualifiedEntries+"; last="+temporalFailure+"; proof="+TEMPORAL_RECEIPTS.counters()+"; compile="+ReimaginedTemporalCompiledPrograms.status()+"; native draw completion/work/cost not inferred";}
public static boolean temporalProfile(Map<String,String> actual){return ReimaginedTemporalCompiledPrograms.profile(actual);}
/** Independent temporal receiver ownership; never grants alpha discard or shares its permission. */
public static boolean temporalPass(SodiumPrograms.Pass pass,String fragment){return (pass==SodiumPrograms.Pass.TERRAIN||pass==SodiumPrograms.Pass.TERRAIN_CUTOUT)&&fragment!=null&&fragment.contains("shaderlodReimaginedTemporalShadowSamplingQualified");}
public static void ownTemporal(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,int id){TEMPORAL_RECEIPTS.legacyProgram(id);TEMPORAL_OWNERS.remove(id);if(p!=null&&s!=null&&s.getParent()!=null&&"gbuffers_terrain".equals(s.getName())&&id>0&&temporalPass(pass,s.getFragmentSource().orElse(null))&&compileTemporalSource(s.getParent().getPack()))TEMPORAL_OWNERS.put(id,new Owner(p,s.getParent().getPack(),epoch));}
public static void ownTemporalNative(IrisRenderingPipeline p,SodiumPrograms.Pass pass,ProgramSource s,
        net.caffeinemc.mods.sodium.client.gl.shader.GlProgram<?> program,AlphaTest alpha,IrisCompiledSource.Certificate certificate){
 int id=program.handle();OWNERS.remove(id);TEMPORAL_OWNERS.remove(id);
 boolean accepted=p!=null&&s!=null&&s.getParent()!=null&&"gbuffers_terrain".equals(s.getName())
     &&ReimaginedTemporalCompiledPrograms.acceptsPass(pass,alpha)&&temporalPass(pass,s.getFragmentSource().orElse(null));
 if(!accepted){TEMPORAL_RECEIPTS.forget(id);return;}
 var proof=certificate!=null&&certificate.source()==s&&certificate.epoch()==epoch?certificate:null;
 // Missing or stale native evidence is recorded as refusal, never a legacy source fallback.
 TEMPORAL_RECEIPTS.nativeProgram(id,program,proof,proof==null?Map.of():locations(id));
 TEMPORAL_OWNERS.put(id,new Owner(p,s.getParent().getPack(),epoch,proof,Map.of(),program,pass));
}
private static boolean temporalSourceForDraw(int program,ShaderPack pack){
 return TEMPORAL_RECEIPTS.allows(program,c->c.current(pack,Iris.getIrisConfig().getShaderPackName().orElse(null),epoch),()->drawTemporalSource(pack));
}
public static boolean ownsTemporalDerived(Object pipeline,Object pack,int id){var o=TEMPORAL_OWNERS.get(id);return id>0&&pipeline!=null&&pack!=null&&o!=null&&o.pipeline()==pipeline&&o.pack()==pack&&o.epoch()==epoch;}
public static boolean temporalControls(boolean enabled,LodPreset preset,boolean snapshot,boolean requested){return enabled&&preset!=null&&preset!=LodPreset.QUALITY&&snapshot&&requested;}
public static void ownTemporalSpatial(Object pipeline,ShaderPack pack,int program){ReimaginedReceiverCompiledPrograms.legacy(program);TEMPORAL_RECEIPTS.legacyProgram(program);TEMPORAL_OWNERS.remove(program);if(pipeline!=null&&pack!=null&&program>0&&com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedSpatialRuntime.ownsPrivateProgram(pipeline,pack,program))TEMPORAL_OWNERS.put(program,new Owner(pipeline,pack,epoch));}
/** Same owned native sampler law, independent of the temporal or alpha request. */
public static boolean spatialSamplers(Object pipeline,int program){if(!(pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline)||program<=0||GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=program)return false;var view=(ShadowPipelineView)pipeline;var shadows=view.shadowlod$shadows();if(shadows==null)return false;var zero=sample(program,"shadowtex0");if(zero==null)zero=sample(program,"shadowtex0HW");var one=sample(program,"shadowtex1");if(one==null)one=sample(program,"shadowtex1HW");return ReimaginedEarlyAlphaContract.sampler(zero,shadows.getDepthTexture().getTextureId(),true)&&ReimaginedEarlyAlphaContract.sampler(one,shadows.getDepthTextureNoTranslucents().getTextureId(),true)&&ReimaginedEarlyAlphaContract.sampler(sample(program,"shadowcolor0"),shadows.getColorTextureId(0),false);}
/** Private native coefficient programs own no alpha discard permission. */
public static void drawTemporalForSpatial(){temporalDraw(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));}
private static void temporalDraw(int p){boolean samplerQualified=false;try{if(p<=0)return;int loc=location(p,"shaderlodReimaginedTemporalShadowSamplingQualified");if(loc<0)return;GL20.glUniform1i(loc,0);temporalFailure="native/refused";
try{var c=net.minecraft.client.Minecraft.getInstance();var core=OptimizationRuntimeContext.core();if(core==null||!temporalControls(core.settings().enabled(),core.preset(),core.optimizations().enabled(OptimizationId.TEMPORAL_SHADOW_FILTER),OptimizationConfig.requested(OptimizationId.TEMPORAL_SHADOW_FILTER))||MakeUpQuadLifecycle.suspended()||!com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin.ReimaginedEarlyAlphaPlugin.exactApi()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()||c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return;
if(!MakeUpQuadRuntime.loadedStackMatches()||!MakeUpQuadRuntime.resourcesMatch(c.getResourcePackRepository().getSelectedIds()))return;
var pipe=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);var own=TEMPORAL_OWNERS.get(p);if(own==null||own.pipeline()!=pipe||own.pack()!=pack||own.epoch()!=epoch||!(pipe instanceof IrisRenderingPipeline)||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()||!temporalSourceForDraw(p,pack))return;
if(integer(p,"shaderlodBridgeVersion")!=2||integer(p,"shaderlodEnabled")!=1||integer(p,"shaderlodOptimizationFlagsVersion")!=1||integer(p,"shaderlodOptTemporalShadowFilter")!=1||!Set.of(0,1,2).contains(integer(p,"shaderlodPreset"))||GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)return;
var view=(ShadowPipelineView)pipe;var shadows=view.shadowlod$shadows();var targets=view.shadowlod$targets();if(shadows==null||targets==null)return;int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);if(!Arrays.equals(v,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()})&&(TEMPORAL_RECEIPTS.nativeProgram(p)||!com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedSpatialRuntime.temporalHalfViewport(pipe,pack,p,v,targets.getCurrentWidth(),targets.getCurrentHeight())))return;
var key=TEMPORAL_SAMPLERS.key(own.nativeProgram(),own.certificate(),own.nativePass(),p,core,pipe,pack,targets,shadows,epoch,shadows.getDepthTexture().getTextureId(),shadows.getDepthTextureNoTranslucents().getTextureId());
var samplers=samplerObservations(p,TEMPORAL_SAMPLERS,key);var zero=samplers.zero();var one=samplers.one();var colorSampler=samplers.color();
if(!ReimaginedEarlyAlphaContract.sampler(zero,shadows.getDepthTexture().getTextureId(),true)||!ReimaginedEarlyAlphaContract.sampler(one,shadows.getDepthTextureNoTranslucents().getTextureId(),true)||!ReimaginedEarlyAlphaContract.sampler(colorSampler,shadows.getColorTextureId(0),false))return;
TEMPORAL_SAMPLERS.remember(key,samplers);

GL20.glUniform1i(loc,1);TEMPORAL_SAMPLERS.granted(key,loc);samplerQualified=true;temporalQualifiedEntries++;temporalFailure="qualified owned source/program/current samplers";
}catch(RuntimeException|LinkageError unavailable){GL20.glUniform1i(loc,0);temporalFailure=unavailable.getClass().getSimpleName();}}finally{if(!samplerQualified)TEMPORAL_SAMPLERS.invalidate();}}

private static void receiverDraw(int p){receiverGrant=null;boolean samplerQualified=false;try{if(p<=0)return;if(com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime.retainsReceiverGrant(p))return;int loc=location(p,"shaderlodComplementaryReceiverPermission");if(loc<0)return;GL20.glUniform1i(loc,0);receiverFailure="native/refused";
try{var c=net.minecraft.client.Minecraft.getInstance();var core=OptimizationRuntimeContext.core();if(core==null||!temporalControls(core.settings().enabled(),core.preset(),core.optimizations().enabled(OptimizationId.SHADOW_RECEIVER_FOOTPRINT),OptimizationConfig.requested(OptimizationId.SHADOW_RECEIVER_FOOTPRINT))||MakeUpQuadLifecycle.suspended()||!com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin.ReimaginedEarlyAlphaPlugin.exactApi()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()||c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return;
var pipe=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);if(pack==null||!(pipe instanceof IrisRenderingPipeline)||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;
// These combinations always retain native sampling; do not fingerprint an unused source.
var loaded=pack.getShaderPackOptions().getOptionValues();
boolean activeSpatial=ComplementaryReceiverAdmission.spatialActive(core.optimizations().enabled(OptimizationId.SPATIAL_LIGHTING_REUSE),OptimizationConfig.requested(OptimizationId.SPATIAL_LIGHTING_REUSE),loaded.getStringValueOrDefault("SHADERLOD_REIMAGINED_SPATIAL_REUSE"))||ComplementaryReceiverAdmission.spatialActive(core.optimizations().enabled(OptimizationId.SPATIAL_LIGHTING_RATE),OptimizationConfig.requested(OptimizationId.SPATIAL_LIGHTING_RATE),loaded.getStringValueOrDefault("SHADERLOD_REIMAGINED_SPATIAL_RATE"));
if(activeSpatial)receiverFailure="native; active spatial receiver derivative join unfinished";
if(!ComplementaryReceiverAdmission.validate(loaded.getStringValueOrDefault("SHADOW_QUALITY"),activeSpatial,()->{
 
 return MakeUpQuadRuntime.loadedStackMatches()&&MakeUpQuadRuntime.resourcesMatch(c.getResourcePackRepository().getSelectedIds())&&receiverSourceForDraw(p,pipe,pack);
}))return;
if(integer(p,"shaderlodBridgeVersion")!=2||integer(p,"shaderlodEnabled")!=1||integer(p,"shaderlodOptimizationFlagsVersion")!=1||integer(p,"shaderlodOptShadowReceiverFootprint")!=1||!Set.of(0,1,2).contains(integer(p,"shaderlodPreset"))||GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)return;
var view=(ShadowPipelineView)pipe;var shadows=view.shadowlod$shadows();var targets=view.shadowlod$targets();if(shadows==null||targets==null)return;int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);if(!Arrays.equals(v,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()}))return;
var receipt=ReimaginedReceiverCompiledPrograms.current(p,pipe,pack,epoch);
var key=receipt==null?null:RECEIVER_SAMPLERS.key(receipt.program(),receipt.certificate().source(),receipt.certificate().pass(),p,core,pipe,pack,targets,shadows,epoch,shadows.getDepthTexture().getTextureId(),shadows.getDepthTextureNoTranslucents().getTextureId());
var samplers=samplerObservations(p,RECEIVER_SAMPLERS,key);var zero=samplers.zero();var one=samplers.one();var colorSampler=samplers.color();
if(!ReimaginedEarlyAlphaContract.sampler(zero,shadows.getDepthTexture().getTextureId(),true)||!ReimaginedEarlyAlphaContract.sampler(one,shadows.getDepthTextureNoTranslucents().getTextureId(),true)||!ReimaginedEarlyAlphaContract.sampler(colorSampler,shadows.getColorTextureId(0),false))return;
if(zero.width()!=shadows.getResolution()||zero.height()!=shadows.getResolution()||one.width()!=shadows.getResolution()||one.height()!=shadows.getResolution()||colorSampler.width()!=shadows.getResolution()||colorSampler.height()!=shadows.getResolution())return;
RECEIVER_SAMPLERS.remember(key,samplers);
GL20.glUniform1i(loc,1);RECEIVER_SAMPLERS.granted(key,loc);samplerQualified=true;receiverQualifiedEntries++;
receiverGrant=receipt==null?null:new ReceiverGrant(GL.getCapabilities(),receipt,core,OptimizationConfig.generation());

if(receipt!=null){if(receipt.certificate().pass()==SodiumPrograms.Pass.TERRAIN)receiverSolidGrants++;else if(receipt.certificate().pass()==SodiumPrograms.Pass.TERRAIN_CUTOUT)receiverCutoutGrants++;}receiverFailure="qualified owned source/program/current samplers";
}catch(RuntimeException|LinkageError unavailable){GL20.glUniform1i(loc,0);receiverFailure=unavailable.getClass().getSimpleName();}}finally{if(!samplerQualified)RECEIVER_SAMPLERS.invalidate();}}

/** Capture once after all guards; receiver reuses the same color reading for its dimensions. */
private static ComplementarySamplerPass.Observation<ReimaginedEarlyAlphaContract.Sampler> samplerObservations(int p,
 ComplementarySamplerPass.Lane<ReimaginedEarlyAlphaContract.Sampler> lane,ComplementarySamplerPass.Key key){
 var cached=lane.reuse(key);if(cached!=null)return cached;lane.captured();
 var zero=sample(p,"shadowtex0");if(zero==null)zero=sample(p,"shadowtex0HW");
 var one=sample(p,"shadowtex1");if(one==null)one=sample(p,"shadowtex1HW");
 return new ComplementarySamplerPass.Observation<>(zero,one,sample(p,"shadowcolor0"));
}

private static Map<String,Integer> locations(int program){
 var values=new HashMap<String,Integer>();
 for(String name:List.of("iris_currentAlphaTest","shaderlodBridgeVersion","shaderlodComplementaryReceiverPermission","shaderlodEnabled","shaderlodOptEarlyAlpha","shaderlodOptShadowReceiverFootprint","shaderlodOptTemporalShadowFilter","shaderlodOptimizationFlagsVersion","shaderlodPreset","shaderlodReimaginedEarlyAlphaPermission","shaderlodReimaginedEarlyAlphaReference","shaderlodReimaginedTemporalShadowSamplingQualified","shadowcolor0","shadowtex0","shadowtex0HW","shadowtex1","shadowtex1HW"))values.put(name,com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,name));
 return Map.copyOf(values);
}
private static int location(int program,String name){
 var receiver=ReimaginedReceiverCompiledPrograms.locations(program);
 if(receiver!=null)return receiver.getOrDefault(name,-1);
 var temporal=TEMPORAL_RECEIPTS.location(program,name);if(temporal!=null)return temporal;
 // A rejected/deleted native link cannot lend locations from an old integer owner.
 if(ReimaginedReceiverCompiledPrograms.nativeRegistration(program)||TEMPORAL_RECEIPTS.nativeProgram(program))return com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,name);
 var owner=OWNERS.get(program);var cached=owner==null?null:owner.locations().get(name);
 return cached==null?com.pp.shaderlod.optimization.sourcecache.LinkedUniformLocations.location(program,name):cached;
}
private static int integer(int p,String n){int l=location(p,n);return l<0?-1:GL20.glGetUniformi(p,l);}private static int param(int s,int n){return s==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,n):GL33.glGetSamplerParameteri(s,n);}private static ReimaginedEarlyAlphaContract.Sampler sample(int p,String n){int u=integer(p,n);if(u<0||u>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))return null;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+u);int t=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),s=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);var c=GL.getCapabilities();float a=(c.GL_EXT_texture_filter_anisotropic||c.GL_ARB_texture_filter_anisotropic)?(s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,0x84FE):GL33.glGetSamplerParameterf(s,0x84FE)):1;float bias=s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_LOD_BIAS):GL33.glGetSamplerParameterf(s,GL14.GL_TEXTURE_LOD_BIAS);return new ReimaginedEarlyAlphaContract.Sampler(t,GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT),param(s,GL11.GL_TEXTURE_MIN_FILTER),param(s,GL11.GL_TEXTURE_MAG_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL),param(s,GL14.GL_TEXTURE_COMPARE_MODE),param(s,GL14.GL_TEXTURE_COMPARE_FUNC),bias,s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MIN_LOD):GL33.glGetSamplerParameterf(s,GL12.GL_TEXTURE_MIN_LOD),s==0?GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LOD):GL33.glGetSamplerParameterf(s,GL12.GL_TEXTURE_MAX_LOD),a);}finally{GL13.glActiveTexture(active);}}
public static void revoke(){receiverGrant=null;
 int receiverCleared=0,temporalCleared=0;
 try{receiverCleared=RECEIVER_SAMPLERS.revokeGranted();}
 finally{
  try{temporalCleared=TEMPORAL_SAMPLERS.revokeGranted();}
  finally{
   revokeCurrent(receiverCleared,temporalCleared);
  }
 }
}
private static void revokeCurrent(int receiverCleared,int temporalCleared){
   int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
   if(p>0){
    try{int receiver=location(p,"shaderlodComplementaryReceiverPermission");if(receiver>=0&&receiverCleared!=p)GL20.glUniform1i(receiver,0);}
    finally{
     try{int temporal=location(p,"shaderlodReimaginedTemporalShadowSamplingQualified");if(temporal>=0&&temporalCleared!=p)GL20.glUniform1i(temporal,0);}
     finally{int alpha=location(p,"shaderlodReimaginedEarlyAlphaPermission");if(alpha>=0)GL20.glUniform1i(alpha,0);}
    }
   }
}public static void draw(){int p=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);receiverDraw(p);temporalDraw(p);alphaDraw(p);}
private static void alphaDraw(int p){if(p<=0)return;int l=location(p,"shaderlodReimaginedEarlyAlphaPermission");if(l<0)return;GL20.glUniform1i(l,0);failure="native/refused";try{var c=net.minecraft.client.Minecraft.getInstance();var core=OptimizationRuntimeContext.core();if(core==null||!core.settings().enabled()||core.preset()==LodPreset.QUALITY||!core.optimizations().enabled(OptimizationId.EARLY_ALPHA)||!OptimizationConfig.requested(OptimizationId.EARLY_ALPHA)||MakeUpQuadLifecycle.suspended()||!com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin.ReimaginedEarlyAlphaPlugin.exactApi()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()||c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())return;if(!MakeUpQuadRuntime.loadedStackMatches()||!MakeUpQuadRuntime.resourcesMatch(c.getResourcePackRepository().getSelectedIds()))return;var pipe=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);var own=OWNERS.get(p);if(own==null||own.pipeline()!=pipe||own.pack()!=pack||own.epoch()!=epoch||!(pipe instanceof IrisRenderingPipeline)||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;ready=sourceForDraw(own,pack);if(!ready||integer(p,"shaderlodBridgeVersion")!=2||integer(p,"shaderlodEnabled")!=1||integer(p,"shaderlodOptimizationFlagsVersion")!=1||integer(p,"shaderlodOptEarlyAlpha")!=1||!Set.of(0,1,2).contains(integer(p,"shaderlodPreset"))||GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)return;int alpha=location(p,"iris_currentAlphaTest"),privateRef=location(p,"shaderlodReimaginedEarlyAlphaReference");if(alpha<0||privateRef<0)return;float ref=GL20.glGetUniformf(p,alpha);if(!ReimaginedEarlyAlphaContract.alpha(ref)||GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE)||GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_ONE)||GL11.glIsEnabled(GL13.GL_SAMPLE_COVERAGE)||GL11.glIsEnabled(GL32.GL_SAMPLE_MASK))return;var view=(ShadowPipelineView)pipe;var shadows=view.shadowlod$shadows();var targets=view.shadowlod$targets();if(shadows==null||targets==null)return;int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);if(!Arrays.equals(v,new int[]{0,0,targets.getCurrentWidth(),targets.getCurrentHeight()}))return;var zero=sample(p,"shadowtex0");if(zero==null)zero=sample(p,"shadowtex0HW");var one=sample(p,"shadowtex1");if(one==null)one=sample(p,"shadowtex1HW");if(!ReimaginedEarlyAlphaContract.sampler(zero,shadows.getDepthTexture().getTextureId(),true)||!ReimaginedEarlyAlphaContract.sampler(one,shadows.getDepthTextureNoTranslucents().getTextureId(),true)||!ReimaginedEarlyAlphaContract.sampler(sample(p,"shadowcolor0"),shadows.getColorTextureId(0),false))return;GL20.glUniform1f(privateRef,ref);GL20.glUniform1i(l,1);qualifiedDraws++;if(CONTROL_PROOF){proofProgram=p;proofGrants++;proofGrantPermission=GL20.glGetUniformi(p,l);proofNativeReference=ref;proofPrivateReference=GL20.glGetUniformf(p,privateRef);}failure="qualified ownedCUTOUT/native alphaRef/samplers";}catch(RuntimeException|LinkageError e){GL20.glUniform1i(l,0);failure=e.getClass().getSimpleName();}}

 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{cachedSource(pack);}catch(Exception|LinkageError refused){}try{cachedTemporalSource(pack);}catch(Exception|LinkageError refused){}try{cachedReceiverSource(pack);}catch(Exception|LinkageError refused){}}
}
