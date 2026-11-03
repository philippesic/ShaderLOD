package com.pp.shaderlod.optimization.photon;
import java.util.*;import net.irisshaders.iris.Iris;
/** Deferred native-graph recovery, outside every retained active world pipeline. */
public final class PhotonSpatialRecovery {
 private static boolean requested,recovering;private PhotonSpatialRecovery(){}
 public static void request(){requested=true;}
 /** Only an external reload after pending recovery has completed may rearm setup. */
 public static boolean canRearm(){return !requested&&!recovering;}
 public static boolean requested(){return requested;}public static void cancel(){requested=false;}
 @FunctionalInterface public interface Reload{void run()throws Exception;}
 public static void nativeQueue(Map<String,String> queue,Reload reload)throws Exception{var before=Map.copyOf(queue);before.forEach((k,v)->queue.remove(k,v));queue.put("SHADERLOD_SHARED_SPATIAL","false");queue.put("SHADERLOD_LIGHTING_RATE_PROBE","false");try{reload.run();}finally{queue.remove("SHADERLOD_SHARED_SPATIAL","false");queue.remove("SHADERLOD_LIGHTING_RATE_PROBE","false");before.forEach((k,v)->{if(!Set.of("SHADERLOD_SHARED_SPATIAL","SHADERLOD_LIGHTING_RATE_PROBE").contains(k))queue.putIfAbsent(k,v);});}}
 private static boolean declared(net.irisshaders.iris.shaderpack.option.ShaderPackOptions o){var set=o.getOptionSet();for(String key:List.of("SHADERLOD_SHARED_SPATIAL","SHADERLOD_LIGHTING_RATE_PROBE")){var b=set.getBooleanOptions().get(key);if(b==null||b.getOption().getDefaultValue()!=key.equals("SHADERLOD_SHARED_SPATIAL"))return false;}return true;}
 public static void beforeCapture(boolean activeFrame,boolean revoked)throws Exception{if(!requested||recovering)return;if(activeFrame||!revoked)throw new IllegalStateException("Photon recovery lacks safe pre-capture boundary");recovering=true;try{nativeQueue(Iris.getShaderPackOptionQueue(),Iris::reload);var options=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();if(!declared(options))throw new IllegalStateException("Photon recovery graph declarations/source unavailable");var v=options.getOptionValues();if(v.getBooleanValueOrDefault("SHADERLOD_SHARED_SPATIAL")||v.getBooleanValueOrDefault("SHADERLOD_LIGHTING_RATE_PROBE"))throw new IllegalStateException("Photon native graph did not load");requested=false;}finally{recovering=false;}}
}
