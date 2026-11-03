package com.pp.shaderlod.optimization.blissspatial;
import java.util.*;
/** Literal graph0 queue recovery, only outside a retained rendering pipeline. */
public final class BlissSpatialRecovery {
 @FunctionalInterface public interface Reload{void run()throws Exception;}
 private BlissSpatialRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception{var before=Map.copyOf(queue);before.forEach((k,v)->queue.remove(k,v));queue.put(BlissSpatialContract.REUSE,"0");queue.put(BlissSpatialContract.RATE,"0");try{reload.run();}finally{queue.remove(BlissSpatialContract.REUSE,"0");queue.remove(BlissSpatialContract.RATE,"0");before.forEach((k,v)->{if(!Set.of(BlissSpatialContract.REUSE,BlissSpatialContract.RATE).contains(k))queue.putIfAbsent(k,v);});}}
}
