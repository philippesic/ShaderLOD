package com.pp.shaderlod.optimization.makeupshadow;

import java.util.Map;
import java.util.Set;
import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.sourcecache.NativePassSamplerCache;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;

/** Sampler observations only; every source/configuration/permission guard remains live. */
public final class MakeUpShadowSamplerPass {
 private MakeUpShadowSamplerPass(){}
 private static final boolean DISABLED=Boolean.getBoolean("shaderlod.dev.disableShadowSamplerReuse");
 private static final NativePassSamplerCache<Key,Observation> CACHE=new NativePassSamplerCache<>();
 private static final ThreadLocal<TerrainRenderPass> PASS=new ThreadLocal<>();
 private static final Set<OptimizationId> ALLOWED=Set.of(OptimizationId.SHADOW_SAMPLE_BUDGET,
  OptimizationId.BINDING_CERTIFICATE_CACHE,OptimizationId.SHADOW_ADMISSION_CACHE,
  OptimizationId.QUAD_COMMAND_COMPACTION,OptimizationId.INDEX_SCALAR_CACHE);
 private static long captures,reuses;
 public record Reading(int unit,int texture,int sampler,MakeUpColoredShadowContract.Sampler state){}
 public record Observation(Reading zero,Reading one,Reading color){}
 /** All reference-bearing fields are compared by identity, including program/link ownership. */
 public record Key(Object program,Object pipeline,Object pack,Object context,Object targets,Object shadows,
  Object depth0,Object depth1,Object color0,int depth0Id,int depth1Id,int color0Id,int resolution,long epoch){
  @Override public boolean equals(Object other){return other instanceof Key k&&program==k.program&&pipeline==k.pipeline&&pack==k.pack&&context==k.context&&targets==k.targets&&shadows==k.shadows&&depth0==k.depth0&&depth1==k.depth1&&color0==k.color0&&depth0Id==k.depth0Id&&depth1Id==k.depth1Id&&color0Id==k.color0Id&&resolution==k.resolution&&epoch==k.epoch;}
  @Override public int hashCode(){return System.identityHashCode(program);}
 }
 public static boolean compiledProfile(Map<String,String> values){
  values=com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.withoutInactiveControl(values);if(values==null)return false;
  for(var name:java.util.List.of("SHADERLOD_MAKEUP_EARLY_ALPHA","SHADERLOD_MAKEUP_AO_HALF_RES","SHADERLOD_MAKEUP_DEPTH_PRIMING","SHADERLOD_MAKEUP_SPATIAL_REUSE","SHADERLOD_MAKEUP_SPATIAL_RATE","SHADERLOD_MAKEUP_WORLD_PIXEL"))
   if(!"0".equals(values.get(name)))return false;
  return true;
 }
 public static boolean allowed(CoreUniformFrame.Core core){
  if(DISABLED||core==null||!core.settings().enabled()||core.preset()!=LodPreset.PERFORMANCE
    ||!"makeup-ultra-fast-shaders".equals(OptimizationConfig.activeFamily())
    ||!MakeUpShadowScopePins.exact()||!MakeUpShadowScopePins.bootstrapSafe()
    ||!OptimizationConfig.requested(OptimizationId.SHADOW_SAMPLE_BUDGET)
    ||!core.optimizations().enabled(OptimizationId.SHADOW_SAMPLE_BUDGET))return false;
  return allowedFlags(OptimizationConfig::requested,core.optimizations()::enabled);
 }
 public static boolean allowedFlags(java.util.function.Predicate<OptimizationId> requested,java.util.function.Predicate<OptimizationId> enabled){
  for(var id:OptimizationId.values())if(!ALLOWED.contains(id)&&(requested.test(id)||enabled.test(id)))return false;
  return true;
 }
 public static final class Scope implements AutoCloseable {
  private final AutoCloseable scope;private final TerrainRenderPass previous;
  private Scope(AutoCloseable scope,TerrainRenderPass previous){this.scope=scope;this.previous=previous;}
  public void close(){try{scope.close();}catch(RuntimeException|Error failure){throw failure;}catch(Exception impossible){throw new IllegalStateException(impossible);}finally{if(previous==null)PASS.remove();else PASS.set(previous);}}
 }
 public static Scope open(TerrainRenderPass pass){
  var previous=PASS.get();var scope=CACHE.open((pass==DefaultTerrainRenderPasses.SOLID||pass==DefaultTerrainRenderPasses.CUTOUT)
    &&!net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()
    &&allowed(OptimizationRuntimeContext.core()));PASS.set(pass);return new Scope(scope,previous);
 }
 public static boolean matchesPass(SodiumPrograms.Pass pass){return pass==SodiumPrograms.Pass.TERRAIN&&PASS.get()==DefaultTerrainRenderPasses.SOLID
  ||pass==SodiumPrograms.Pass.TERRAIN_CUTOUT&&PASS.get()==DefaultTerrainRenderPasses.CUTOUT;}
 public static void arm(){CACHE.arm();}
 public static void invalidate(){CACHE.invalidate();}
 public static Observation reuse(Key key){var result=CACHE.reuse(key);if(result!=null)reuses++;return result;}
 public static void remember(Key key,Observation value){CACHE.remember(key,value);}
 public static void captured(){captures++;}
 public static void granted(int program){CACHE.granted(()->MakeUpShadowRuntime.revoke(program));}
 public static void revoked(){CACHE.revoked();}
 public static long captures(){return captures;}
 public static long reuses(){return reuses;}
 public static boolean active(){return CACHE.active();}
}
