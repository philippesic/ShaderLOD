package com.pp.shaderlod.integration.bindingcompat;
import java.util.*;
import com.google.gson.JsonObject;
import net.irisshaders.iris.Iris;
import com.pp.shaderlod.integration.makeupquad.*;
import net.caffeinemc.mods.sodium.mixin.core.GlRenderPassAccessor;
/** Exact Fabric26.2 MakeUp/BSL/Reimagined/Unbound/Sildur/Bliss/Photon/AstraLex input compatibility; never bypasses native draw validation or source/shape admission. */
public final class MakeUpBindingCompatibility {
 public static final String PROBE_PROPERTY="shaderlod.bindingCompatibilityProbe";
 private static final boolean PROBE=Boolean.getBoolean(PROBE_PROPERTY);
 public record Context(Object pipeline,Object[] boundSlots){}
 private static Context current;
 private enum SourceFamily { MAKEUP, BSL, REIMAGINED, UNBOUND, SILDUR, BLISS, PHOTON, ASTRA, SOLAS }
 private static SourceFamily sourceFamily;
 private static Object sourcePack;private static Boolean stackMatch;private static String reason="not-observed";
 private static long correctedQueries,shadowCorrectedQueries,ordinaryCorrectedQueries,glFunctionEntries,shadowGlFunctionEntries,ordinaryGlFunctionEntries,completedDrawBodies,shadowCompletedDrawBodies,ordinaryCompletedDrawBodies,certificateRefreshes,certificateReuses,sourceStampReads;
 private static int lastIrisDescriptorSlots,lastCorrectedDescriptorSlots,lastIrisSlotZeroIdentity,lastCorrectedSlotZeroIdentity;
 private MakeUpBindingCompatibility(){}
 public static Context current(){return current;}
 public static Context snapshot(){return current;}
 public static void restore(Context prior){current=prior;}
 private static boolean shadow(){return net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered();}
 public static void enter(Object pass){current=null;try{
  if(Boolean.getBoolean("shaderlod.dev.portBindingInputFix")||MakeUpQuadLifecycle.suspended()||!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()||!BindingInputContract.knownPassClass(pass.getClass().getName()))return;
  var c=net.minecraft.client.Minecraft.getInstance();
  if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!com.pp.shaderlod.fabric.FabricLodConfig.loaded()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()||!MakeUpQuadApiPins.exactApi()||!BindingInputPins.match()||!MakeUpQuadRuntime.resourcesMatch(c.getResourcePackRepository().getSelectedIds()))return;
  String backend=((net.irisshaders.iris.mixin.GpuDeviceAccessor)com.mojang.blaze3d.systems.RenderSystem.getDevice()).getBackend().getDeviceInfo().backendName();if(!backend.equals("OpenGL"))return;
  if(stackMatch==null){var m=new TreeMap<String,String>();for(var mod:net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods())m.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString());stackMatch=MakeUpQuadRuntime.stackMatches(m);}if(!stackMatch)return;
  var pack=Iris.getCurrentPack().orElse(null);var pipeline=Iris.getPipelineManager().getPipelineNullable();if(pack==null||pipeline==null||pipeline.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class)return;
  // The source proof belongs to this successfully built pipeline. Rendering never opens its archive.
  if(!com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(List.of(MakeUpBindingCompatibility.class,pack),()->false))return;
  if(!sourceEligible||MakeUpQuadLifecycle.suspended())return;
  var compiled=((GlRenderPassAccessor)pass).getPipeline();var slots=((com.pp.shaderlod.integration.bindingcompat.mixin.BindingGlPassAccessor)pass).shaderlod$vertexBuffers();
  if(compiled==null||compiled.info()==null||slots==null||slots.length!=16||!compiled.info().getClass().getName().equals("com.mojang.blaze3d.pipeline.RenderPipeline"))return;
  current=new Context(compiled.info(),slots);
 }catch(Exception|LinkageError failure){current=null;reason=failure.toString();}}
 private static boolean sourceEligible;
 /** Called only inside the authenticated Iris pipeline build transaction, before publication. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){
  sourceEligible=false;current=null;
  com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(List.of(MakeUpBindingCompatibility.class,pack),()->{
   var options=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());
   var family=family(options);var directory=Iris.getShaderpacksDirectory();var name=Iris.getIrisConfig().getShaderPackName().orElseThrow();
   var original=originalName(family,directory,name,options);
   Object before=sourceStamp(family,directory,name,original);if(PROBE)sourceStampReads++;
   boolean transition=name.equals(original);
   var result=certificate(family,directory,name,original,options,transition);
   Object after=sourceStamp(family,directory,name,original);if(PROBE)sourceStampReads++;
   boolean accepted=result.eligible()&&before.equals(after);
   reason=result.reason();sourceFamily=family;
   sourcePack=pack;sourceEligible=accepted;if(PROBE)certificateRefreshes++;
   return accepted;
  });
 }
 private record SourceResult(boolean eligible,String reason){}
 /** Candidate family selection is NOT admission: exact archive/recipe/state certification follows. */
 private static SourceFamily family(Map<String,String> options){
  boolean makeup=MakeUpSourceCertificate.nativeOptionsMatchKnown(options)||MakeUpSourceCertificate.generatedOptionsMatch(options);
  boolean bsl=com.pp.shaderlod.integration.bslquad.BslQuadEligibility.optionsMatch(options)||com.pp.shaderlod.optimization.bslprefix.BslPrefixProfile.nativeSource(options)||BslSourceCertificate.generatedOptionsMatch(options);
  boolean reimagined=com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.optionsMatch(options)||ReimaginedSourceCertificate.generatedOptions().equals(options);
  boolean unbound=com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.optionsMatch(options)||UnboundSourceCertificate.generatedOptions().equals(options);
  boolean sildur=com.pp.shaderlod.integration.sildurquad.SildurSourceCertificate.nativeSourceProfileOptionsMatch(options)||SildurSourceCertificate.generatedOptionsMatch(options);
  boolean bliss=BlissSourceCertificate.optionsMatchKnownNative(options)||BlissSourceCertificate.optionsMatchGenerated(options);
  boolean photon=PhotonSourceCertificate.optionsMatch(options)||PhotonSourceCertificate.generatedOptionsMatch(options);
  boolean astra=(com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.nativeOptions().size()==1015&&com.pp.shaderlod.integration.astraquad.AstraQuadEligibility.nativeOptions().equals(options))||AstraSourceCertificate.generatedOptionsMatch(options);
  boolean solas=SolasSourceCertificate.nativeOptionsMatch(options)||SolasSourceCertificate.generatedOptionsMatch(options);
  int matches=(makeup?1:0)+(bsl?1:0)+(reimagined?1:0)+(unbound?1:0)+(sildur?1:0)+(bliss?1:0)+(photon?1:0)+(astra?1:0)+(solas?1:0);
  if(matches!=1)throw new IllegalStateException("Unknown/ambiguous complete source option family");return makeup?SourceFamily.MAKEUP:bsl?SourceFamily.BSL:reimagined?SourceFamily.REIMAGINED:unbound?SourceFamily.UNBOUND:sildur?SourceFamily.SILDUR:bliss?SourceFamily.BLISS:photon?SourceFamily.PHOTON:astra?SourceFamily.ASTRA:SourceFamily.SOLAS;
 }
 private static String originalName(SourceFamily family,java.nio.file.Path dir,String name,Map<String,String> options)throws Exception{
  return switch(family){case MAKEUP->MakeUpSourceCertificate.originalName(dir,name,options);case BSL->BslSourceCertificate.originalName(dir,name,options);case REIMAGINED->ReimaginedSourceCertificate.originalName(dir,name,options);case UNBOUND->UnboundSourceCertificate.originalName(dir,name,options);case SILDUR->SildurSourceCertificate.originalName(dir,name,options);case BLISS->BlissSourceCertificate.originalName(dir,name,options);case PHOTON->PhotonSourceCertificate.originalName(dir,name,options);case ASTRA->AstraSourceCertificate.originalName(dir,name,options);case SOLAS->SolasSourceCertificate.originalName(dir,name,options);};
 }
 private static Object sourceStamp(SourceFamily family,java.nio.file.Path dir,String name,String original)throws Exception{
  return switch(family){case MAKEUP->MakeUpSourceCertificate.stamp(dir,name,original);case BSL->BslSourceCertificate.stamp(dir,name,original);case REIMAGINED->ReimaginedSourceCertificate.stamp(dir,name,original);case UNBOUND->UnboundSourceCertificate.stamp(dir,name,original);case SILDUR->SildurSourceCertificate.stamp(dir,name,original);case BLISS->BlissSourceCertificate.stamp(dir,name,original);case PHOTON->PhotonSourceCertificate.stamp(dir,name,original);case ASTRA->AstraSourceCertificate.stamp(dir,name,original);case SOLAS->SolasSourceCertificate.stamp(dir,name,original);};
 }
 private static SourceResult certificate(SourceFamily family,java.nio.file.Path dir,String name,String original,Map<String,String> options,boolean transition){
  return switch(family){case MAKEUP->{var result=MakeUpSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case BSL->{var result=BslSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case REIMAGINED->{var result=ReimaginedSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case UNBOUND->{var result=UnboundSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case SILDUR->{var result=SildurSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case BLISS->{var result=BlissSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case PHOTON->{var result=PhotonSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case ASTRA->{var result=AstraSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case SOLAS->{var result=SolasSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}};
 }
 public static void corrected(Object[] before,Object[] after){if(!PROBE)return;correctedQueries++;if(shadow())shadowCorrectedQueries++;else ordinaryCorrectedQueries++;lastIrisDescriptorSlots=before.length;lastCorrectedDescriptorSlots=after.length;lastIrisSlotZeroIdentity=System.identityHashCode(before[0]);lastCorrectedSlotZeroIdentity=System.identityHashCode(after[0]);}
 public static void completedDrawBody(){if(!PROBE||current==null)return;completedDrawBodies++;if(shadow())shadowCompletedDrawBodies++;else ordinaryCompletedDrawBodies++;}
 public static void glFunctionEntry(){if(!PROBE||current==null)return;glFunctionEntries++;if(shadow())shadowGlFunctionEntries++;else ordinaryGlFunctionEntries++;}
 public static JsonObject observe(){var o=new JsonObject();o.addProperty("probeEnabled",PROBE);o.addProperty("validationPolicyMutated",false);o.addProperty("bindingCertificateCacheRequested",com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.BINDING_CERTIFICATE_CACHE));o.addProperty("bindingDigestStreamingRequested",com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.BINDING_DIGEST_STREAMING));o.addProperty("bindingCertificateImplementation","Fabric26.2 source proof published with the consumed pack/pipeline and immutable load inputs; live binding/resource guards remain mandatory");o.addProperty("bindingDigestImplementation",com.pp.shaderlod.optimization.SourceArchiveDigest.implementation());o.addProperty("bindingScope","native9 full archive certificates/setup; Fabric26.2 load-owned exact archive/recipe/state proof + observed Iris raw graph/properties; steady lookup has no source IO");o.addProperty("productionCorrectionRequested",!Boolean.getBoolean("shaderlod.dev.portBindingInputFix"));o.addProperty("scope","Fabric26.2 separate exactMakeUp/BSL/Reimagined/Unbound/Sildur/Bliss/Photon/AstraLex/Solas source/live16sole0; plural GLentry, all4 nativebody returns; not GPU completion");o.addProperty("sourceCertificateReason",reason);o.addProperty("sourceCertificateEligible",sourceEligible&&sourcePack!=null&&com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(List.of(MakeUpBindingCompatibility.class,sourcePack),()->false));o.addProperty("sourceFamily",sourceFamily==null?"unqualified":sourceFamily.name().toLowerCase(java.util.Locale.ROOT));
  o.addProperty("correctedQueries",correctedQueries);o.addProperty("shadowCorrectedQueries",shadowCorrectedQueries);o.addProperty("ordinaryCorrectedQueries",ordinaryCorrectedQueries);o.addProperty("glFunctionEntries",glFunctionEntries);o.addProperty("shadowGlFunctionEntries",shadowGlFunctionEntries);o.addProperty("ordinaryGlFunctionEntries",ordinaryGlFunctionEntries);o.addProperty("completedDrawBodies",completedDrawBodies);o.addProperty("shadowCompletedDrawBodies",shadowCompletedDrawBodies);o.addProperty("ordinaryCompletedDrawBodies",ordinaryCompletedDrawBodies);o.addProperty("lastIrisDescriptorSlots",lastIrisDescriptorSlots);o.addProperty("lastCorrectedDescriptorSlots",lastCorrectedDescriptorSlots);o.addProperty("lastIrisSlotZeroIdentity",lastIrisSlotZeroIdentity);o.addProperty("lastCorrectedSlotZeroIdentity",lastCorrectedSlotZeroIdentity);o.addProperty("certificateRefreshes",certificateRefreshes);o.addProperty("certificateReuses",certificateReuses);o.addProperty("sourceStampReads",sourceStampReads);return o;
 }
}
