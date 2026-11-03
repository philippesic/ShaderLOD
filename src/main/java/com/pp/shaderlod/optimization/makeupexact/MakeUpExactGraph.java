package com.pp.shaderlod.optimization.makeupexact;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import java.util.*;
/** Pinned source-only exact singletons. No per-draw admission or synchronous GL reads. */
public final class MakeUpExactGraph {
 public static final String DEFINE="SHADERLOD_G06_ASAHI_RUNTIME_GUARD";
 public static final String G04_DEFINE="SHADERLOD_G04_ASAHI_RUNTIME_GUARD";
 private MakeUpExactGraph() {}
 /** Setup-only, spoof-filtered capability; native100/source/flags remain independent gates. */
 public static com.google.common.collect.ImmutableList<net.irisshaders.iris.helpers.StringPair> constructorDefines(
   java.util.List<net.irisshaders.iris.helpers.StringPair> input,boolean exactApi) {
  var result=new java.util.ArrayList<net.irisshaders.iris.helpers.StringPair>();
  for(var define:input)if(!DEFINE.equals(define.key())&&!G04_DEFINE.equals(define.key())&&!"SHADERLOD_COMBINATION_RUNTIME_GUARD".equals(define.key())&&!"SHADERLOD_COMBINATION_BLOOM_RUNTIME_GUARD".equals(define.key()))result.add(define);
  if(exactApi && net.neoforged.fml.ModList.get()!=null && com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaSource.host(
      System.getProperty("os.name"),System.getProperty("os.arch"),
      org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER),
      org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION))
    &&com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlpha.stackMatches(
      com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.loadedStack()))
   {result.add(new net.irisshaders.iris.helpers.StringPair(DEFINE,"1"));
    result.add(new net.irisshaders.iris.helpers.StringPair(G04_DEFINE,"1"));
    if(MakeUpCombinationGraph.enabled()){result.add(new net.irisshaders.iris.helpers.StringPair("SHADERLOD_COMBINATION_RUNTIME_GUARD","1"));
     if(Boolean.getBoolean("shaderlod.dev.shortlistG10Runtime"))result.add(new net.irisshaders.iris.helpers.StringPair("SHADERLOD_COMBINATION_BLOOM_RUNTIME_GUARD","1"));}}
  return com.google.common.collect.ImmutableList.copyOf(result);
 }
 public static boolean only(OptimizationSnapshot flags,OptimizationId selected) {
  if(MakeUpCombinationGraph.selected(flags,selected))return true;
  if(flags==null)return false;
  for(var id:OptimizationId.values())if(flags.enabled(id)!=(id==selected))return false;
  return true;
 }
 public static boolean singleton(OptimizationSnapshot flags) {
  return only(flags,OptimizationId.TRANSLUCENT_FASTPATH)||only(flags,OptimizationId.AO_ATTENUATION);
 }
 public static boolean eligible(BslLeanGraph.Route r,boolean enabled,LodPreset preset) {
  return enabled&&preset==LodPreset.PERFORMANCE&&r.loaded()&&r.exactCompiler()&&r.overworld()
   &&"makeup-ultra-fast-shaders".equals(r.family())&&singleton(r.requests());
 }
 public static boolean nativeProfile(Map<String,CompileOptionControl.NativeOption> typed) {
  if(typed==null)return false;
  var values=new TreeMap<String,String>();typed.forEach((k,v)->values.put(k,v.value()));
  if(MakeUpQuadEligibility.optionsMatch(values))return true;
  return transitionProfile(values);
 }
 public static boolean profile(Map<String,String> actual) {
  if(actual==null)return false;
  var n=new TreeMap<>(actual);
  for(String key:List.of(CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH,CompileOptionControl.MAKEUP_AO_ATTENUATION)) {
   if(!Set.of("0","1").contains(n.getOrDefault(key,"invalid")))return false;
   n.put(key,"0");
  }
  if(!MakeUpCombinationGraph.enabled()&&"1".equals(actual.get(CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH))&&"1".equals(actual.get(CompileOptionControl.MAKEUP_AO_ATTENUATION)))return false;
  return MakeUpLeanGraph.profile(n);
 }
 public static boolean transitionProfile(Map<String,String> actual) {
  if(actual==null||!Set.of("0","1").contains(actual.getOrDefault(BslLeanGraph.ADAPTER,"invalid")))return false;
  var n=new TreeMap<>(actual);n.put(BslLeanGraph.ADAPTER,"0");return profile(n);
 }
}
