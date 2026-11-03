package com.pp.shaderlod.optimization.sourcecache;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceProofEpochTest {
 @Test void steadyLookupDoesNotRunValidationAndPublicationBelongsToTheActualOwner(){
  var epoch=new SourceProofEpoch();var owner=new Object();var reads=new AtomicInteger();
  assertFalse(epoch.current(owner,"source"));
  try(var build=epoch.begin()){
   assertTrue(build.validate("source",()->{reads.incrementAndGet();return true;}));
   assertTrue(build.validate("source",()->{throw new AssertionError("duplicate source IO");}));
   assertFalse(epoch.current(owner,"source"),"A partial build cannot authorize rendering");
   assertTrue(build.publish(owner,()->true));
  }
  for(int frame=0;frame<10000;frame++)assertTrue(epoch.current(owner,"source"));
  assertEquals(1,reads.get());assertFalse(epoch.current(new Object(),"source"));
  epoch.invalidate();assertFalse(epoch.current(owner,"source"));
 }
 @Test void failedCancelledNestedAndLateBuildsCannotResurrectAReceipt(){
  var epoch=new SourceProofEpoch();var owner=new Object();
  try(var failed=epoch.begin()){assertFalse(failed.validate("source",()->false));assertTrue(failed.publish(owner,()->true));}
  assertFalse(epoch.current(owner,"source"));
  try(var cancelled=epoch.begin()){assertTrue(cancelled.validate("source",()->true));}
  assertFalse(epoch.current(owner,"source"));
  var old=epoch.begin();assertTrue(old.validate("source",()->true));
  try(var replacement=epoch.begin()){
   assertFalse(old.publish(owner,()->true));
   assertTrue(replacement.validate("source",()->true));
   assertTrue(replacement.publish(owner,()->true));
  }
  old.close();assertTrue(epoch.current(owner,"source"));
  try(var stale=epoch.begin()){
   assertFalse(stale.validate("source",()->{epoch.invalidate();return true;}));
   assertFalse(stale.publish(owner,()->true));
  }
  assertFalse(epoch.current(owner,"source"));
 }
 @Test void transitionDuringFinalCurrentCheckCannotPublish(){
  var epoch=new SourceProofEpoch();var owner=new Object();
  try(var build=epoch.begin()){
   assertTrue(build.validate("source",()->true));
   assertFalse(build.publish(owner,()->{epoch.invalidate();return true;}));
  }
  assertFalse(epoch.current(owner,"source"));
 }
}
