package com.pp.shaderlod.shadowcoherence12111;
import java.util.*;
import java.util.function.Function;
/** Exact Fabric Iris1.10.8 namespaces; unknown/mixed/changed resources remain native. */
public final class ShadowCoherenceApi {
 private static Boolean pinned;
 private ShadowCoherenceApi(){}
 public static boolean exactApi(){if(pinned!=null)return pinned;try{return pinned=verify(net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace(),name->{try(var in=ShadowCoherenceApi.class.getClassLoader().getResourceAsStream(name)){return in==null?null:in.readAllBytes();}catch(Exception e){return null;}});}catch(RuntimeException|LinkageError unavailable){return pinned=false;}}
 public static boolean verify(String namespace,Function<String,byte[]> resources){if(namespace==null||resources==null)return false;String[][] pins=switch(namespace){
  case "named" -> new String[][]{{"net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum.class","7dd0856d361064731516721d97c06f48c23002808ca03e342567be085403c02a"},{"net/irisshaders/iris/shadows/frustum/advanced/SafeZoneCullingFrustum.class","c97124166f624c677b72ab53c154f017a4dcd469b7cf10c36a2f74618e73be37"},{"net/irisshaders/iris/shadows/frustum/BoxCuller.class","daeffe9f94405118c6238d174e11b281548ba5bad141db0fa860e41b40dee924"},{"net/caffeinemc/mods/sodium/client/render/viewport/frustum/Frustum.class","73c5338bee0aa3a9459fc2b85611fe8e0f9843686a04496d99423571e06bf62f"},{"net/caffeinemc/mods/sodium/client/render/viewport/Viewport.class","fd63b9e2cf2f8345b0f08c536a552fe0b2dbf34743b8ee40665faaac4f6c7511"}};
  case "intermediary" -> new String[][]{{"net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum.class","593b1439e7c890ce70cfde72fcb12ce6fb0cfb2dc0dc172e70bb47fd127c8d98"},{"net/irisshaders/iris/shadows/frustum/advanced/SafeZoneCullingFrustum.class","f63fffba8683a2df449aedc9ba6ee7192f7eac1ed3227a784c7861ae03000af6"},{"net/irisshaders/iris/shadows/frustum/BoxCuller.class","f42a1ab67609451c49677bd084f8ab7bd8c6031cf467cb274c4cf62cad7b526c"},{"net/caffeinemc/mods/sodium/client/render/viewport/frustum/Frustum.class","73c5338bee0aa3a9459fc2b85611fe8e0f9843686a04496d99423571e06bf62f"},{"net/caffeinemc/mods/sodium/client/render/viewport/Viewport.class","c0fc64477cb93f4697c0db98b3efc0e2e0618d389ad8a35d6971a2b74a54f4e4"}};
  default -> null;
 };if(pins==null)return false;try{for(var pin:pins){byte[] bytes=resources.apply(pin[0]);if(bytes==null||!pin[1].equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))))return false;}return true;}catch(Exception|LinkageError unavailable){return false;}}
}
