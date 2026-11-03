package com.pp.shaderlod.optimization.makeupao;
import java.util.Map;
/** The installed pre-capture recovery uses this exact native0 queue operation. */
public final class MakeUpAoRecovery {
 @FunctionalInterface public interface Reload {void run()throws Exception;}
 private MakeUpAoRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception {
  var previous=Map.copyOf(queue);previous.forEach((k,v)->queue.remove(k,v));queue.put(MakeUpAoContract.OPTION,"0");
  try {reload.run();}finally{queue.remove(MakeUpAoContract.OPTION,"0");previous.forEach((k,v)->{if(!k.equals(MakeUpAoContract.OPTION))queue.putIfAbsent(k,v);});}
 }
}
