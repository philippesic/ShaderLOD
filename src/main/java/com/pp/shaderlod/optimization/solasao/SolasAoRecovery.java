package com.pp.shaderlod.optimization.solasao;
import java.util.Map;
/** The installed pre-capture recovery uses this exact native0 queue operation. */
public final class SolasAoRecovery {
 @FunctionalInterface public interface Reload {void run()throws Exception;}
 private SolasAoRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception {
  var previous=Map.copyOf(queue);previous.forEach((k,v)->queue.remove(k,v));queue.put(SolasAoContract.OPTION,"0");
  try {reload.run();}finally{queue.remove(SolasAoContract.OPTION,"0");previous.forEach((k,v)->{if(!k.equals(SolasAoContract.OPTION))queue.putIfAbsent(k,v);});}
 }
}
