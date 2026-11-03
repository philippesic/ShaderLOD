package com.pp.shaderlod.optimization.blissao;
import java.util.Map;
/** Existing native0 queue/reload recovery, invoked only before pipeline capture. */
public final class BlissAoRecovery {
 @FunctionalInterface public interface Reload{void run()throws Exception;}
 private BlissAoRecovery(){}
 public static void nativeReload(Map<String,String> queue,Reload reload)throws Exception{var prior=Map.copyOf(queue);prior.forEach((k,v)->queue.remove(k,v));queue.put(BlissAoContract.OPTION,"0");try{reload.run();}finally{queue.remove(BlissAoContract.OPTION,"0");prior.forEach((k,v)->{if(!k.equals(BlissAoContract.OPTION))queue.putIfAbsent(k,v);});}}
}
