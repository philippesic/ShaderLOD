package com.pp.shaderlod.optimization.sildurao;
import java.util.Map;
/** The installed pre-capture recovery uses this exact native0 queue operation. */
public final class SildurAoRecovery {
 @FunctionalInterface public interface Reload {void run()throws Exception;}
 private SildurAoRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception {
  var previous=Map.copyOf(queue);previous.forEach((k,v)->queue.remove(k,v));queue.put(SildurAoContract.OPTION,"0");
  try {reload.run();}finally{queue.remove(SildurAoContract.OPTION,"0");previous.forEach((k,v)->{if(!k.equals(SildurAoContract.OPTION))queue.putIfAbsent(k,v);});}
 }
}
