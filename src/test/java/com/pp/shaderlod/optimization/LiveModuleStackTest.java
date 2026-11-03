package com.pp.shaderlod.optimization;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LiveModuleStackTest {
 private static boolean matches(List<Map.Entry<String,String>> modules){return MakeUpQuadRuntime.stackMatchesModules(modules,Map.Entry::getKey,Map.Entry::getValue);}
 @Test void liveEnumerationRetainsVersionAndUnknownModuleRefusal(){
  var modules=new ArrayList<>(MakeUpQuadRuntime.REQUIRED_STACK.entrySet());modules.add(Map.entry("shaderlod","any product version"));
  assertTrue(matches(modules));modules.add(Map.entry("shaderlod_dev","0.1.0"));assertTrue(matches(modules));
  modules.add(Map.entry("foreign","1"));assertFalse(matches(modules));modules.removeLast();
  var first=modules.getFirst();modules.set(0,Map.entry(first.getKey(),"changed"));assertFalse(matches(modules));
  modules.set(0,first);assertTrue(matches(modules));modules.add(first);assertFalse(matches(modules));
 }
 @Test void missingProductAndWrongObserverRemainRejected(){
  var modules=new ArrayList<>(MakeUpQuadRuntime.REQUIRED_STACK.entrySet());assertFalse(matches(modules));
  modules.add(Map.entry("shaderlod","1"));modules.add(Map.entry("shaderlod_dev","wrong"));assertFalse(matches(modules));
 }
 @Test void experimentalDhPinsVersionAndKeepsUnknownDuplicateAndRequiredModuleGuards(){
  var stack=new LinkedHashMap<>(MakeUpQuadRuntime.REQUIRED_STACK);stack.put("shaderlod","test");stack.put("distanthorizons","3.3.3");
  assertFalse(MakeUpQuadRuntime.stackMatches(stack,false));assertTrue(MakeUpQuadRuntime.stackMatches(stack,true));
  var modules=new ArrayList<>(stack.entrySet());
  assertFalse(MakeUpQuadRuntime.stackMatchesModules(modules,Map.Entry::getKey,Map.Entry::getValue,false));
  assertTrue(MakeUpQuadRuntime.stackMatchesModules(modules,Map.Entry::getKey,Map.Entry::getValue,true));
  modules.add(Map.entry("distanthorizons","3.3.3"));assertFalse(MakeUpQuadRuntime.stackMatchesModules(modules,Map.Entry::getKey,Map.Entry::getValue,true));
  stack.put("distanthorizons","3.3.4");assertFalse(MakeUpQuadRuntime.stackMatches(stack,true));
  assertFalse(MakeUpQuadRuntime.stackMatchesModules(new ArrayList<>(stack.entrySet()),Map.Entry::getKey,Map.Entry::getValue,true));
  stack.put("distanthorizons","3.3.3");stack.put("foreign","1");assertFalse(MakeUpQuadRuntime.stackMatches(stack,true));
  stack.remove("foreign");stack.put("sodium","wrong");assertFalse(MakeUpQuadRuntime.stackMatches(stack,true));
 }
 @Test void dhExperimentCannotAdmitOtherFamilyOrGpuRequests(){
  assertTrue(MakeUpQuadRuntime.dhContextAllows(true,true,true));
  assertFalse(MakeUpQuadRuntime.dhContextAllows(true,false,true));
  assertFalse(MakeUpQuadRuntime.dhContextAllows(true,true,false));
  assertTrue(MakeUpQuadRuntime.dhContextAllows(false,false,false));
 }
 @Test void bothPermissionValuesRetainEveryRequiredVersionAndPresenceCheck(){
  for(boolean allowDh:new boolean[]{false,true}){
   var base=new LinkedHashMap<>(MakeUpQuadRuntime.REQUIRED_STACK);base.put("shaderlod","test");
   assertTrue(MakeUpQuadRuntime.stackMatches(base,allowDh));
   var withDh=new LinkedHashMap<>(base);withDh.put("distanthorizons","3.3.3");
   assertEquals(allowDh,MakeUpQuadRuntime.stackMatches(withDh,allowDh));
   for(String id:MakeUpQuadRuntime.REQUIRED_STACK.keySet()){
    var missing=new LinkedHashMap<>(base);missing.remove(id);
    assertFalse(MakeUpQuadRuntime.stackMatches(missing,allowDh),id);
    var changed=new LinkedHashMap<>(base);changed.put(id,"wrong");
    assertFalse(MakeUpQuadRuntime.stackMatches(changed,allowDh),id);
    var duplicates=new ArrayList<>(base.entrySet());duplicates.add(Map.entry(id,base.get(id)));
    assertFalse(MakeUpQuadRuntime.stackMatchesModules(duplicates,Map.Entry::getKey,Map.Entry::getValue,allowDh),id);
   }
  }
 }
}
