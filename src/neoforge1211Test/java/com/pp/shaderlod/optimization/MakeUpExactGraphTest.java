package com.pp.shaderlod.optimization;
import com.pp.shaderlod.*;
import com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph;
import com.pp.shaderlod.optimization.mips.BslLeanGraph;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoContract;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MakeUpExactGraphTest {
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
