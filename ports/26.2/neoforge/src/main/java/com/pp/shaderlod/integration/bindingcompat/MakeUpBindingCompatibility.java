package com.pp.shaderlod.integration.bindingcompat;
import java.util.*;
import com.google.gson.JsonObject;
import net.irisshaders.iris.Iris;
import com.pp.shaderlod.integration.makeupquad.*;
import net.caffeinemc.mods.sodium.mixin.core.GlRenderPassAccessor;
/** Exact NF26.2 MakeUp/BSL/Reimagined/Unbound/Sildur/Bliss/Photon input compatibility; never bypasses native draw validation or source/shape admission. */
public final class MakeUpBindingCompatibility {
 public static final String PROBE_PROPERTY="shaderlod.bindingCompatibilityProbe";
 private static final boolean PROBE=Boolean.getBoolean(PROBE_PROPERTY);
 public record Context(Object pipeline,Object[] boundSlots){}
 private static Context current;
 private static final BindingInputCertificateCache CACHE=new BindingInputCertificateCache();
 private enum SourceFamily { MAKEUP, BSL, REIMAGINED, UNBOUND, SILDUR, BLISS, PHOTON }
 private static SourceFamily sourceFamily;
 private static Object sourcePack,sourceValues,sourceWorld,sourcePipeline;private static String sourceName,originalName;private static long sourceGeneration=-1,stampBucket=Long.MIN_VALUE;
 private static Map<String,Boolean> rawBooleans=Map.of();private static Map<String,String> rawStrings=Map.of(),effective=Map.of();private static Object stamp;private static Boolean stackMatch;private static String reason="not-observed";
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
  if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!com.pp.shaderlod.ShaderLodConfig.SPEC.isLoaded()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()||!MakeUpQuadApiPins.exactApi()||!BindingInputPins.match()||!MakeUpQuadRuntime.resourcesMatch(c.getResourcePackRepository().getSelectedIds()))return;
  String backend=((net.irisshaders.iris.mixin.GpuDeviceAccessor)com.mojang.blaze3d.systems.RenderSystem.getDevice()).getBackend().getDeviceInfo().backendName();if(!backend.equals("OpenGL"))return;
  if(stackMatch==null){var m=new TreeMap<String,String>();for(var mod:net.neoforged.fml.ModList.get().getMods())m.put(mod.getModId(),mod.getVersion().toString());stackMatch=MakeUpQuadRuntime.stackMatches(m);}if(!stackMatch)return;
  var pack=Iris.getCurrentPack().orElse(null);var pipeline=Iris.getPipelineManager().getPipelineNullable();if(pack==null||pipeline==null||pipeline.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class)return;
  var opts=pack.getShaderPackOptions();var values=opts.getOptionValues();if(values.getClass()!=net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class)return;var mutable=(net.irisshaders.iris.shaderpack.option.values.MutableOptionValues)values;String name=Iris.getCurrentPackName();long generation=MakeUpQuadLifecycle.generation();
  if(sourceFamily==null||pack!=sourcePack||values!=sourceValues||c.level!=sourceWorld||pipeline!=sourcePipeline||generation!=sourceGeneration||!Objects.equals(name,sourceName)||!mutable.getBooleanValues().equals(rawBooleans)||!mutable.getStringValues().equals(rawStrings)){
   CACHE.invalidate();var map=new TreeMap<String,String>();for(String key:opts.getOptionSet().getBooleanOptions().keySet())map.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));for(String key:opts.getOptionSet().getStringOptions().keySet())map.put(key,values.getStringValueOrDefault(key));effective=Map.copyOf(map);sourceFamily=family(effective);originalName=originalName(sourceFamily,Iris.getShaderpacksDirectory(),name,effective);
   sourcePack=pack;sourceValues=values;sourceWorld=c.level;sourcePipeline=pipeline;sourceGeneration=generation;sourceName=name;rawBooleans=Map.copyOf(mutable.getBooleanValues());rawStrings=Map.copyOf(mutable.getStringValues());stampBucket=Long.MIN_VALUE;
  }
  long bucket=System.nanoTime()/50_000_000L;if(bucket!=stampBucket){stamp=sourceStamp(sourceFamily,Iris.getShaderpacksDirectory(),name,originalName);stampBucket=bucket;if(PROBE)sourceStampReads++;}
  // Source/vertex compatibility is independent of Core commit order during native return selection.
  boolean transition=Iris.getIrisConfig().getShaderPackName().orElse("").equals(originalName);Object key=List.of(sourceFamily,stamp,transition);
  if(!CACHE.matches(pack,pipeline,c.level,values,generation,name,mutable.getBooleanValues(),mutable.getStringValues(),c.getResourcePackRepository().getSelectedIds(),key)){
   CACHE.invalidate();var certificate=certificate(sourceFamily,Iris.getShaderpacksDirectory(),name,originalName,effective,transition);reason=certificate.reason();CACHE.remember(pack,pipeline,c.level,values,generation,name,mutable.getBooleanValues(),mutable.getStringValues(),c.getResourcePackRepository().getSelectedIds(),key,certificate.eligible());if(PROBE)certificateRefreshes++;
  }else if(PROBE)certificateReuses++;
  if(!CACHE.eligible()||MakeUpQuadLifecycle.suspended())return;
  var compiled=((GlRenderPassAccessor)pass).getPipeline();var slots=((com.pp.shaderlod.integration.bindingcompat.mixin.BindingGlPassAccessor)pass).shaderlod$vertexBuffers();
  if(compiled==null||compiled.info()==null||slots==null||slots.length!=16||!compiled.info().getClass().getName().equals("com.mojang.blaze3d.pipeline.RenderPipeline"))return;
  current=new Context(compiled.info(),slots);
 }catch(Exception|LinkageError failure){CACHE.invalidate();sourceFamily=null;reason=failure.toString();}}
 private record SourceResult(boolean eligible,String reason){}
 /** Candidate family selection is NOT admission: exact archive/recipe/state certification follows. */
 private static SourceFamily family(Map<String,String> options){
  boolean makeup=MakeUpQuadEligibility.optionsMatch(options)||MakeUpSourceCertificate.generatedOptions().equals(options);
  boolean bsl=com.pp.shaderlod.integration.bslquad.BslQuadEligibility.optionsMatch(options)||BslSourceCertificate.generatedOptions().equals(options);
  boolean reimagined=com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.optionsMatch(options)||ReimaginedSourceCertificate.generatedOptions().equals(options);
  boolean unbound=com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.optionsMatch(options)||UnboundSourceCertificate.generatedOptions().equals(options);
  boolean sildur=com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.optionsMatch(options)||SildurSourceCertificate.generatedOptions().equals(options);
  boolean bliss=com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.optionsMatch(options)||BlissSourceCertificate.generatedOptions().equals(options);
  boolean photon=PhotonSourceCertificate.optionsMatch(options)||PhotonSourceCertificate.generatedOptions().equals(options);
  int matches=(makeup?1:0)+(bsl?1:0)+(reimagined?1:0)+(unbound?1:0)+(sildur?1:0)+(bliss?1:0)+(photon?1:0);
  if(matches!=1)throw new IllegalStateException("Unknown/ambiguous complete source option family");return makeup?SourceFamily.MAKEUP:bsl?SourceFamily.BSL:reimagined?SourceFamily.REIMAGINED:unbound?SourceFamily.UNBOUND:sildur?SourceFamily.SILDUR:bliss?SourceFamily.BLISS:SourceFamily.PHOTON;
 }
 private static String originalName(SourceFamily family,java.nio.file.Path dir,String name,Map<String,String> options)throws Exception{
  return switch(family){case MAKEUP->MakeUpSourceCertificate.originalName(dir,name,options);case BSL->BslSourceCertificate.originalName(dir,name,options);case REIMAGINED->ReimaginedSourceCertificate.originalName(dir,name,options);case UNBOUND->UnboundSourceCertificate.originalName(dir,name,options);case SILDUR->SildurSourceCertificate.originalName(dir,name,options);case BLISS->BlissSourceCertificate.originalName(dir,name,options);case PHOTON->PhotonSourceCertificate.originalName(dir,name,options);};
 }
 private static Object sourceStamp(SourceFamily family,java.nio.file.Path dir,String name,String original)throws Exception{
  return switch(family){case MAKEUP->MakeUpSourceCertificate.stamp(dir,name,original);case BSL->BslSourceCertificate.stamp(dir,name,original);case REIMAGINED->ReimaginedSourceCertificate.stamp(dir,name,original);case UNBOUND->UnboundSourceCertificate.stamp(dir,name,original);case SILDUR->SildurSourceCertificate.stamp(dir,name,original);case BLISS->BlissSourceCertificate.stamp(dir,name,original);case PHOTON->PhotonSourceCertificate.stamp(dir,name,original);};
 }
 private static SourceResult certificate(SourceFamily family,java.nio.file.Path dir,String name,String original,Map<String,String> options,boolean transition){
  return switch(family){case MAKEUP->{var result=MakeUpSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case BSL->{var result=BslSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case REIMAGINED->{var result=ReimaginedSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case UNBOUND->{var result=UnboundSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case SILDUR->{var result=SildurSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case BLISS->{var result=BlissSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}case PHOTON->{var result=PhotonSourceCertificate.validate(dir,name,original,options,transition);yield new SourceResult(result.eligible(),result.reason());}};
 }
 public static void corrected(Object[] before,Object[] after){if(!PROBE)return;correctedQueries++;if(shadow())shadowCorrectedQueries++;else ordinaryCorrectedQueries++;lastIrisDescriptorSlots=before.length;lastCorrectedDescriptorSlots=after.length;lastIrisSlotZeroIdentity=System.identityHashCode(before[0]);lastCorrectedSlotZeroIdentity=System.identityHashCode(after[0]);}
 public static void completedDrawBody(){if(!PROBE||current==null)return;completedDrawBodies++;if(shadow())shadowCompletedDrawBodies++;else ordinaryCompletedDrawBodies++;}
 public static void glFunctionEntry(){if(!PROBE||current==null)return;glFunctionEntries++;if(shadow())shadowGlFunctionEntries++;else ordinaryGlFunctionEntries++;}
 public static JsonObject observe(){var o=new JsonObject();o.addProperty("probeEnabled",PROBE);o.addProperty("validationPolicyMutated",false);o.addProperty("productionCorrectionRequested",!Boolean.getBoolean("shaderlod.dev.portBindingInputFix"));o.addProperty("scope","NF26.2 separate exactMakeUp/BSL/Reimagined/Unbound/Sildur/Bliss/Photon source/live16sole0; plural GLentry, all4 nativebody returns; not GPU completion");o.addProperty("sourceCertificateReason",reason);o.addProperty("sourceCertificateEligible",CACHE.eligible());o.addProperty("sourceFamily",sourceFamily==null?"unqualified":sourceFamily.name().toLowerCase(java.util.Locale.ROOT));
  o.addProperty("correctedQueries",correctedQueries);o.addProperty("shadowCorrectedQueries",shadowCorrectedQueries);o.addProperty("ordinaryCorrectedQueries",ordinaryCorrectedQueries);o.addProperty("glFunctionEntries",glFunctionEntries);o.addProperty("shadowGlFunctionEntries",shadowGlFunctionEntries);o.addProperty("ordinaryGlFunctionEntries",ordinaryGlFunctionEntries);o.addProperty("completedDrawBodies",completedDrawBodies);o.addProperty("shadowCompletedDrawBodies",shadowCompletedDrawBodies);o.addProperty("ordinaryCompletedDrawBodies",ordinaryCompletedDrawBodies);o.addProperty("lastIrisDescriptorSlots",lastIrisDescriptorSlots);o.addProperty("lastCorrectedDescriptorSlots",lastCorrectedDescriptorSlots);o.addProperty("lastIrisSlotZeroIdentity",lastIrisSlotZeroIdentity);o.addProperty("lastCorrectedSlotZeroIdentity",lastCorrectedSlotZeroIdentity);o.addProperty("certificateRefreshes",certificateRefreshes);o.addProperty("certificateReuses",certificateReuses);o.addProperty("sourceStampReads",sourceStampReads);return o;
 }
}
