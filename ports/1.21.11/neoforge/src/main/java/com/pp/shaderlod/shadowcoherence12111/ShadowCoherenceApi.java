package com.pp.shaderlod.shadowcoherence12111;
import java.util.*;
import java.util.function.Function;
/** Exact Fabric Iris1.10.8 namespaces; unknown/mixed/changed resources remain native. */
public final class ShadowCoherenceApi {
 private static Boolean pinned;
 private ShadowCoherenceApi(){}
 public static boolean exactApi(){if(pinned!=null)return pinned;try{return pinned=verify("named",name->{try(var in=ShadowCoherenceApi.class.getClassLoader().getResourceAsStream(name)){return in==null?null:in.readAllBytes();}catch(Exception e){return null;}});}catch(RuntimeException|LinkageError unavailable){return pinned=false;}}
 public static boolean verify(String namespace,Function<String,byte[]> resources){if(namespace==null||resources==null)return false;String[][] pins=switch(namespace){
  case "named" -> new String[][]{{"net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum.class","f2b54bf61e2896bfaefeb78e4965b8d86010e50948df3c6b6e340bdd85838c2a"},{"net/irisshaders/iris/shadows/frustum/advanced/SafeZoneCullingFrustum.class","1be625998a0ece3ffb3567b561ea59d3fcdde25052dd1e49b739f52890aba019"},{"net/irisshaders/iris/shadows/frustum/BoxCuller.class","46127746d2e710bf227c69877d62ca0993e32b79b9b716b591c47e38de439f06"},{"net/caffeinemc/mods/sodium/client/render/viewport/frustum/Frustum.class","4ae26b2dc838050c1586ef000e3b82ba811d8b39be290042d36660b865dc79cb"},{"net/caffeinemc/mods/sodium/client/render/viewport/Viewport.class","3e92ce376144a17a0ec0fe3a53f9700334311d8ec09376df6796692fe216c931"}};
  default -> null;
 };if(pins==null)return false;try{for(var pin:pins){byte[] bytes=resources.apply(pin[0]);if(bytes==null||!pin[1].equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))))return false;}return true;}catch(Exception|LinkageError unavailable){return false;}}
}
