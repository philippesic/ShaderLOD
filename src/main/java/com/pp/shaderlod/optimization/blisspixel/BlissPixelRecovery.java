package com.pp.shaderlod.optimization.blisspixel;
import java.util.Map;
/** The installed pre-capture recovery uses this exact native0 queue operation. */
public final class BlissPixelRecovery {
 @FunctionalInterface public interface Reload {void run()throws Exception;}
 private BlissPixelRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception {
  var previous=Map.copyOf(queue);previous.forEach((k,v)->queue.remove(k,v));queue.put(BlissPixelContract.OPTION,"0");
  try {reload.run();}finally{queue.remove(BlissPixelContract.OPTION,"0");previous.forEach((k,v)->{if(!k.equals(BlissPixelContract.OPTION))queue.putIfAbsent(k,v);});}
 }
}
