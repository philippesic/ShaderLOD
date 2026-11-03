package com.pp.shaderlod.optimization.reimaginedao;
import java.util.Map;
/** The installed pre-capture recovery uses this exact native0 queue operation. */
public final class ReimaginedAoRecovery {
 @FunctionalInterface public interface Reload {void run()throws Exception;}
 private ReimaginedAoRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception {
  var previous=Map.copyOf(queue);previous.forEach((k,v)->queue.remove(k,v));queue.put(ReimaginedAoContract.OPTION,"0");
  try {reload.run();}finally{queue.remove(ReimaginedAoContract.OPTION,"0");previous.forEach((k,v)->{if(!k.equals(ReimaginedAoContract.OPTION))queue.putIfAbsent(k,v);});}
 }
}
