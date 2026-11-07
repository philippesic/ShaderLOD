package com.pp.shaderlod.optimization;
import com.pp.shaderlod.*;
import com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoContract;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpExactGraphTest {
 @Test void windowsExactPathsRequireTheFiniteConstructorCapability()throws Exception {
  String fragment;
  try(var in=getClass().getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/makeup-exact/shaders/lib/g04/water_blocks_fragment.glsl")){
   assertNotNull(in);fragment=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  }
  String condition=fragment.lines().map(String::trim).filter(s->s.startsWith("#if SHADERLOD_G04_TRANSLUCENT_FASTPATH")).findFirst().orElseThrow();
  String base="#define SHADERLOD_G04_TRANSLUCENT_FASTPATH 1\n#define IS_IRIS\n#define IRIS_VERSION 10812\n#define MC_VERSION 12101\n#define WATER_F\n#define V_CLOUDS 1\n#define CLOUD_VOL_STYLE 0\n#define CLOUD_STEPS_AVG 10\n#define AA_TYPE 2\n";
  for(String os:List.of("MC_OS_WINDOWS","MC_OS_LINUX"))for(boolean cap:List.of(false,true)){
   String input=base+"#define "+os+"\n"+(cap?"#define SHADERLOD_G04_ASAHI_RUNTIME_GUARD\n":"")+condition+"\nQUALIFIED\n#endif\n";
   assertEquals(cap,net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor.glslPreprocessSource(input,List.of()).contains("QUALIFIED"),os+" capability="+cap);
  }
  try(var in=getClass().getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")){
   assertNotNull(in);String adapter=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   assertTrue(adapter.contains("((defined MC_OS_LINUX || defined MC_OS_WINDOWS) && ((SHADERLOD_G06_AO_ATTENUATION"),"vertex and fragment must share the finite host route");
  }
 }
 @Test void onlyExactSingletonAndPinnedRouteSelectNativeQuality() {
  for(var id:List.of(OptimizationId.TRANSLUCENT_FASTPATH,OptimizationId.AO_ATTENUATION)) {
   var f=OptimizationSnapshot.noneRequested().with(id,true);
   var r=new BslLeanGraph.Route(f,true,"makeup-ultra-fast-shaders",true,true);
   assertTrue(MakeUpExactGraph.eligible(r,true,LodPreset.PERFORMANCE));
   assertFalse(MakeUpExactGraph.eligible(r,false,LodPreset.PERFORMANCE));
   assertFalse(MakeUpExactGraph.eligible(r,true,LodPreset.QUALITY));
   assertFalse(MakeUpExactGraph.eligible(new BslLeanGraph.Route(f,true,r.family(),false,true),true,LodPreset.PERFORMANCE));
   for(var peer:OptimizationId.values())if(peer!=id)assertFalse(MakeUpExactGraph.singleton(f.with(peer,true)),peer.key());
  }
 }
 @Test void exactFullMapRejectsMutationsAndActivePeers() {
  var m=new TreeMap<>(MakeUpAoContract.generated("0"));m.put(BslLeanGraph.ADAPTER,"0");
  assertTrue(MakeUpExactGraph.profile(m));
  for(String k:List.of(CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH,CompileOptionControl.MAKEUP_AO_ATTENUATION)) {
   var on=new TreeMap<>(m);on.put(k,"1");assertTrue(MakeUpExactGraph.profile(on));
   on.put("AOSTEPS","4");assertFalse(MakeUpExactGraph.profile(on));
  }
  m.put(CompileOptionControl.MAKEUP_TRANSLUCENT_FASTPATH,"1");m.put(CompileOptionControl.MAKEUP_AO_ATTENUATION,"1");assertFalse(MakeUpExactGraph.profile(m));
  assertNull(com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission.withoutInactiveControl(m));
 }
}
