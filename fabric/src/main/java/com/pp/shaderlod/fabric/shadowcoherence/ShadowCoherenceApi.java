package com.pp.shaderlod.fabric.shadowcoherence;
import java.util.*;
import java.util.function.Function;
/** Exact Fabric Iris1.8.8 namespaces; unknown/mixed/changed resources remain native. */
public final class ShadowCoherenceApi {
 private static Boolean pinned;
 private ShadowCoherenceApi(){}
 public static boolean exactApi(){if(pinned!=null)return pinned;try{return pinned=verify(net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace(),name->{try(var in=ShadowCoherenceApi.class.getClassLoader().getResourceAsStream(name)){return in==null?null:in.readAllBytes();}catch(Exception e){return null;}});}catch(RuntimeException|LinkageError unavailable){return pinned=false;}}
 public static boolean verify(String namespace,Function<String,byte[]> resources){if(namespace==null||resources==null)return false;String[][] pins=switch(namespace){
  case "named" -> new String[][]{{"net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum.class","73784423b7b341e8a1cda480e7a2007e6e5c61cfde26259ce04b50f0f49e4490"},{"net/irisshaders/iris/shadows/frustum/advanced/ReversedAdvancedShadowCullingFrustum.class","78b2c2c059140245d5fb9716103df5e5e2cfd5bebda068536865d3dc6d449100"},{"net/irisshaders/iris/shadows/frustum/BoxCuller.class","4a902aa7cbc13f3a1aa27d6188ea0ea68ea321d7e62378e9d6a24ee0781727c9"}};
  case "intermediary" -> new String[][]{{"net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum.class","ddfe09a0475f595c8aab570d0446890521dfabe3a4cd80b3d582bff70102f217"},{"net/irisshaders/iris/shadows/frustum/advanced/ReversedAdvancedShadowCullingFrustum.class","828c6182780f515077b0024019daa529a466000304c101d8b65f14c13f23086b"},{"net/irisshaders/iris/shadows/frustum/BoxCuller.class","2b677e4a15c49d92902e73a91d446da8067ff9227b47fdc78d3ffa223beddfe1"}};
  default -> null;
 };if(pins==null)return false;try{for(var pin:pins){byte[] bytes=resources.apply(pin[0]);if(bytes==null||!pin[1].equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))))return false;}return true;}catch(Exception|LinkageError unavailable){return false;}}
}
