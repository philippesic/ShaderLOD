package com.pp.shaderlod.optimization.makeupprime;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class C01CostTest {
 @Test void ordinaryAndUnarmedCallsCannotRecordCosts() {
  org.junit.jupiter.api.Assumptions.assumeFalse(C01Cost.ENABLED);
  C01Cost.observe(false);
  assertFalse(C01Cost.observing());assertNull(C01Cost.begin());
  assertThrows(IllegalStateException.class,()->C01Cost.observe(true));
  assertTrue(((java.util.List<?>)C01Cost.snapshot().get("rows")).isEmpty());
 }
 @Test void nullTokenEntriesDoNotAcquireTheRecorderMonitor() throws Exception {
  var held=new CountDownLatch(1);
  var release=new CountDownLatch(1);
  Thread holder=Thread.ofPlatform().daemon().start(()->{
   synchronized(C01Cost.class){
    held.countDown();
    try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
   }
  });
  var worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().factory());
  try{
   assertTrue(held.await(2,TimeUnit.SECONDS));
   worker.submit(()->{
    C01Cost.end("disabled",null);
    C01Cost.end("disabled",null,Map.of("sentinel",true));
   }).get(2,TimeUnit.SECONDS);
  }finally{
   release.countDown();holder.join(2000);worker.shutdownNow();
  }
  assertFalse(holder.isAlive());
 }
 @Test void entryPointsStayOutsideTheEnabledRecorderLock() throws Exception {
  assertFalse(Modifier.isSynchronized(C01Cost.class.getDeclaredMethod("end",String.class,long[].class).getModifiers()));
  assertFalse(Modifier.isSynchronized(C01Cost.class.getDeclaredMethod("end",String.class,long[].class,Map.class).getModifiers()));
  assertTrue(Modifier.isSynchronized(C01Cost.class.getDeclaredMethod("record",String.class,long[].class,Map.class).getModifiers()));
 }
}
