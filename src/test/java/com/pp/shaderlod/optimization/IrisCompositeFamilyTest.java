package com.pp.shaderlod.optimization;
import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs;
import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs.*;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoContract;
import com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoContract;
import com.pp.shaderlod.optimization.unboundao.UnboundAoContract;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IrisCompositeFamilyTest {
 private Map<String,String> profile(Family family,String half){return switch(family){case MAKEUP->MakeUpAoContract.generated(half);case REIMAGINED->ReimaginedAoContract.generated(half);case UNBOUND->UnboundAoContract.generated(half);case SILDUR->com.pp.shaderlod.optimization.sildurao.SildurAoContract.generated(half);};}
 @Test void eachFamilyAdmitsOnlyItsOwnCompleteActiveProfile(){
  for(var family:Family.values()){
   var values=profile(family,"1");assertEquals(family,IrisCompositeCompiledInputs.family(values));assertNull(IrisCompositeCompiledInputs.family(profile(family,"0")));
   for(var other:Family.values())assertEquals(other==family,other.options(values));
   var changed=new TreeMap<>(values);changed.put("AA_TYPE","foreign AA");assertNull(IrisCompositeCompiledInputs.family(changed));
   changed=new TreeMap<>(values);changed.put("FOREIGN_COMPILE_INPUT","1");assertNull(IrisCompositeCompiledInputs.family(changed));
  }
 }
 @Test void sildurMetadataRefusesShiftedSpatialRolesAndNeverCertifiesReflectionOrField(){
  for(String mode:List.of("none","reuse","rate")){
   var values=new TreeMap<>(profile(Family.SILDUR,"1"));
   if(!mode.equals("none"))values.put(mode.equals("reuse")?"SHADERLOD_SILDUR_SPATIAL_REUSE":"SHADERLOD_SILDUR_LIGHTING_RATE","1");
   boolean metadata=mode.equals("none");
   assertEquals(metadata?Family.SILDUR:null,IrisCompositeCompiledInputs.family(values));
   String producer=mode.equals("none")?"deferred":"deferred1",consumer=mode.equals("none")?"deferred1":"deferred2";
   assertEquals(producer,Family.SILDUR.producer(values));assertEquals(consumer,Family.SILDUR.consumer(values));
   for(String name:List.of("deferred","deferred1","deferred2","deferred3","composite"))
    assertEquals(metadata&&(name.equals(producer)||name.equals(consumer)),Family.SILDUR.admits(name,values),mode+" "+name);
  }
  assertFalse(Family.SILDUR.admits("deferred",profile(Family.SILDUR,"0")));
  for(var family:List.of(Family.MAKEUP,Family.REIMAGINED,Family.UNBOUND))assertFalse(family.admits("deferred2",profile(family,"1")));
 }
 @Test void failedBothCapturesRefuseForEverySupportedFamily(){
  for(var family:Family.values()){
   var before=IrisCompositeCompiledInputs.status(family).get("legacy_frames");
   assertEquals(State.STALE,IrisCompositeCompiledInputs.lookup(true,family,null,null,null,null,null).state());
   assertEquals(before,IrisCompositeCompiledInputs.status(family).get("legacy_frames"));
   assertEquals(State.MISSING,IrisCompositeCompiledInputs.lookup(false,family,null,null,null,null,null).state());
  }
 }
}
