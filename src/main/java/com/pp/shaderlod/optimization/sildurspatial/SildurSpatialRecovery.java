package com.pp.shaderlod.optimization.sildurspatial;
import java.util.*;
/** Literal graph0 queue recovery, only outside a retained rendering pipeline. */
public final class SildurSpatialRecovery {
 @FunctionalInterface public interface Reload{void run()throws Exception;}
 private SildurSpatialRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception{var before=Map.copyOf(queue);before.forEach((k,v)->queue.remove(k,v));queue.put(SildurSpatialContract.REUSE,"0");queue.put(SildurSpatialContract.RATE,"0");try{reload.run();}finally{queue.remove(SildurSpatialContract.REUSE,"0");queue.remove(SildurSpatialContract.RATE,"0");before.forEach((k,v)->{if(!Set.of(SildurSpatialContract.REUSE,SildurSpatialContract.RATE).contains(k))queue.putIfAbsent(k,v);});}}
}
