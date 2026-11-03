package com.pp.shaderlod.optimization.astraao;
import java.util.Map;
/** The installed pre-capture recovery uses this exact native0 queue operation. */
public final class AstraAoRecovery {
 @FunctionalInterface public interface Reload {void run()throws Exception;}
 private AstraAoRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception {
  var previous=Map.copyOf(queue);previous.forEach((k,v)->queue.remove(k,v));queue.put(AstraAoContract.OPTION,"0");
  try {reload.run();}finally{queue.remove(AstraAoContract.OPTION,"0");previous.forEach((k,v)->{if(!k.equals(AstraAoContract.OPTION))queue.putIfAbsent(k,v);});}
 }
}
